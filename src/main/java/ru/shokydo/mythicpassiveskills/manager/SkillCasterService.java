package ru.shokydo.mythicpassiveskills.manager;

import io.lumine.mythic.api.adapters.AbstractEntity;
import io.lumine.mythic.api.adapters.AbstractPlayer;
import io.lumine.mythic.api.skills.Skill;
import io.lumine.mythic.api.skills.SkillCaster;
import io.lumine.mythic.api.skills.SkillManager;
import io.lumine.mythic.api.skills.SkillMetadata;
import io.lumine.mythic.api.skills.SkillTrigger;
import io.lumine.mythic.bukkit.BukkitAdapter;
import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.core.skills.SkillMetadataImpl;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import ru.shokydo.mythicpassiveskills.MythicPassiveSkillsPlugin;
import ru.shokydo.mythicpassiveskills.config.PassiveAction;
import ru.shokydo.mythicpassiveskills.config.PassiveProfile;
import ru.shokydo.mythicpassiveskills.trigger.PassiveTrigger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;

/**
 * Выполняет действия пассивных навыков через MythicMobs.
 * <p>
 * Полные строки ({@code skill{s=FireAura} @self ~onTimer:40 ?health{h=>0}})
 * компилируются при загрузке профиля, поэтому штатные условия, кулдауны
 * и таргетеры MythicMobs работают как в обычном скилле.
 * <p>
 * Для кликов по предмету в {@code SkillMetadata} подменяется имя предмета:
 * MythicCrucible запускает скиллы предмета в руке на {@code onUse} при выполнении
 * механики {@code skill{...}}, и без этого один клик запускал бы способность дважды.
 */
public final class SkillCasterService {

    /**\u041a\u0430\u043a \u0447\u0430\u0441\u0442\u043e (\u043c\u0441) \u043f\u043e\u0432\u0442\u043e\u0440\u044f\u0442\u044c \u043e\u0434\u043d\u043e \u0438 \u0442\u043e \u0436\u0435 debug-\u0441\u043e\u043e\u0431\u0449\u0435\u043d\u0438\u0435. */
    private static final long DEBUG_REPEAT_MS = 5000L;

    private final MythicPassiveSkillsPlugin plugin;
    private final Set<String> warnedMissingSkills = new HashSet<>();
    /** \u041a\u043b\u044e\u0447 \u2192 \u0432\u0440\u0435\u043c\u044f \u043f\u043e\u0441\u043b\u0435\u0434\u043d\u0435\u0433\u043e \u043e\u0434\u0438\u043d\u0430\u043a\u043e\u0432\u043e\u0433\u043e debug-\u0441\u043e\u043e\u0431\u0449\u0435\u043d\u0438\u044f. */
    private final Map<String, Long> debugOnceAt = new HashMap<>();

    public SkillCasterService(MythicPassiveSkillsPlugin plugin) {
        this.plugin = plugin;
    }

    public void clearWarnings() {
        warnedMissingSkills.clear();
    }

    @Deprecated
    public boolean cast(Player player, PassiveProfile profile, PassiveAction action, Entity context) {
        return cast(player, profile, action, context, false);
    }

    /**
     * Выполняет действие пассивки.
     *
     * @param suppressHeldItem {@code true}, если клик по предмету в руке уже
     *                         обслужен пассивкой: имя предмета в контексте меняется,
     *                         и MythicCrucible не запускает тот же скилл ещё раз
     *                         событием {@code onUse} предмета
     * @return {@code true}, если событие дошло до выполнения скилла (условия
     *         MythicMobs могли его не пустить) — по этому значению слушатели
     *         решают, отменять ли само Bukkit-событие
     */
    public boolean cast(Player player, PassiveProfile profile, PassiveAction action, Entity context,
                        boolean suppressHeldItem) {
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(plugin, () -> cast(player, profile, action, context, suppressHeldItem));
            return false;
        }

        if (MythicBukkit.inst() == null) {
            return false;
        }

        SkillManager skillManager = MythicBukkit.inst().getSkillManager();
        if (skillManager == null) {
            return false;
        }

        AbstractPlayer casterEntity = BukkitAdapter.adapt(player);
        SkillCaster caster = skillManager.getCaster(casterEntity);
        if (caster == null) {
            plugin.debug("Не найден SkillCaster для " + player.getName() + " (" + profile.id() + ")");
            return false;
        }

        // Триггер MythicMobs-контекста. Он нужен только для условий
        // {@code ~onTrigger}-стиля, поэтому незарегистрированный триггер
        // не отключает пассивку целиком — просто берём безопасный.
        SkillTrigger trigger = action.skillTrigger();

        Skill skill = resolveSkill(skillManager, profile, action);
        if (skill == null) {
            return false;
        }

        AbstractEntity triggerEntity = context != null ? BukkitAdapter.adapt(context) : casterEntity;

        // По умолчанию пассивка применяется к самому игроку (@self / @target),
        // а сущность-контекст события доступна в строке через @trigger.
        List<AbstractEntity> targets = new ArrayList<>(1);
        targets.add(casterEntity);

        SkillMetadata metadata = buildMetadata(trigger, caster, triggerEntity, player, targets);

