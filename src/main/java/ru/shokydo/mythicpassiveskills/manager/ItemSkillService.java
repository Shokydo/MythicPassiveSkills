package ru.shokydo.mythicpassiveskills.manager;

import io.lumine.mythic.api.adapters.AbstractEntity;
import io.lumine.mythic.api.adapters.AbstractPlayer;
import io.lumine.mythic.api.skills.SkillCaster;
import io.lumine.mythic.api.skills.SkillManager;
import io.lumine.mythic.api.skills.SkillTrigger;
import io.lumine.mythic.bukkit.BukkitAdapter;
import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.core.skills.SkillMechanic;
import io.lumine.mythic.core.skills.SkillMetadataImpl;
import io.lumine.mythic.core.skills.TriggeredSkill;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import ru.shokydo.mythicpassiveskills.MythicPassiveSkillsPlugin;
import ru.shokydo.mythicpassiveskills.trigger.PassiveTrigger;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.logging.Level;

/**
 * Выполняет скиллы <b>предмета</b> MythicCrucible на нажатие клавиши хотбара
 * ({@code ~onPressSlot0}…{@code ~onPressSlot8}).
 * <p>
 * MythicCrucible сам вызывает скиллы предметов только на {@code onHeld} /
 * {@code onUnheld} и не знает про клавиши слотов, поэтому нужный триггер
 * запускаем мы. Механики берутся прямо из определения предмета
 * ({@code CrucibleItem.getSkills(trigger)}), поэтому работает всё, что умеет
 * обычный скилл предмета: {@code skill{s=...}}, {@code cooldown},
 * {@code message}, {@code cancelevent} и т.д.
 * <p>
 * Классы MythicCrucible читаются через рефлексию: без Crucible плагин должен
 * запускаться как обычно.
 */
public final class ItemSkillService {

    private static final String CRUCIBLE_CLASS = "io.lumine.mythiccrucible.MythicCrucible";
    private static final String ITEM_MANAGER_CLASS = "io.lumine.mythiccrucible.items.ItemManager";
    private static final String SKILLED_ITEM_CLASS = "io.lumine.mythiccrucible.skills.SkilledItem";

    private final MythicPassiveSkillsPlugin plugin;

    private boolean resolved;
    private boolean available;
    private boolean warned;
    private Method crucibleInstance;
    private Method itemManager;
    private Method getItem;
    private Method getItems;
    private Method getSkills;

