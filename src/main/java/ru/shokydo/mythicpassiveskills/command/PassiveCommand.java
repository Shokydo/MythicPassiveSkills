package ru.shokydo.mythicpassiveskills.command;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import ru.shokydo.mythicpassiveskills.MythicPassiveSkillsPlugin;
import ru.shokydo.mythicpassiveskills.config.PassiveAction;
import ru.shokydo.mythicpassiveskills.config.PassiveProfile;
import ru.shokydo.mythicpassiveskills.manager.PassiveManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * Команда {@code /passive reload|list|info|player}.
 */
public final class PassiveCommand implements CommandExecutor, TabCompleter {

    private static final String PREFIX = ChatColor.DARK_AQUA + "[Passive] " + ChatColor.RESET;

    private final MythicPassiveSkillsPlugin plugin;

    public PassiveCommand(MythicPassiveSkillsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("mythicpassiveskills.admin")) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Недостаточно прав.");
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender, label);
            return true;
        }

        PassiveManager manager = plugin.getPassiveManager();
        if (manager == null) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Менеджер пассивок ещё не инициализирован.");
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> {
                // Перед перечитыванием профилей повторно регистрируем триггеры:
        // /mm reload мог создать триггеры аддонов, а /passive reload — нет.
        plugin.registerTriggers();
        plugin.reloadPassives();
                sender.sendMessage(PREFIX + ChatColor.GREEN + "Пассивные навыки перезагружены. Профилей: "
                        + manager.getProfiles().size() + ".");
            }
            case "list" -> listProfiles(sender, manager);
            case "info" -> showInfo(sender, manager, args);
            case "player" -> showPlayer(sender, manager, args);
            default -> sendHelp(sender, label);
        }

        return true;
    }

    private void listProfiles(CommandSender sender, PassiveManager manager) {
        Collection<PassiveProfile> profiles = manager.getProfiles();
        sender.sendMessage(PREFIX + "Профили пассивных навыков (" + profiles.size() + "):");

        if (profiles.isEmpty()) {
            sender.sendMessage(ChatColor.GRAY + "  Профили не найдены. Проверьте plugins/MythicMobs/PassiveSkills.");
            return;
        }

        for (PassiveProfile profile : profiles) {
            String state = profile.enabled() ? ChatColor.GREEN + "вкл" : ChatColor.RED + "выкл";
            String triggers = String.join(", ", profile.triggerSummary());
            sender.sendMessage(ChatColor.DARK_GRAY + "  • " + ChatColor.YELLOW + profile.id()
                    + ChatColor.GRAY + " [" + state + ChatColor.GRAY + ", "
                    + triggers + ChatColor.GRAY + "] "
                    + ChatColor.AQUA + profile.permissionDisplay());
        }
    }

    private void showInfo(CommandSender sender, PassiveManager manager, String[] args) {
        if (args.length < 2) {
            if (sender instanceof Player player) {
                showPlayerProfiles(sender, manager, player);
            } else {
                sender.sendMessage(PREFIX + ChatColor.GRAY + "Использование: /passive info <id|ник>");
            }
            return;
        }

        String query = args[1];
        PassiveProfile profile = manager.getProfile(query);
        if (profile != null) {
            showProfile(sender, manager, profile);
            return;
        }

        Player player = Bukkit.getPlayerExact(query);
        if (player != null) {
            showPlayerProfiles(sender, manager, player);
            return;
        }

        sender.sendMessage(PREFIX + ChatColor.RED + "Профиль '" + query + "' не найден.");
    }

    private void showPlayer(CommandSender sender, PassiveManager manager, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(PREFIX + ChatColor.GRAY + "Использование: /passive player <ник>");
            return;
        }

        Player player = Bukkit.getPlayerExact(args[1]);
        if (player == null) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Игрок '" + args[1] + "' не в сети.");
            return;
        }

        showPlayerProfiles(sender, manager, player);
    }

    private void showProfile(CommandSender sender, PassiveManager manager, PassiveProfile profile) {
        sender.sendMessage(PREFIX + "Профиль " + ChatColor.YELLOW + profile.id());
        sender.sendMessage(ChatColor.GRAY + "  Файл: " + ChatColor.WHITE + profile.source());
        sender.sendMessage(ChatColor.GRAY + "  Статус: " + (profile.enabled()
                ? ChatColor.GREEN + "включён" : ChatColor.RED + "выключен"));
        sender.sendMessage(ChatColor.GRAY + "  Права: " + ChatColor.AQUA + profile.permissionDisplay());
        sender.sendMessage(ChatColor.GRAY + "  Действия (" + profile.actions().size() + "):");

        for (PassiveAction action : profile.actions()) {
            sender.sendMessage(ChatColor.DARK_GRAY + "    • " + ChatColor.WHITE + action.line()
                    + ChatColor.GRAY + " [" + action.triggerDisplay() + "]");
        }

        if (!profile.conditionLines().isEmpty()) {
            sender.sendMessage(ChatColor.GRAY + "  Условия профиля: " + ChatColor.WHITE
                    + String.join(" | ", profile.conditionLines()));
        }

        if (sender instanceof Player player && manager.isActive(player, profile.id())) {
            sender.sendMessage(ChatColor.GRAY + "  У вас: " + ChatColor.GREEN + "активен");
        }
    }

    private void showPlayerProfiles(CommandSender sender, PassiveManager manager, Player player) {
        List<PassiveProfile> active = manager.getActiveProfiles(player);
        sender.sendMessage(PREFIX + "Активные пассивки " + ChatColor.YELLOW + player.getName()
                + ChatColor.RESET + " (" + active.size() + "):");

        if (active.isEmpty()) {
            sender.sendMessage(ChatColor.GRAY + "  Нет активных пассивок.");
            return;
        }

        for (PassiveProfile profile : active) {
            sender.sendMessage(ChatColor.DARK_GRAY + "  • " + ChatColor.YELLOW + profile.id()
                    + ChatColor.GRAY + " (" + String.join(", ", profile.triggerSummary()) + ")");
        }
    }

    private void sendHelp(CommandSender sender, String label) {
        sender.sendMessage(PREFIX + "Команды:");
        sender.sendMessage(ChatColor.YELLOW + "  /" + label + " reload" + ChatColor.GRAY + " — перезагрузить профили");
        sender.sendMessage(ChatColor.YELLOW + "  /" + label + " list" + ChatColor.GRAY + " — список профилей");
        sender.sendMessage(ChatColor.YELLOW + "  /" + label + " info <id|ник>" + ChatColor.GRAY + " — информация");
        sender.sendMessage(ChatColor.YELLOW + "  /" + label + " player <ник>" + ChatColor.GRAY + " — активные пассивки игрока");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("mythicpassiveskills.admin")) {
            return List.of();
        }

        if (args.length == 1) {
            return filter(List.of("reload", "list", "info", "player", "help"), args[0]);
        }

        if (args.length == 2) {
            PassiveManager manager = plugin.getPassiveManager();
            if (manager == null) {
                return List.of();
            }

            List<String> values = new ArrayList<>();
            if (args[0].equalsIgnoreCase("info")) {
                for (PassiveProfile profile : manager.getProfiles()) {
                    values.add(profile.id());
                }
            }
            if (args[0].equalsIgnoreCase("info") || args[0].equalsIgnoreCase("player")) {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    values.add(player.getName());
                }
            }
            return filter(values, args[1]);
        }

        return List.of();
    }

    private List<String> filter(Collection<String> values, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String value : values) {
            if (value.toLowerCase(Locale.ROOT).startsWith(lower)) {
                result.add(value);
            }
        }
        return result;
    }
}
