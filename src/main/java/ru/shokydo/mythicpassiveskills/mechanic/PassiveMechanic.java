package ru.shokydo.mythicpassiveskills.mechanic;

import io.lumine.mythic.api.adapters.AbstractEntity;
import io.lumine.mythic.api.config.MythicLineConfig;
import io.lumine.mythic.api.skills.ITargetedEntitySkill;
import io.lumine.mythic.api.skills.SkillMetadata;
import io.lumine.mythic.api.skills.SkillResult;
import io.lumine.mythic.api.skills.ThreadSafetyLevel;
import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.core.skills.SkillCondition;
import io.lumine.mythic.core.skills.SkillExecutor;
import io.lumine.mythic.core.skills.SkillMechanic;
import org.bukkit.entity.Player;
import ru.shokydo.mythicpassiveskills.MythicPassiveSkillsPlugin;
import ru.shokydo.mythicpassiveskills.config.PassiveAction;
import ru.shokydo.mythicpassiveskills.config.PassiveActionFactory;
import ru.shokydo.mythicpassiveskills.config.PassiveProfile;
import ru.shokydo.mythicpassiveskills.trigger.PassiveTrigger;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Level;

/**
 * Механика MythicMobs {@code passive{...}}, включающая пассивный навык для игрока.
 * <p>
 * Пример использования в скилле:
 * <pre>
 * ExampleSkill:
 *   Skills:
 *     - passive{permission=mythicpassives.fire;skill=FireAura;interval=40;trigger=interval}
 * </pre>
 * Механика создаёт профиль пассивки и передаёт его в
 * {@link ru.shokydo.mythicpassiveskills.manager.PassiveManager}.
 * Повторный вызов с теми же параметрами ничего не дублирует.
 * <p>
 * Профиль строится при первом действительном вызове, а не в конструкторе:
 * конструктор срабатывает при разборе скиллов, когда триггеры MythicCrucible
 * и другие аддоны ещё могут быть не зарегистрированы.
 */
public final class PassiveMechanic extends SkillMechanic implements ITargetedEntitySkill {

    private final MythicPassiveSkillsPlugin plugin;
    private final MythicLineConfig config;
    private volatile PassiveProfile profile;
    private volatile boolean configError;

    public PassiveMechanic(MythicPassiveSkillsPlugin plugin, SkillExecutor manager, String line, MythicLineConfig config) {
        super(manager, line, config);
        this.plugin = plugin;
        this.config = config;
        this.threadSafetyLevel = ThreadSafetyLevel.SYNC_ONLY;
    }

    @Override
    public SkillResult castAtEntity(SkillMetadata data, AbstractEntity target) {
        PassiveProfile profile = profile();
        if (configError) {
            return SkillResult.INVALID_CONFIG;
        }
        if (profile == null) {
            return SkillResult.INVALID_CONFIG;
        }
        if (target == null || !target.isPlayer()) {
            return SkillResult.INVALID_TARGET;
        }

        Player player;
        try {
            player = (Player) target.getBukkitEntity();
        } catch (ClassCastException exception) {
            return SkillResult.INVALID_TARGET;
        }

        if (!player.isOnline()) {
            return SkillResult.INVALID_TARGET;
        }

        var passiveManager = plugin.getPassiveManager();
        if (passiveManager == null) {
            return SkillResult.ERROR;
        }

        if (!profile.isGranted(player)) {
            passiveManager.deactivate(player, profile.id());
            return SkillResult.CONDITION_FAILED;
        }

        if (!passiveManager.activateMechanic(player, profile)) {
            return SkillResult.CONDITION_FAILED;
        }

        return SkillResult.SUCCESS;
    }

    @Override
    public boolean getTargetsCreatives() {
        return true;
    }

    /**
     * Механика passive должна выполняться независимо от того, каким триггером
     * был вызван родительский скилл. Параметр {@code trigger} относится к самой
     * пассивке, а не к механике, поэтому штатную проверку отключаем.
     */
    @Override
    protected boolean checkSkillTrigger(SkillMetadata data) {
        return true;
    }

