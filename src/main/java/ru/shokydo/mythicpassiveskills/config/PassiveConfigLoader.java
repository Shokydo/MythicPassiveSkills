package ru.shokydo.mythicpassiveskills.config;

import io.lumine.mythic.api.skills.SkillManager;
import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.core.skills.SkillCondition;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import ru.shokydo.mythicpassiveskills.MythicPassiveSkillsPlugin;
import ru.shokydo.mythicpassiveskills.trigger.PassiveTrigger;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Level;

/**
 * Загружает пассивные навыки из {@code plugins/MythicMobs/PassiveSkills/*.yml}.
 * <p>
 * Поддерживаются как секция {@code PassiveSkills:}, так и профили в корне файла.
 * Строки {@code Skills:} могут быть полными механиками MythicMobs
 * ({@code skill{s=FireAura} @self ~onTimer:40 ?health{h=>0}}) или именами скиллов
 * старого формата ({@code FireAura}).
 */
public final class PassiveConfigLoader {

    private static final Set<String> META_SECTIONS = Set.of(
            "settings", "config", "meta", "defaults", "passiveskills"
    );

    private final MythicPassiveSkillsPlugin plugin;
    private final PassiveActionFactory actionFactory;
    private final File directory;
    private final PassiveAction.TriggerRouting routing;

    public PassiveConfigLoader(MythicPassiveSkillsPlugin plugin) {
        this.plugin = plugin;
        this.actionFactory = new PassiveActionFactory(plugin);
        Plugin mythic = plugin.getServer().getPluginManager().getPlugin("MythicMobs");
        File dataFolder = mythic != null
                ? mythic.getDataFolder()
                : new File(plugin.getDataFolder().getParentFile(), "MythicMobs");
        this.directory = new File(dataFolder, "PassiveSkills");
        this.routing = readRouting(plugin);
    }

    /**
     * Правила маршрутизации, применённые при загрузке профилей.
     */
    public PassiveAction.TriggerRouting getRouting() {
        return routing;
    }

    /**
     * Читает {@code passive-routing.item-triggers} из {@code config.yml}:
     * работает ли пассивка параллельно с предметом MythicCrucible
     * или уступает ему клик при конфликте триггеров.
     */
    private static PassiveAction.TriggerRouting readRouting(MythicPassiveSkillsPlugin plugin) {
        return new PassiveAction.TriggerRouting(
                plugin.getConfig().getBoolean("passive-routing.item-triggers", true));
    }

    public File getDirectory() {
        return directory;
    }

    /**
     * Разбирает строки {@code Skills:} с теми же правилами маршрутизации,
     * что и файловые профили. Используется механикой {@code passive{...}},
     * чтобы поведение кликов совпадало.
     */
    public List<PassiveAction> buildActions(List<String> skills,
                                            File file,
                                            PassiveTrigger trigger,
                                            int interval,
                                            List<PassiveAction.ConditionSpec> conditions) {
        return actionFactory.build(skills, file, trigger, interval, conditions, routing);
    }

    public void ensureDirectory() {
        if (!directory.exists() && !directory.mkdirs()) {
            plugin.getLogger().severe("Не удалось создать папку " + directory.getAbsolutePath());
            return;
        }

        File[] files = directory.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files != null && files.length > 0) {
            return;
        }

        try (InputStream in = plugin.getResource("example-passive.yml")) {
            if (in == null) {
                return;
            }

            File target = new File(directory, "example.yml");
            if (target.exists()) {
                return;
            }

            Files.copy(in, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            plugin.getLogger().info("Создан пример пассивных навыков: " + target.getAbsolutePath());
        } catch (Exception exception) {
            plugin.getLogger().log(Level.WARNING, "Не удалось создать пример пассивных навыков", exception);
        }
    }

    public List<PassiveProfile> load() {
        ensureDirectory();
        List<PassiveProfile> result = new ArrayList<>();

        File[] files = directory.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files == null || files.length == 0) {
            return result;
        }

        for (File file : files) {
            try {
                loadFile(file, result);
            } catch (Exception exception) {
                plugin.getLogger().log(Level.WARNING, "Ошибка загрузки " + file.getName(), exception);
            }
        }

