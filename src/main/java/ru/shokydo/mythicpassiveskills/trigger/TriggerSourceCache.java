package ru.shokydo.mythicpassiveskills.trigger;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import ru.shokydo.mythicpassiveskills.MythicPassiveSkillsPlugin;

import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Кэш «кто уже обрабатывает клик».
 * <p>
 * Один и тот же клик хотят обработать несколько плагинов: MythicCrucible
 * ({@code onLeftClick}, {@code onPressF}, {@code onPress_F} предмета),
 * MythicMobs
 * ({@code onUse} предмета на правый клик) и пассивки. Чтобы предмет
 * MythicCrucible не «терял» способность:
 * <ul>
 * <li>если у предмета в руке объявлен {@code onPressF}/{@code onPress_F},
 * свап рук ему не мешаем;</li>
 * <li>если у предмета есть {@code onUse}, правый клик мы отменяем ровно
 * так, чтобы MythicMobs запустил {@code onUse} ровно один раз;</li>
 * <li>при срабатывании пассивки помним, что клик уже обслужен нашей
 * копией — тогда имя предмета в {@code SkillMetadata} меняется только
 * пока он в руке и только после успешного срабатывания.</li>
 * </ul>
 * Список предметов перечитывается после {@code /mm reload} и по таймеру.
 */
public final class TriggerSourceCache {

    private static final Set<String> EXTERNAL_ALWAYS = Set.of(
            "ATTACK", "DAMAGED", "KILL", "SHOOT", "CONSUME", "PICKUP", "BLOCK_BREAK", "THROW"
    );

    private final MythicPassiveSkillsPlugin plugin;

    /** ID Mythic-предмета (нижний регистр) → триггеры, объявленные у предмета. */
    private volatile Map<String, Map<String, Boolean>> itemSkills = Map.of();

    /** UUID игрока → {@code (предмет | триггер)}, на который мы уже отдали клик. */
    private final Map<UUID, String> served = new HashMap<>();

    /**
     * Последнее флаговое значение свапа: если после нашего MONITOR кто-то
     * (MythicCrucible) снова отменил событие, это означает, что способность
     * предмета его всё-таки обработала — и руки мы возвращаем уже после всех.
     */
    private final Map<UUID, SwapMark> swapMarks = new HashMap<>();

    public TriggerSourceCache(MythicPassiveSkillsPlugin plugin) {
        this.plugin = plugin;
    }

    private record SwapMark(long tick, boolean ourCancellation, String mainId, String offId,
            ItemStack main, ItemStack off) {
    }

    /**
     * @return {@code true}, если MythicCrucible/MythicMobs сам вызывает скиллы
     *         предмета на этом триггере (атака, урон, убийство, выстрел {@code onBow}…)
     */
    public static boolean isExternallyOwned(String triggerName) {
        return triggerName != null && EXTERNAL_ALWAYS.contains(normalize(triggerName));
    }

    // ------------------------------------------------------------ Предметы

    // ------------------------------------------------------------ Предметы

    /**
     * Перечитывает список Mythic-предметов и их триггеров.
     */
    public void refresh() {
        Plugin mythic = Bukkit.getPluginManager().getPlugin("MythicMobs");
        if (mythic == null || !mythic.isEnabled()) {
            itemSkills = Map.of();
            return;
        }

        Map<String, Map<String, Boolean>> collected = new HashMap<>();
        try {
            var itemManager = io.lumine.mythic.bukkit.MythicBukkit.inst().getItemManager();
            if (itemManager == null) {
                return;
            }
            collectItems(itemManager.getItems(), collected);
        } catch (Throwable throwable) {
            plugin.debug("Не удалось прочитать Mythic-предметы: " + throwable.getMessage());
            return;
        }

        // Предметы MythicCrucible регистрируются в том же реестре MythicMobs,
        // поэтому того же getItems() достаточно: отдельная зависимость на классы
        // Crucible не нужна (и не должна ломать запуск без него).
        itemSkills = Map.copyOf(collected);
        plugin.debug("Загружено триггеров предметов: " + itemSkills.size());
    }

