package ru.shokydo.mythicpassiveskills.manager;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import ru.shokydo.mythicpassiveskills.MythicPassiveSkillsPlugin;
import ru.shokydo.mythicpassiveskills.config.PassiveAction;
import ru.shokydo.mythicpassiveskills.config.PassiveProfile;
import ru.shokydo.mythicpassiveskills.trigger.PassiveTrigger;
import ru.shokydo.mythicpassiveskills.trigger.TriggerSourceCache;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Хранит активные пассивные навыки игроков, запускает interval-задачи
 * и вызывает действия MythicMobs по событиям.
 */
public final class PassiveManager {

    private final MythicPassiveSkillsPlugin plugin;
    private final SkillCasterService caster;
    private final TriggerSourceCache sources;

    private final Map<String, PassiveProfile> profiles = new LinkedHashMap<>();
    private final Map<UUID, Map<String, ActivePassive>> active = new HashMap<>();
    /**
     * Защита от рекурсии: ключ — "uuid|profileId|trigger". Блокируется только та же
     * пассивка на том же триггере, а не все пассивки игрока. Раньше здесь был
     * Set<UUID> на игрока, из-за чего сторонний аддон, кастующий скилл на ЛКМ/
     * SWAP/DROP, глушил все остальные пассивки в этом тике.
     */
    private final Set<String> casting = new HashSet<>();

    public PassiveManager(MythicPassiveSkillsPlugin plugin) {
        this.plugin = plugin;
        this.caster = new SkillCasterService(plugin);
        this.sources = new TriggerSourceCache(plugin);
    }

    public TriggerSourceCache getTriggerSources() {
        return sources;
    }

    public void reload(Collection<PassiveProfile> loaded) {
        clearAll();
        profiles.clear();
        caster.clearWarnings();

        for (PassiveProfile profile : loaded) {
            profiles.put(profile.id(), profile);
        }

        refreshAll();

        int enabled = 0;
        for (PassiveProfile profile : profiles.values()) {
            if (profile.enabled()) {
                enabled++;
            }
        }
        plugin.getLogger().info("Загружено пассивных навыков: " + profiles.size()
                + " (включено: " + enabled + ").");
    }

    public Collection<PassiveProfile> getProfiles() {
        return Collections.unmodifiableCollection(profiles.values());
    }

    public PassiveProfile getProfile(String id) {
        return profiles.get(id);
    }

    public List<PassiveProfile> getActiveProfiles(Player player) {
        Map<String, ActivePassive> map = active.get(player.getUniqueId());
        if (map == null || map.isEmpty()) {
            return List.of();
        }

        List<PassiveProfile> result = new ArrayList<>(map.size());
        for (ActivePassive passive : map.values()) {
            result.add(passive.profile());
        }
        return result;
    }

    public boolean isActive(Player player, String profileId) {
        Map<String, ActivePassive> map = active.get(player.getUniqueId());
        return map != null && map.containsKey(profileId);
    }

