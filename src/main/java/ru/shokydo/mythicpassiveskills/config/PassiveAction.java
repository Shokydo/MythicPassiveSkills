package ru.shokydo.mythicpassiveskills.config;

import io.lumine.mythic.api.skills.Skill;
import io.lumine.mythic.api.skills.SkillTrigger;
import io.lumine.mythic.core.skills.SkillCondition;
import ru.shokydo.mythicpassiveskills.trigger.PassiveTrigger;
import ru.shokydo.mythicpassiveskills.trigger.TriggerSourceCache;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Одно действие пассивного навыка.
 * <p>
 * Действие — это либо готовая строка MythicMobs
 * ({@code skill{s=FireAura} @self ~onTimer:40 ?health{h=>0}}),
 * либо имя обычного скилла из старого формата ({@code FireAura}).
 */
public final class PassiveAction {

    private final String line;
    private final PassiveTrigger trigger;
    private final int interval;
    private final Skill skill;
    private final String skillName;
    private final SkillTrigger skillTrigger;
    private final List<ConditionSpec> conditions;
    private final TriggerRouting routing;
    /**
     * ID пассивки из {@code ?passive=<id>} для {@code ~onStartPassive}/{@code ~onEndPassive}.
     * {@code null} — сработать при включении/выключении любой пассивки игрока.
     */
    private final String passiveFilter;

    private PassiveAction(String line,
                          PassiveTrigger trigger,
                          int interval,
                          Skill skill,
                          String skillName,
                          SkillTrigger skillTrigger,
                          List<ConditionSpec> conditions,
                          TriggerRouting routing,
                          String passiveFilter) {
        this.line = line;
        this.trigger = trigger;
        this.interval = Math.max(1, interval);
        this.skill = skill;
        this.skillName = skillName;
        this.skillTrigger = skillTrigger;
        this.conditions = List.copyOf(conditions);
        this.routing = routing == null ? TriggerRouting.ALL : routing;
        this.passiveFilter = passiveFilter == null || passiveFilter.isBlank()
                ? null
                : passiveFilter.trim().toLowerCase(java.util.Locale.ROOT);
    }

    /**
     * @param passiveFilter {@code ?passive=<id>} — сработать только на старте/конце
     *                      этой пассивки; {@code null} — на любой
     * @return копия действия с фильтром по ID пассивки
     */
    public PassiveAction withPassiveFilter(String passiveFilter) {
        return new PassiveAction(line, trigger, interval, skill, skillName, skillTrigger,
                conditions, routing, passiveFilter);
    }

    /**
     * Проверяет, нужно ли выполнять действие на событии старта/конца пассивки.
     *
     * @param changedId ID пассивки, которая включилась/выключилась
     */
    public boolean matchesPassive(String changedId) {
        if (passiveFilter == null) {
            return true;
        }
        return changedId != null && getPassiveFilter().equals(changedId.toLowerCase(java.util.Locale.ROOT));
    }

    public String getPassiveFilter() {
        return passiveFilter;
    }

    /**
     * Настройки {@code passive-routing.item-triggers} из {@code config.yml}.
     * <p>
     * {@code true} — пассивка запускается на клик всегда, вместе с предметом
     * MythicCrucible; {@code false} — только если у предмета в руке нет своего
     * скилла на этот триггер (приоритет у MythicCrucible).
     */
    public record TriggerRouting(boolean itemTriggers) {

        public static final TriggerRouting ALL = new TriggerRouting(true);

        /**
         * @param itemId  ID Mythic-предмета в основной руке ({@code null} — не Mythic-предмет)
         * @param sources кэш триггеров предметов
         * @return {@code false}, если это событие для пассивки обрабатывает
         *         MythicCrucible/MythicMobs
         */
        public boolean allowedFor(String itemId, String triggerName, TriggerSourceCache sources) {
            if (itemId == null) {
                return true;
            }
            return itemTriggers || !sources.itemHasTrigger(itemId, triggerName);
        }
    }

    /**
     * Одно условие пассивки.
     * {@code negated} — условие указано с префиксом {@code !} (например {@code ?!sneaking}):
     * результат проверки инвертируется, т.к. MythicMobs сам не понимает "!".
     */
    public record ConditionSpec(SkillCondition condition, boolean negated) {
    }

    /**
     * Оборачивает скомпилированные условия MythicMobs вместе с флагом отрицания.
     * {@code rawLines} — исходные строки условий (уже без "?", но возможно с "!").
     */
    @SuppressWarnings("rawtypes")
    public static List<ConditionSpec> pairConditions(List<String> rawLines, List<SkillCondition> compiled) {
        List<ConditionSpec> result = new ArrayList<>(compiled.size());
        for (int i = 0; i < compiled.size(); i++) {
            boolean negated = i < rawLines.size() && rawLines.get(i).startsWith("!");
            result.add(new ConditionSpec(compiled.get(i), negated));
        }
        return result;
    }

    /**
     * Действие из полной строки MythicMobs, разобранной и скомпилированной заранее.
     */
    public static PassiveAction compiled(String line,
                                         PassiveTrigger trigger,
                                         int interval,
                                         Skill skill,
                                         SkillTrigger skillTrigger,
                                         List<ConditionSpec> conditions,
                                         TriggerRouting routing) {
        return new PassiveAction(line, trigger, interval, skill, null, skillTrigger, conditions, routing, null);
    }

    /**
     * Действие старого формата: имя скилла MythicMobs, которое будет найдено при выполнении.
     */
    public static PassiveAction named(String name,
                                      PassiveTrigger trigger,
                                      int interval,
                                      SkillTrigger skillTrigger,
                                      List<ConditionSpec> conditions,
                                      TriggerRouting routing) {
        return new PassiveAction(name, trigger, interval, null, name, skillTrigger, conditions, routing, null);
    }

    /**
     * Должна ли пассивка обрабатывать событие, если в руке игрока такой предмет.
     *
     * @see TriggerRouting#allowedFor(String, String, TriggerSourceCache)
     */
    public boolean accepts(Optional<String> itemId, PassiveTrigger trigger, TriggerSourceCache sources) {
        return routing.allowedFor(itemId.orElse(null), trigger.name(), sources);
    }

    public TriggerRouting routing() {
        return routing;
    }

    public String line() {
        return line;
    }

    public PassiveTrigger trigger() {
        return trigger;
    }

    public int interval() {
        return interval;
    }

    public Skill skill() {
        return skill;
    }

    public String skillName() {
        return skillName;
    }

    /**
     * Триггер MythicMobs, который передаётся в {@code SkillMetadata} при выполнении.
     */
    public SkillTrigger skillTrigger() {
        return skillTrigger;
    }

    /**
     * Дополнительные условия профиля (старое поле {@code Conditions}) + условия строки.
     * Проверяются нашей системой перед выполнением.
     */
    public List<ConditionSpec> conditions() {
        return conditions;
    }

    /**
     * {@code true} — действие является именем скилла старого формата.
     */
    public boolean isLegacy() {
        return skillName != null;
    }

    /**
     * Короткое описание для {@code /passive list} и {@code /passive info}.
     */
    public String triggerDisplay() {
        String name = trigger.name().toLowerCase(java.util.Locale.ROOT);
        return trigger == PassiveTrigger.INTERVAL ? name + ":" + interval : name;
    }

    @Override
    public String toString() {
        return line;
    }
}