    private void collectItems(Collection<?> items, Map<String, Map<String, Boolean>> target) {
        if (items == null) {
            return;
        }
        for (Object candidate : items) {
            if (!(candidate instanceof io.lumine.mythic.core.items.MythicItem item)) {
                continue;
            }

            Map<String, Boolean> triggers = new HashMap<>();
            Object skills = invoke(item, "getSkills");
            if (skills instanceof Map<?, ?> map) {
                for (Object key : map.keySet()) {
                    if (key instanceof io.lumine.mythic.api.skills.SkillTrigger<?> trigger) {
                        triggers.put(normalize(trigger.name()), Boolean.TRUE);
                    }
                }
            }
            if (!triggers.isEmpty()) {
                target.put(item.getInternalName().toLowerCase(Locale.ROOT), triggers);
            }
        }
    }

    /**
     * @return {@code true}, если предмет с таким ID объявляет скилл на этом
     *         триггере
     */
    public boolean itemHasTrigger(String itemId, String trigger) {
        if (itemId == null || trigger == null) {
            return false;
        }
        Map<String, Boolean> triggers = itemSkills.get(itemId.toLowerCase(Locale.ROOT));
        return triggers != null && triggers.containsKey(normalize(trigger));
    }



    public Optional<String> mythicItemId(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return Optional.empty();
        }
        try {
            var itemManager = io.lumine.mythic.bukkit.MythicBukkit.inst().getItemManager();
            if (itemManager == null) {
                return Optional.empty();
            }
            String type = itemManager.getMythicTypeFromItem(item);
            return type != null && !type.isBlank() ? Optional.of(type) : Optional.empty();
        } catch (Throwable ignored) {
            return Optional.empty();
        }
    }

    // ------------------------------------------------------------ Правый клик

    /**
     * @return {@code true}, если правый клик нужно отменить, чтобы MythicMobs
     *         запустил {@code onUse} предмета ровно один раз
     */
    public boolean shouldCancelRightClick(Player player, ItemStack item) {
        if (item == null || item.getType().isAir() || isBukkitHandled(item)) {
            return false;
        }

        Optional<String> itemId = mythicItemId(item);
        if (itemId.isEmpty() || !itemHasTrigger(itemId.get(), "USE")) {
            return false;
        }

        // Отменённый клик MythicMobs запускает как onUse предмета — то есть
        // «отдать клик onUse» может только это событие. Помечаем, чтобы наша
        // пассивка не считала предмет «чужим» именно для этого onUse.
        served.put(player.getUniqueId(), key(itemId.get(), "USE"));
        return true;
    }

    /**
     * Расходуемые и метательные предметы Bukkit сам превращает в onUse/запуск
     * снаряда — их клик отменять нельзя, иначе предмет перестанет работать.
     */
    private boolean isBukkitHandled(ItemStack item) {
        Material type = item.getType();
        if (type.isEdible()) {
            return true;
        }

        String name = type.name();
        return name.startsWith("SPLASH_POTION")
                || name.startsWith("LINGERING_POTION")
                || name.startsWith("LINGERING_POTION")
                || name.equals("ENDER_PEARL")
                || name.equals("ENDER_EYE")
                || name.equals("SNOWBALL")
                || name.equals("EGG")
                || name.equals("EXPERIENCE_BOTTLE")
                || name.equals("FIREWORK_ROCKET")
                || name.equals("FIREWORK");
    }

    // ------------------------------------------------------------ Свап рук

    /**
     * Пассивка отменила эффект свапа. Если после нас событие ещё раз отменит
     * MythicCrucible (то есть его {@code onPressF} сработал), руки нужно
     * вернуть на место — иначе предметы из плагина разъедутся.
     */
    public void markSwapCancelled(Player player, ItemStack main, ItemStack off) {
        swapMarks.put(player.getUniqueId(), new SwapMark(
                Bukkit.getCurrentTick(),
                true,
                mythicItemId(main).orElse(null),
                mythicItemId(off).orElse(null),
                main.clone(),
                off.clone()));
    }

    /**
     * Проверяет, сработал ли после нашего обработчика чужой (MythicCrucible).
     * Вызывать на {@code MONITOR} последнего порядка — после всех плагинов.
     *
     * @return предметы, которые нужно вернуть в руки (или {@code null}),
     *         если свап трогать не нужно
     */
    public ItemStack[] reapSwapIfConverted(Player player) {
        UUID uuid = player.getUniqueId();
        SwapMark mark = swapMarks.remove(uuid);
        if (mark == null || mark.tick() != Bukkit.getCurrentTick() || !mark.ourCancellation()) {
            return null;
        }

        ItemStack main = player.getInventory().getItemInMainHand();
        ItemStack off = player.getInventory().getItemInOffHand();
        if (!sameItem(main, mark.off()) || !sameItem(off, mark.main())) {
            // Даже если руки уже успели поменяться вручную — просто не трогаем.
            return null;
        }

        player.getInventory().setItemInMainHand(mark.main());
        player.getInventory().setItemInOffHand(mark.off());
        plugin.debug("Свап рук возвращён: способность предмета MythicCrucible сработала у "
                + player.getName());
        return new ItemStack[] { mark.main(), mark.off() };
    }

    private static boolean sameItem(ItemStack a, ItemStack b) {
        return a != null && b != null && a.getType() == b.getType() && a.isSimilar(b);
    }

    // ------------------------------------------------------------ Отдано/не отдано

    /**
     * Запоминает, что пассивка успешно обработала клик по предмету.
     */
    public void markServed(Player player, String itemId, PassiveTrigger trigger) {
        served.put(player.getUniqueId(), key(itemId, trigger.name()));
    }

    /**
     * @return {@code true}, если такой клик по этому предмету уже обслужила наша
     *         пассивка
     */
    public boolean isServed(Player player, String itemId, String triggerName) {
        if (itemId == null || triggerName == null) {
            return false;
        }
        return key(itemId, triggerName).equals(served.get(player.getUniqueId()));
    }

    public void clear(Player player) {
        served.remove(player.getUniqueId());
        swapMarks.remove(player.getUniqueId());
    }

    public void clearAll() {
        served.clear();
        swapMarks.clear();
    }

    // ------------------------------------------------------------ Утилиты

    private static String key(String itemId, String trigger) {
        return itemId.toLowerCase(Locale.ROOT) + "|" + normalize(trigger);
    }

    private static Object invoke(Object target, String method) {
        try {
            return target.getClass().getMethod(method).invoke(target);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String normalize(String trigger) {
        return trigger.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
    }

    /**
     * Склеивает обращения к MythicMobs в одно обращение за тик.
     */
    public static final class PerTick {

        private final TriggerSourceCache cache;
        private long tick = Long.MIN_VALUE;
        private final Map<UUID, ItemStack> held = new HashMap<>();
        private final Map<String, String> ids = new HashMap<>();

        public PerTick(TriggerSourceCache cache) {
            this.cache = cache;
        }

        public ItemStack heldItem(Player player) {
            sync();
            return held.computeIfAbsent(player.getUniqueId(),
                    uuid -> player.getInventory().getItemInMainHand().clone());
        }

        public String itemId(ItemStack item) {
            sync();
            if (item == null || item.getType().isAir()) {
                return null;
            }
            String key = item.getType() + "|" + item.hashCode();
            if (ids.containsKey(key)) {
                return ids.get(key);
            }
            String value = cache.mythicItemId(item).orElse(null);
            ids.put(key, value);
            return value;
        }

        private void sync() {
            long current = Bukkit.getCurrentTick();
            if (current != tick) {
                tick = current;
                held.clear();
                ids.clear();
            }
        }
    }
}