    public void refreshAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            refresh(player);
        }
    }

    /**
     * Синхронизирует активные пассивки игрока с текущими правами и загруженными профилями.
     */
    public void refresh(Player player) {
        UUID uuid = player.getUniqueId();
        Map<String, ActivePassive> map = active.computeIfAbsent(uuid, key -> new LinkedHashMap<>());

        Iterator<Map.Entry<String, ActivePassive>> iterator = map.entrySet().iterator();
        while (iterator.hasNext()) {
            ActivePassive passive = iterator.next().getValue();
            PassiveProfile profile = passive.profile();

            boolean valid;
            if (passive.mechanic()) {
                valid = profile.enabled() && profile.isGranted(player);
            } else {
                valid = profiles.containsKey(profile.id())
                        && profile.enabled()
                        && profile.isGranted(player);
            }

            if (!valid) {
                passive.cancel();
                iterator.remove();
                plugin.debug("Пассивка " + profile.id() + " остановлена у " + player.getName());
            }
        }

        for (PassiveProfile profile : profiles.values()) {
            if (!profile.enabled() || !profile.isGranted(player)) {
                continue;
            }
            if (map.containsKey(profile.id())) {
                continue;
            }
            start(player, profile, false);
        }

        if (map.isEmpty()) {
            active.remove(uuid);
        }
    }

    /**
     * Активирует пассивку, созданную механикой {@code passive{...}}.
     *
     * @return {@code true}, если пассивка активна (в том числе была активна раньше)
     */
    public boolean activateMechanic(Player player, PassiveProfile profile) {
        if (!profile.enabled() || !profile.isGranted(player)) {
            return false;
        }

        Map<String, ActivePassive> map = active.computeIfAbsent(player.getUniqueId(), key -> new LinkedHashMap<>());
        if (map.containsKey(profile.id())) {
            return true;
        }

        start(player, profile, true);
        plugin.debug("Механика passive активировала " + profile.id() + " у " + player.getName());
        return true;
    }

    /**
     * Немедленно останавливает конкретную пассивку игрока.
     */
    public void deactivate(Player player, String profileId) {
        remove(player.getUniqueId(), profileId);
    }

    private void start(Player player, PassiveProfile profile, boolean mechanic) {
        Map<String, ActivePassive> map = active.computeIfAbsent(player.getUniqueId(), key -> new LinkedHashMap<>());
        ActivePassive passive = new ActivePassive(profile, mechanic);
        map.put(profile.id(), passive);

        UUID uuid = player.getUniqueId();
        for (PassiveAction action : profile.actions()) {
            if (action.trigger() != PassiveTrigger.INTERVAL) {
                continue;
            }

            int interval = Math.max(1, action.interval());
            BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin,
                    () -> intervalTick(uuid, profile.id(), action), interval, interval);
            passive.addTask(task);
        }
    }

    private void intervalTick(UUID uuid, String profileId, PassiveAction action) {
        ActivePassive passive = getActive(uuid, profileId);
        if (passive == null) {
            return;
        }

        Player player = Bukkit.getPlayer(uuid);
        if (player == null || !player.isOnline()) {
            remove(uuid, profileId);
            return;
        }

        // Пока игрок мёртв, interval-навыки не выполняем: задача переживёт респавн.
        if (player.isDead()) {
            return;
        }

        PassiveProfile profile = passive.profile();
        if (!profile.enabled()
                || (!passive.mechanic() && !profiles.containsKey(profile.id()))) {
            remove(uuid, profileId);
            return;
        }

        if (plugin.getConfig().getBoolean("settings.recheck-permission-each-tick", true)
                && !profile.isGranted(player)) {
            remove(uuid, profileId);
            return;
        }

        cast(player, profile, action, null, false);
    }

    /**
     * Вызывает все действия активных пассивок игрока с указанным триггером.
     *
     * @param trigger триггер события
     * @param context сущность-контекст события ({@code @trigger})
     * @param held    предмет в основной руке (может быть {@code null}) — нужен для
     *                настроек {@code passive-routing.item-triggers}: строки пассивки
     *                сопоставляются с триггерами, объявленными у самого предмета
     * @return {@code true}, если хотя бы одно действие пассивки реально выполнилось
     */
    public boolean fire(Player player, PassiveTrigger trigger, Entity context, ItemStack held) {
        if (player == null || !player.isOnline()) {
            return false;
        }

        Map<String, ActivePassive> map = active.get(player.getUniqueId());
        if (map == null || map.isEmpty()) {
            return false;
        }

        boolean anyCast = false;
        List<ActivePassive> snapshot = new ArrayList<>(map.values());
        for (ActivePassive passive : snapshot) {
            PassiveProfile profile = passive.profile();

            List<PassiveAction> matching = new ArrayList<>();
            for (PassiveAction action : profile.actions()) {
                if (action.trigger() == trigger) {
                    matching.add(action);
                }
            }
            if (matching.isEmpty()) {
                continue;
            }

            if (!profile.enabled() || (!passive.mechanic() && !profiles.containsKey(profile.id()))) {
                remove(player.getUniqueId(), profile.id());
                continue;
            }

            if (!profile.isGranted(player)) {
                remove(player.getUniqueId(), profile.id());
                continue;
            }

            for (PassiveAction action : matching) {
                // Уступаем событие предмету только если пассивка на этом триггере
                // уже была обслужена нашим кликом по тому же предмету.
                // Без Mythic-предмета в руке никаких проверок нет — пассивка работает.
                Optional<String> itemId = held == null
                        ? Optional.empty()
                        : sources.mythicItemId(held);

                if (itemId.isPresent()) {
                    String id = itemId.get();
                    boolean served = sources.isServed(player, id, trigger.name());

                    if (!action.accepts(itemId, trigger, sources) && !served) {
                        plugin.debug("Пассивка " + profile.id() + " пропущена: триггер "
                                + trigger.name() + " у предмета " + id + " ведёт MythicCrucible/MythicMobs");
                        continue;
                    }

                    // Наш клик по предмету: вырезаем сам предмет из @self, чтобы скилл
                    // не запускался второй раз его же событием (как это делает
                    // MythicCrucible при касте через item-caster).
                    boolean suppress = !sources.itemHasTrigger(id, trigger.name());
                    if (cast(player, profile, action, context, suppress)) {
                        anyCast = true;
                        if (suppress) {
                            sources.markServed(player, id, trigger);
                        }
                    }
                    continue;
                }

                if (cast(player, profile, action, context, false)) {
                    anyCast = true;
                }
            }
        }

        return anyCast;
    }

    public boolean fire(Player player, PassiveTrigger trigger, Entity context) {
        return fire(player, trigger, context, null);
    }

    /**
     * @return {@code true}, если у игрока есть активная пассивка на этом триггере
     */
    public boolean hasClickPassive(Player player, PassiveTrigger trigger) {
        Map<String, ActivePassive> map = active.get(player.getUniqueId());
        if (map == null || map.isEmpty()) {
            return false;
        }
        for (ActivePassive passive : map.values()) {
            if (passive.profile().hasTrigger(trigger)) {
                return true;
            }
        }
        return false;
    }

    private boolean cast(Player player, PassiveProfile profile, PassiveAction action, Entity context,
                         boolean suppressHeldItem) {
        // Блокируем только ту же пассивку на том же триггере (защита от рекурсии),
        // остальные пассивки игрока продолжают работать.
        String key = player.getUniqueId() + "|" + profile.id() + "|" + action.trigger().name();
        if (!casting.add(key)) {
            return false;
        }

        try {
            boolean ok = caster.cast(player, profile, action, context, suppressHeldItem);
            return ok;
        } finally {
            casting.remove(key);
        }
    }

    public void clear(Player player) {
        clear(player.getUniqueId());
    }

    public void clear(UUID uuid) {
        Map<String, ActivePassive> map = active.remove(uuid);
        if (map == null) {
            return;
        }
        for (ActivePassive passive : map.values()) {
            passive.cancel();
        }
    }

    /**
     * Останавливает пассивки игроков в выгружаемом мире.
     * После загрузки мира пассивки будут включены снова при следующем refresh.
     */
    public void clearWorld(World world) {
        List<UUID> affected = new ArrayList<>();
        for (Map.Entry<UUID, Map<String, ActivePassive>> entry : active.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null && player.getWorld().equals(world)) {
                affected.add(entry.getKey());
            }
        }

        for (UUID uuid : affected) {
            clear(uuid);
        }
    }

    public void shutdown() {
        clearAll();
        casting.clear();
    }

    private void clearAll() {
        for (Map<String, ActivePassive> map : active.values()) {
            for (ActivePassive passive : map.values()) {
                passive.cancel();
            }
        }
        active.clear();
    }

    private ActivePassive getActive(UUID uuid, String profileId) {
        Map<String, ActivePassive> map = active.get(uuid);
        return map == null ? null : map.get(profileId);
    }

    private void remove(UUID uuid, String profileId) {
        Map<String, ActivePassive> map = active.get(uuid);
        if (map == null) {
            return;
        }

        ActivePassive passive = map.remove(profileId);
        if (passive != null) {
            passive.cancel();
        }
        if (map.isEmpty()) {
            active.remove(uuid);
        }
    }
}
