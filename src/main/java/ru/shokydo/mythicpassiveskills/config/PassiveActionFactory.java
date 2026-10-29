package ru.shokydo.mythicpassiveskills.config;

import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.core.skills.MetaSkill;
import io.lumine.mythic.core.skills.SkillCondition;
import io.lumine.mythic.core.skills.SkillExecutor;
import ru.shokydo.mythicpassiveskills.MythicPassiveSkillsPlugin;
import ru.shokydo.mythicpassiveskills.trigger.PassiveTrigger;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Превращает строки из секции {@code Skills:} в {@link PassiveAction}.
 * <p>
 * Полная строка MythicMobs определяется по наличию {@code {}, @, ~} или {@code ?}.
 * Всё остальное считается именем скилла старого формата. Старые поля
 * {@code Trigger} и {@code Interval} используются как значения по умолчанию,
 * если в строке нет собственного {@code ~триггера}.
 */
public final class PassiveActionFactory {

    private static final Pattern TRIGGER_PATTERN =
            Pattern.compile("~([A-Za-z_][A-Za-z0-9_]*)(?::(\\d+))?");

    private final MythicPassiveSkillsPlugin plugin;

    public PassiveActionFactory(MythicPassiveSkillsPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * @param lines           строки из {@code Skills:}
     * @param file            файл, к которому относится профиль (для сообщений MythicMobs)
     * @param fallbackTrigger триггер из старого поля {@code Trigger}
     * @param fallbackInterval интервал из старого поля {@code Interval}
     * @param conditions      дополнительные условия профиля
     * @param routing         настройки {@code passive-routing.item-triggers}
     * @return список действий; неподдерживаемые строки пропускаются с предупреждением
     */
    public List<PassiveAction> build(List<String> lines,
                                     File file,
                                     PassiveTrigger fallbackTrigger,
                                     int fallbackInterval,
                                     List<PassiveAction.ConditionSpec> conditions,
                                     PassiveAction.TriggerRouting routing) {
        List<PassiveAction> actions = new ArrayList<>(lines.size());
        for (String rawLine : lines) {
            PassiveAction action = buildOne(rawLine, file, fallbackTrigger, fallbackInterval, conditions, routing);
            if (action != null) {
                actions.add(action);
            }
        }
        return actions;
    }

    private PassiveAction buildOne(String rawLine,
                                   File file,
                                   PassiveTrigger fallbackTrigger,
                                   int fallbackInterval,
                                   List<PassiveAction.ConditionSpec> profileConditions,
                                   PassiveAction.TriggerRouting routing) {
        String line = rawLine == null ? "" : rawLine.trim();
        if (line.isEmpty()) {
            return null;
        }

        if (!isFullLine(line)) {
            // Имя скилла без строки: такое действие привязано к триггеру профиля,
            // поэтому для него важен только уровень «по умолчанию» из конфига.
            return PassiveAction.named(line, fallbackTrigger, fallbackInterval,
                    fallbackTrigger.skillTrigger(), profileConditions, routing);
        }

        // 1. Триггер из строки (~onPressF, ~onTimer:40) разбираем сами.
        PassiveTrigger trigger = fallbackTrigger;
        int interval = fallbackInterval;
        Matcher matcher = TRIGGER_PATTERN.matcher(line);
        if (matcher.find()) {
            PassiveTrigger parsed = PassiveTrigger.parseExact(matcher.group(1));
            if (parsed == null) {
                plugin.getLogger().warning("Неизвестный триггер '~" + matcher.group(1)
                        + "' в строке пассивки: " + line);
                return null;
            }
            if (parsed == PassiveTrigger.INTERVAL && matcher.group(2) == null) {
                plugin.getLogger().warning("У '~onTimer' нет интервала в строке: " + line
                        + ". Используется " + fallbackInterval + " тиков.\n"
                        + "Пример: '~onTimer:20' — 20 тиков = 1 секунда.");
            }

            trigger = parsed;
            if (parsed == PassiveTrigger.INTERVAL && matcher.group(2) != null) {
                try {
                    interval = Integer.parseInt(matcher.group(2));
                } catch (NumberFormatException exception) {
                    plugin.getLogger().warning("Неверный интервал '" + matcher.group(2)
                            + "' в строке пассивки: " + line + ". Используется " + fallbackInterval + ".");
                    interval = fallbackInterval;
                }
            }
        }

        // 2. Разрезаем строку на токены верхнего уровня: механика/таргетер,
        //    ~триггер и ?условия. Триггер вырезаем — MythicMobs не должен
        //    видеть его в строке MetaSkill, иначе компиляция ломается на
        //    кастомных триггерах. Условия проверяет наша система (conditionsPass).
        List<String> tokens = splitTokens(line);
        List<String> mechanicTokens = new ArrayList<>();
        List<String> inlineConditions = new ArrayList<>();
        String passiveFilter = null;
        for (String token : tokens) {
            if (token.startsWith("~")) {
                continue;
            }
            if (token.startsWith("?")) {
                String condition = token.substring(1).trim();
                if (condition.isEmpty()) {
                    continue;
                }

                // ?passive=<id> — не условие MythicMobs, а фильтр строки
                // ~onStartPassive / ~onEndPassive: выполнить только для этой пассивки.
                String bare = condition.startsWith("!") ? condition.substring(1).trim() : condition;
                if (bare.toLowerCase(java.util.Locale.ROOT).startsWith("passive=")) {
                    passiveFilter = bare.substring("passive=".length()).trim();
                    continue;
                }

                inlineConditions.add(condition);
                continue;
            }
            mechanicTokens.add(token);
        }

        if (mechanicTokens.isEmpty()) {
            plugin.getLogger().warning("В строке пассивки нет механики: " + line);
            return null;
        }

        String mechanicPart = String.join(" ", mechanicTokens);

        // 3. Условия строки добавляются к условиям профиля. Поддерживается отрицание
        //    через "!" (например ?!sneaking): MythicMobs сам не понимает "!" в строке,
        //    поэтому инвертируем результат проверки сами.
        List<PassiveAction.ConditionSpec> conditions = new ArrayList<>(profileConditions);
        if (!inlineConditions.isEmpty()) {
            List<String> cleaned = new ArrayList<>(inlineConditions.size());
            List<Boolean> negated = new ArrayList<>(inlineConditions.size());
            for (String cond : inlineConditions) {
                boolean neg = cond.startsWith("!");
                String name = neg ? cond.substring(1).trim() : cond;
                if (name.isEmpty()) {
                    plugin.getLogger().warning("Пустое условие в строке пассивки: " + line);
                    return null;
                }
                cleaned.add(name);
                negated.add(neg);
            }
            List<SkillCondition> parsedConditions = compileConditions(line, cleaned);
            if (parsedConditions == null) {
                return null;
            }
            for (int i = 0; i < parsedConditions.size(); i++) {
                conditions.add(new PassiveAction.ConditionSpec(
                        parsedConditions.get(i), i < negated.size() && negated.get(i)));
            }
        }

        try {
            SkillExecutor executor = MythicBukkit.inst().getSkillManager();
            MetaSkill skill = new MetaSkill(executor, safeFile(file), List.of(mechanicPart));
            skill.setInlineSkill(true);
            return PassiveAction.compiled(line, trigger, Math.max(1, interval),
                    skill, trigger.skillTrigger(), conditions, routing).withPassiveFilter(passiveFilter);
        } catch (Throwable throwable) {
            plugin.getLogger().log(Level.WARNING,
                    "Не удалось разобрать строку пассивки: " + line, throwable);
            return null;
        }
    }



    /**
     * Компилирует условия, вырезанные из строки пассивки.
     *
     * @return список условий или {@code null}, если разобрать не удалось
     */
    private List<SkillCondition> compileConditions(String source, List<String> lines) {
        try {
            var skillManager = MythicBukkit.inst().getSkillManager();
            if (skillManager == null) {
                return List.of();
            }
            List<SkillCondition> compiled = skillManager.getConditions(lines);
            return compiled == null ? List.of() : List.copyOf(compiled);
        } catch (Throwable throwable) {
            plugin.getLogger().log(Level.WARNING,
                    "Не удалось разобрать условия пассивки: " + source, throwable);
            return null;
        }
    }

    /**
     * Делит строку по пробелам верхнего уровня, не затрагивая содержимое
     * {@code {...}}, {@code [...]}, {@code (...)} и кавычек.
     */
    public static List<String> splitTokens(String raw) {
        List<String> result = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            return result;
        }

        StringBuilder current = new StringBuilder();
        int depth = 0;
        char quote = 0;

        for (int i = 0; i < raw.length(); i++) {
            char symbol = raw.charAt(i);

            if (quote != 0) {
                current.append(symbol);
                if (symbol == quote) {
                    quote = 0;
                }
                continue;
            }

            if (symbol == '"' || symbol == '\'') {
                quote = symbol;
                current.append(symbol);
                continue;
            }

            if (symbol == '{' || symbol == '[' || symbol == '(') {
                depth++;
            } else if (symbol == '}' || symbol == ']' || symbol == ')') {
                depth = Math.max(0, depth - 1);
            }

            if (symbol == ' ' && depth == 0) {
                addTrimmed(result, current);
                current.setLength(0);
                continue;
            }

            current.append(symbol);
        }

        addTrimmed(result, current);
        return result;
    }