    public ItemSkillService(MythicPassiveSkillsPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * @return {@code true}, если MythicCrucible найден и скиллы предметов доступны
     */
    public boolean isAvailable() {
        resolve();
        return available;
    }

    /**
     * Запускает скиллы предметов на нажатие слота хотбара.
     * <p>
     * Скиллы выполняются и у предмета из нажатого слота, и у предмета в руке:
     * так работает и «предмет в слоте 1», и «способность оружия в руке по
     * клавише 1». Один и тот же предмет дважды не запускается.
     *
     * @param player      игрок
     * @param pressedSlot номер нажатого слота ({@code 0..8})
     * @param stacks      предметы-кандидаты (нажатый слот, предмет в руке)
     * @return {@code true}, если скилл предмета попросил отменить событие
     *         ({@code cancelevent}) — тогда игрок остаётся в прежнем слоте
     */
    public boolean fireSlotSkills(Player player, int pressedSlot, ItemStack... stacks) {
        if (player == null || stacks == null || stacks.length == 0) {
            return false;
        }
        if (!Bukkit.isPrimaryThread() || MythicBukkit.inst() == null) {
            return false;
        }
        if (!isAvailable()) {
            return false;
        }

        PassiveTrigger trigger = PassiveTrigger.pressSlot(pressedSlot);
        if (trigger == null) {
            return false;
        }

        Set<Object> seen = new LinkedHashSet<>();
        boolean cancel = false;
        for (ItemStack stack : stacks) {
            if (stack == null || stack.getType().isAir()) {
                continue;
            }

            Object crucibleItem = resolveItem(stack);
            if (crucibleItem == null || !seen.add(crucibleItem)) {
                continue;
            }

            Queue<SkillMechanic> mechanics = findMechanics(crucibleItem, trigger);
            if (mechanics == null || mechanics.isEmpty()) {
                continue;
            }

            cancel |= execute(player, stack, trigger, pressedSlot, mechanics);
        }

        return cancel;
    }

    /**
     * Диагностика для {@code settings.debug}: перечисляет предметы, у которых
     * есть скиллы на клавиши хотбара. Помогает понять, разобрался ли
     * {@code ~onPressSlot0} в файле предмета: если предмета нет в списке,
     * нужен {@code /mm reload}.
     */
    public void logSlotTriggers() {
        if (!plugin.isDebugEnabled() || !isAvailable()) {
            return;
        }

        try {
            Object crucible = crucibleInstance.invoke(null);
            Object manager = crucible != null ? itemManager.invoke(crucible) : null;
            Object value = manager != null ? getItems.invoke(manager) : null;
            if (!(value instanceof Iterable<?> items)) {
                return;
            }

            int found = 0;
            int total = 0;
            for (Object item : items) {
                total++;
                Object skills = getSkills.invoke(item);
                if (!(skills instanceof Map<?, ?> map)) {
                    continue;
                }
                List<String> names = new ArrayList<>();
                List<String> slots = new ArrayList<>();
                for (Object key : map.keySet()) {
                    if (!(key instanceof SkillTrigger<?> skillTrigger)) {
                        continue;
                    }
                    names.add(skillTrigger.name());
                    for (int slot = 0; slot < 9; slot++) {
                        PassiveTrigger trigger = PassiveTrigger.pressSlot(slot);
                        if (trigger != null && trigger.matches(skillTrigger)) {
                            slots.add(String.valueOf(slot));
                        }
                    }
                }
                if (!names.isEmpty()) {
                    plugin.debug("Предмет " + describe(item) + " триггеры: " + names);
                }
                if (!slots.isEmpty()) {
                    found++;
                    plugin.debug("Предмет " + describe(item) + " слушает ~onPressSlot" + slots);
                }
            }
            plugin.debug("Предметов MythicCrucible: " + total
                    + ", со скиллами на клавиши хотбара: " + found);
        } catch (Throwable throwable) {
            plugin.debug("Не удалось перечислить скиллы предметов: " + throwable.getMessage());
        }
    }

    // ------------------------------------------------------------ Выполнение
    private boolean execute(Player player, ItemStack stack, PassiveTrigger trigger,
                            int slot, Queue<SkillMechanic> mechanics) {
        SkillManager skillManager = MythicBukkit.inst().getSkillManager();
        if (skillManager == null) {
            return false;
        }

        AbstractPlayer casterEntity = BukkitAdapter.adapt(player);
        SkillCaster caster = skillManager.getCaster(casterEntity);
        if (caster == null) {
            plugin.debug("Нет SkillCaster для " + player.getName() + " — скилл предмета не выполнен.");
            return false;
        }

        @SuppressWarnings("rawtypes")
        SkillTrigger cause = SkillTrigger.get(trigger.triggerName());

        List<AbstractEntity> targets = new ArrayList<>(1);
        targets.add(casterEntity);

        SkillMetadataImpl metadata = new SkillMetadataImpl(
                cause,
                caster,
                casterEntity,
                BukkitAdapter.adapt(player.getLocation()),
                targets,
                new ArrayList<>(),
                1.0f
        );
        // Событие синхронное: cancelevent должен успеть отменить PlayerItemHeldEvent.
        metadata.setIsAsync(false);

        // Те же переменные, что ставит MythicCrucible своим скиллам предмета.
        metadata.getVariables().putString("equip-slot", "HAND");
        metadata.getVariables().putString("slot", String.valueOf(slot));
        metadata.getVariables().putObject("equip-item", stack);

        TriggeredSkill triggered = new TriggeredSkill(metadata);

        for (SkillMechanic mechanic : mechanics) {
            try {
                if (mechanic.isUsableFromCaster(metadata)) {
                    mechanic.execute(metadata);
                }
            } catch (Throwable throwable) {
                plugin.getLogger().log(Level.WARNING, "Ошибка скилла предмета на ~"
                        + trigger.name().toLowerCase() + " у " + player.getName(), throwable);
            }
        }

        boolean cancelled = triggered.getCancelled();
        plugin.debug("Скилл предмета " + describe(stack) + " на " + trigger.triggerName()
                + " (слот " + slot + ") выполнен у " + player.getName()
                + (cancelled ? ", событие отменено" : ""));
        return cancelled;
    }

    // ------------------------------------------------------------ MythicCrucible

    private Object resolveItem(ItemStack stack) {
        try {
            Object crucible = crucibleInstance.invoke(null);
            if (crucible == null) {
                return null;
            }
            Object manager = itemManager.invoke(crucible);
            if (manager == null) {
                return null;
            }
            Object result = getItem.invoke(manager, stack);
            if (result instanceof Optional<?> optional && optional.isPresent()) {
                return optional.get();
            }
        } catch (Throwable throwable) {
            plugin.debug("Не удалось определить предмет MythicCrucible: " + throwable.getMessage());
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Queue<SkillMechanic> findMechanics(Object crucibleItem, PassiveTrigger trigger) {
        try {
            Object value = getSkills.invoke(crucibleItem);
            if (!(value instanceof Map<?, ?> map)) {
                return null;
            }

            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() instanceof SkillTrigger<?> skillTrigger
                        && trigger.matches(skillTrigger)
                        && entry.getValue() instanceof Queue<?> queue) {
                    return (Queue<SkillMechanic>) queue;
                }
            }
        } catch (Throwable throwable) {
            plugin.debug("Не удалось прочитать скиллы предмета MythicCrucible: " + throwable.getMessage());
        }
        return null;
    }

    private void resolve() {
        if (resolved) {
            return;
        }

        try {
            Class<?> crucible = Class.forName(CRUCIBLE_CLASS);
            Class<?> manager = Class.forName(ITEM_MANAGER_CLASS);
            Class<?> skilledItem = Class.forName(SKILLED_ITEM_CLASS);

            crucibleInstance = crucible.getMethod("inst");
            itemManager = crucible.getMethod("getItemManager");
            getItem = manager.getMethod("getItem", ItemStack.class);
            getItems = manager.getMethod("getItems");
            getSkills = skilledItem.getMethod("getSkills");

            available = true;
            resolved = true;
            plugin.debug("Скиллы предметов MythicCrucible доступны: ~onPressSlot0..8.");
        } catch (Throwable throwable) {
            // Crucible может появиться позже (/crucible load) — попробуем ещё раз
            // при следующем нажатии слота, а сообщение напишем только один раз.
            available = false;
            if (!warned) {
                warned = true;
                plugin.getLogger().info("MythicCrucible не найден: ~onPressSlot0..8 работают только"
                        + " для пассивных навыков, скиллы предметов отключены.");
            }
        }
    }

    private static String describe(ItemStack stack) {
        return stack.getType().name();
    }

    private static String describe(Object item) {
        try {
            Object name = item.getClass().getMethod("getInternalName").invoke(item);
            if (name != null) {
                return name.toString();
            }
        } catch (Throwable ignored) {
            // Не CrucibleItem — печатаем как есть.
        }
        return String.valueOf(item);
    }
}