        result.sort(Comparator.comparing(PassiveProfile::id));
        return result;
    }

    private void loadFile(File file, List<PassiveProfile> result) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        String fileBase = file.getName().substring(0, file.getName().length() - ".yml".length());

        ConfigurationSection root = yaml.getConfigurationSection("PassiveSkills");
        if (root == null) {
            root = yaml;
        }

        for (String key : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(key);
            if (section == null) {
                continue;
            }
            if (META_SECTIONS.contains(key.toLowerCase(Locale.ROOT))) {
                continue;
            }

            String id = fileBase + ":" + key;
            PassiveProfile profile = parseProfile(id, file, section);
            if (profile != null) {
                result.add(profile);
            }
        }
    }

    private PassiveProfile parseProfile(String id, File file, ConfigurationSection section) {
        List<String> skills = readSkills(section);
        if (skills.isEmpty()) {
            plugin.getLogger().warning("Профиль " + id + " (" + file.getName()
                    + ") пропущен: не указан Skills.");
            return null;
        }

        String rawTrigger = readString(section, "INTERVAL", "Trigger", "trigger", "On", "on");
        PassiveTrigger trigger = PassiveTrigger.parse(rawTrigger);
        if (trigger == null) {
            plugin.getLogger().warning("Профиль " + id + " (" + file.getName()
                    + ") пропущен: неизвестный Trigger '" + rawTrigger + "'.");
            return null;
        }

        int interval = readInteger(section, 20, "Interval", "interval", "Period", "period", "Every", "every");
        if (interval < 1) {
            plugin.getLogger().warning("Профиль " + id + ": Interval должен быть >= 1, используется 20.");
            interval = 20;
        }

        boolean enabled = readBoolean(section, true, "Enabled", "enabled");
        List<String> permissions = readPermissions(section);

        List<String> conditionLines = readStringList(section, false,
                "Conditions", "conditions", "Condition", "condition");
        List<String> cleanedConditions = new ArrayList<>(conditionLines.size());
        for (String line : conditionLines) {
            String cleaned = line == null ? "" : line.trim();
            while (cleaned.startsWith("?")) {
                cleaned = cleaned.substring(1).trim();
            }
            if (!cleaned.isEmpty()) {
                cleanedConditions.add(cleaned);
            }
        }

        List<SkillCondition> conditions = compileConditions(id, file.getName(), cleanedConditions);
        if (conditions == null) {
            return null;
        }

        List<PassiveAction.ConditionSpec> conditionSpecs = PassiveAction.pairConditions(cleanedConditions, conditions);

        List<PassiveAction> actions = actionFactory.build(skills, file, trigger, interval, conditionSpecs, routing);
        if (actions.isEmpty()) {
            plugin.getLogger().warning("Профиль " + id + " (" + file.getName()
                    + ") пропущен: не удалось разобрать ни одной строки Skills.");
            return null;
        }

        if (enabled) {
            warnMissingLegacySkills(id, actions);
        } else {
            plugin.debug("Профиль " + id + " выключен (Enabled: false).");
        }

        return new PassiveProfile(id, file.getName(), enabled, permissions, actions, cleanedConditions);
    }

    private void warnMissingLegacySkills(String id, List<PassiveAction> actions) {
        SkillManager skillManager = MythicBukkit.inst().getSkillManager();
        if (skillManager == null) {
            return;
        }

        List<String> missing = new ArrayList<>();
        for (PassiveAction action : actions) {
            if (!action.isLegacy()) {
                continue;
            }
            if (skillManager.getSkill(action.skillName()).isEmpty()) {
                missing.add(action.skillName());
            }
        }

        if (!missing.isEmpty()) {
            plugin.getLogger().warning("Профиль " + id + " ссылается на отсутствующие скиллы: "
                    + String.join(", ", missing) + " (проверьте plugins/MythicMobs/Skills).");
        }
    }

    private List<SkillCondition> compileConditions(String id, String source, List<String> lines) {
        if (lines.isEmpty()) {
            return List.of();
        }

        try {
            SkillManager skillManager = MythicBukkit.inst().getSkillManager();
            if (skillManager == null) {
                return List.of();
            }

            List<SkillCondition> compiled = skillManager.getConditions(lines);
            return compiled == null ? List.of() : List.copyOf(compiled);
        } catch (Throwable throwable) {
            plugin.getLogger().log(Level.WARNING, "Профиль " + id + " (" + source
                    + ") пропущен: не удалось разобрать Conditions " + lines, throwable);
            return null;
        }
    }

    /**
     * Читает {@code Skills:}. Полные строки MythicMobs не разбиваются по запятым
     * внутри параметров механики.
     */
    private List<String> readSkills(ConfigurationSection section) {
        for (String key : new String[]{"Skills", "skills", "Skill", "skill"}) {
            if (section.isList(key)) {
                List<String> values = new ArrayList<>();
                for (String value : section.getStringList(key)) {
                    if (value != null && !value.isBlank()) {
                        values.add(value.trim());
                    }
                }
                return values;
            }

            String scalar = section.getString(key);
            if (scalar != null && !scalar.isBlank()) {
                return PassiveActionFactory.splitTopLevel(scalar);
            }
        }
        return List.of();
    }

    private List<String> readPermissions(ConfigurationSection section) {
        LinkedHashSet<String> result = new LinkedHashSet<>();

        for (String key : new String[]{"Permission", "permission", "Perm", "perm"}) {
            String value = section.getString(key);
            if (value == null || value.isBlank()) {
                continue;
            }
            for (String part : value.split(",")) {
                if (!part.isBlank()) {
                    result.add(part.trim());
                }
            }
        }

        for (String key : new String[]{"Permissions", "permissions", "Perms", "perms"}) {
            for (String value : section.getStringList(key)) {
                if (value != null && !value.isBlank()) {
                    result.add(value.trim());
                }
            }
        }

        return List.copyOf(result);
    }

    private List<String> readStringList(ConfigurationSection section, boolean splitScalar, String... keys) {
        for (String key : keys) {
            if (section.isList(key)) {
                List<String> values = new ArrayList<>();
                for (String value : section.getStringList(key)) {
                    if (value == null || value.isBlank()) {
                        continue;
                    }
                    values.add(value.trim());
                }
                return values;
            }

            String scalar = section.getString(key);
            if (scalar != null && !scalar.isBlank()) {
                if (!splitScalar) {
                    return List.of(scalar.trim());
                }

                List<String> values = new ArrayList<>();
                for (String part : scalar.split(",")) {
                    if (!part.isBlank()) {
                        values.add(part.trim());
                    }
                }
                return values;
            }
        }
        return List.of();
    }

    private String readString(ConfigurationSection section, String def, String... keys) {
        for (String key : keys) {
            if (!section.isSet(key)) {
                continue;
            }
            return section.getString(key, def);
        }
        return def;
    }

    private boolean readBoolean(ConfigurationSection section, boolean def, String... keys) {
        for (String key : keys) {
            if (!section.isSet(key)) {
                continue;
            }
            return section.getBoolean(key, def);
        }
        return def;
    }

    private int readInteger(ConfigurationSection section, int def, String... keys) {
        for (String key : keys) {
            if (!section.isSet(key)) {
                continue;
            }
            return section.getInt(key, def);
        }
        return def;
    }
}