    private File safeFile(File file) {
        if (file != null) {
            return file;
        }
        return new File(plugin.getDataFolder(), "PassiveSkills/mechanic.yml");
    }

    /**
     * Полная строка — это механика MythicMobs с таргетером, триггером или условием.
     * Имя скилла старого формата не содержит этих символов.
     */
    public static boolean isFullLine(String line) {
        return line.indexOf('{') >= 0
                || line.indexOf('@') >= 0
                || line.indexOf('~') >= 0
                || line.indexOf('?') >= 0;
    }

    /**
     * Делит строку по запятым верхнего уровня, не затрагивая содержимое
     * {@code {...}}, {@code [...]}, {@code (...)} и кавычек.
     */
    public static List<String> splitTopLevel(String raw) {
        List<String> result = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            return result;
        }

        StringBuilder current = new StringBuilder();
        int depth = 0;
        char quote = 0;

        for (int i = 0; i < raw.length(); i++) {
            char symbol = raw.charAt(i);

            if (quote != 0) {
                current.append(symbol);
                if (symbol == quote) {
                    quote = 0;
                }
                continue;
            }

            if (symbol == '"' || symbol == '\'') {
                quote = symbol;
                current.append(symbol);
                continue;
            }

            if (symbol == '{' || symbol == '[' || symbol == '(') {
                depth++;
            } else if (symbol == '}' || symbol == ']' || symbol == ')') {
                depth = Math.max(0, depth - 1);
            }

            if (symbol == ',' && depth == 0) {
                addTrimmed(result, current);
                current.setLength(0);
                continue;
            }

            current.append(symbol);
        }

        addTrimmed(result, current);
        return result;
    }

    private static void addTrimmed(List<String> target, StringBuilder value) {
        String text = value.toString().trim();
        if (!text.isEmpty()) {
            target.add(text);
        }
    }
}