    private PassiveProfile profile() {
        PassiveProfile current = profile;
        if (current != null || configError) {
            return current;
        }

        synchronized (this) {
            if (profile == null && !configError) {
                profile = buildProfile(getManager(), config);
                if (profile == null) {
                    configError = true;
                }
            }
            return profile;
        }
    }

    private PassiveProfile buildProfile(SkillExecutor manager, MythicLineConfig config) {
        List<String> skillLines;
        String rawSkills = config.getString(new String[]{"skill", "skills", "s"});
        if (rawSkills != null && PassiveActionFactory.isFullLine(rawSkills)) {
            skillLines = List.of(rawSkills.trim());
        } else {
            skillLines = splitList(rawSkills);
        }

        if (skillLines.isEmpty()) {
            plugin.getLogger().warning("Механика passive вызвана без параметра skill — навык не будет создан.");
            return null;
        }

        String rawTrigger = config.getString(new String[]{"trigger", "t", "on"});
        PassiveTrigger trigger = PassiveTrigger.parse(rawTrigger);
        if (trigger == null) {
            plugin.getLogger().warning("Механика passive: неизвестный триггер '" + rawTrigger
                    + "', используется interval.");
            trigger = PassiveTrigger.INTERVAL;
        }

        int interval = Math.max(1,
                config.getInteger(new String[]{"interval", "i", "period", "every"}, 20));

        Set<String> permissions = new LinkedHashSet<>();
        for (String permission : splitList(config.getString(new String[]{"permission", "perm", "p"}))) {
            permissions.add(permission);
        }
        for (String permission : nullSafe(config.getStringList(
                new String[]{"permissions", "perms"}, null))) {
            if (!permission.isBlank()) {
                permissions.add(permission.trim());
            }
        }

        List<String> conditionLines = new ArrayList<>();
        for (String line : nullSafe(config.getStringList(
                new String[]{"conditions", "condition", "c"}, null))) {
            String cleaned = line == null ? "" : line.trim();
            while (cleaned.startsWith("?")) {
                cleaned = cleaned.substring(1).trim();
            }
            if (!cleaned.isEmpty()) {
                conditionLines.add(cleaned);
            }
        }

        List<SkillCondition> conditions = compileConditions(conditionLines);
        List<PassiveAction.ConditionSpec> conditionSpecs = PassiveAction.pairConditions(conditionLines, conditions);
        List<PassiveAction> actions = plugin.getConfigLoader() != null
                ? plugin.getConfigLoader().buildActions(skillLines, getFile(), trigger, interval, conditionSpecs)
                : new PassiveActionFactory(plugin).build(skillLines, getFile(), trigger, interval,
                        conditionSpecs, PassiveAction.TriggerRouting.ALL);
        if (actions.isEmpty()) {
            plugin.getLogger().warning("Механика passive: не удалось создать ни одного действия.");
            return null;
        }

        String id = buildId(skillLines, trigger, interval, permissions);

        return new PassiveProfile(
                id,
                "mechanic",
                true,
                List.copyOf(permissions),
                actions,
                List.copyOf(conditionLines)
        );
    }

    private List<SkillCondition> compileConditions(List<String> lines) {
        if (lines.isEmpty()) {
            return List.of();
        }

        try {
            var skillManager = MythicBukkit.inst().getSkillManager();
            if (skillManager == null) {
                return List.of();
            }
            List<SkillCondition> conditions = skillManager.getConditions(lines);
            return conditions == null ? List.of() : List.copyOf(conditions);
        } catch (Throwable throwable) {
            plugin.getLogger().log(Level.WARNING,
                    "Не удалось разобрать условия механики passive: " + lines, throwable);
            return List.of();
        }
    }

    private String buildId(List<String> skills, PassiveTrigger trigger, int interval, Set<String> permissions) {
        String id = "mechanic:" + String.join(",", skills)
                + ":" + trigger.name().toLowerCase(Locale.ROOT)
                + ":" + interval
                + ":" + String.join(",", permissions);
        return id.length() > 160 ? id.substring(0, 160) : id;
    }

    private List<String> splitList(String raw) {
        return PassiveActionFactory.splitTopLevel(raw);
    }

    private List<String> nullSafe(List<String> values) {
        return values == null ? List.of() : values;
    }
}