        if (suppressHeldItem) {
            suppressItemUseLoop(metadata, player);
        }

        boolean inRange = true;
        try {
            inRange = conditionsPass(profile, action, metadata);
        } catch (Throwable throwable) {
            plugin.getLogger().log(Level.WARNING, "Ошибка проверки условий пассивки "
                    + profile.id(), throwable);
        }

        if (!inRange) {
            // Interval-строки проверяются 20 раз в секунду: без ограничения лог
            // превращается в шум, за которым не видно ничего другого.
            String key = "cond|" + player.getUniqueId() + "|" + profile.id();
            long now = System.currentTimeMillis();
            Long last = debugOnceAt.get(key);
            if (last == null || now - last > DEBUG_REPEAT_MS) {
                debugOnceAt.put(key, now);
                plugin.debug("Условия пассивки " + profile.id() + " не выполнены у " + player.getName()
                        + " (дальше повтор этого сообщения — раз в " + (DEBUG_REPEAT_MS / 1000) + " с)");
            }
            return false;
        }

        try {
            SkillTrigger runTrigger = trigger != null ? trigger : PassiveTrigger.INTERVAL.skillTrigger();
            if (skill.executeIfUsable(metadata, runTrigger)) {
                plugin.debug("Действие '" + action.line() + "' выполнено у " + player.getName()
                        + " (" + profile.id() + ", " + action.triggerDisplay() + ")");
            } else {
                plugin.debug("Действие '" + action.line() + "' не выполнено (условия/КД) у "
                        + player.getName() + " (" + profile.id() + ")");
            }
        } catch (Throwable throwable) {
            plugin.getLogger().log(Level.WARNING, "Ошибка выполнения действия '" + action.line()
                    + "' для пассивки " + profile.id(), throwable);
        }

        return true;
    }

    /**
     * Контекст скилла. Если триггер аддона не зарегистрирован в MythicMobs,
     * используется собственный {@code PASSIVE_INTERVAL} — скилл всё равно
     * выполняется.
     */
    @SuppressWarnings({"rawtypes", "unchecked", "deprecation"})
    private SkillMetadata buildMetadata(SkillTrigger trigger, SkillCaster caster,
                                        AbstractEntity triggerEntity, Player player,
                                        List<AbstractEntity> targets) {
        SkillTrigger safeTrigger = trigger != null ? trigger : PassiveTrigger.INTERVAL.skillTrigger();
        return new SkillMetadataImpl(
                safeTrigger,
                caster,
                triggerEntity,
                BukkitAdapter.adapt(player.getLocation()),
                targets,
                new ArrayList<>(),
                1.0f
        );
    }

    /**
     * Не даёт MythicCrucible повторно запустить скилл предмета, по которому
     * только что кликнул игрок.
     * <p>
     * MythicCrucible при выполнении механики {@code skill{...}} прокидывает скилл
     * в предмет в руке и запускает его на {@code onUse}. Для пассивки, уже
     * вызванной кликом по этому же предмету, это был бы запуск того же скилла
     * второй раз — то есть задвоение способности. Подмена {@code itemname}
     * в контексте скилла не даёт Crucible узнать предмет, и повторного запуска
     * не происходит.
     * <p>
     * Подмена живёт только внутри одного {@link SkillMetadata} — глобальное
     * состояние игрока не меняется.
     */
    private void suppressItemUseLoop(SkillMetadata metadata, Player player) {
        if (!plugin.getConfig().getBoolean("passive-routing.resolve-item-use-loop", true)) {
            return;
        }

        try {
            var registry = metadata.getVariables();
            if (registry == null) {
                return;
            }

            ItemStack held = player.getInventory().getItemInMainHand();
            String itemName = plugin.getTriggerSources()
                    .mythicItemId(held)
                    .orElse(held.getType().name() + "_passive");
            registry.putString("itemname", itemName);
        } catch (Throwable throwable) {
            plugin.debug("Не удалось подменить itemname: " + throwable.getMessage());
        }
    }

    private Skill resolveSkill(SkillManager skillManager, PassiveProfile profile, PassiveAction action) {
        if (!action.isLegacy()) {
            return action.skill();
        }

        Optional<Skill> optional = skillManager.getSkill(action.skillName());
        if (optional.isEmpty()) {
            warnMissing(profile, action.skillName());
            return null;
        }
        return optional.get();
    }

    private boolean conditionsPass(PassiveProfile profile, PassiveAction action, SkillMetadata metadata) {
        for (PassiveAction.ConditionSpec spec : action.conditions()) {
            try {
                boolean passed = spec.condition().evaluateCaster(metadata);
                if (spec.negated()) {
                    passed = !passed;
                }
                if (!passed) {
                    return false;
                }
            } catch (Throwable throwable) {
                plugin.getLogger().log(Level.WARNING, "Ошибка проверки условия в пассивке "
                        + profile.id() + ": " + spec.condition().getLine(), throwable);
                return false;
            }
        }
        return true;
    }

    private void warnMissing(PassiveProfile profile, String skillName) {
        if (!warnedMissingSkills.add(profile.id() + "|" + skillName)) {
            return;
        }
        plugin.getLogger().warning("Скилл '" + skillName + "' из пассивки " + profile.id() + " не найден.");
    }
}

