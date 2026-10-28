package ru.shokydo.mythicpassiveskills;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.event.EventSubscription;
import net.luckperms.api.event.node.NodeMutateEvent;
import net.luckperms.api.event.user.UserDataRecalculateEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import ru.shokydo.mythicpassiveskills.command.PassiveCommand;
import ru.shokydo.mythicpassiveskills.config.PassiveConfigLoader;
import ru.shokydo.mythicpassiveskills.config.PassiveProfile;
import ru.shokydo.mythicpassiveskills.listener.MythicMechanicListener;
import ru.shokydo.mythicpassiveskills.listener.MythicReloadListener;
import ru.shokydo.mythicpassiveskills.listener.PassiveTriggerListener;
import ru.shokydo.mythicpassiveskills.listener.PlayerLifecycleListener;
import ru.shokydo.mythicpassiveskills.manager.PassiveManager;
import ru.shokydo.mythicpassiveskills.trigger.PassiveTrigger;
import ru.shokydo.mythicpassiveskills.trigger.TriggerSourceCache;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Главный класс плагина MythicPassiveSkills.
 * <p>
 * Пассивные навыки привязываются к permission-нодам и пересчитываются при изменении прав игрока.
 */
public final class MythicPassiveSkillsPlugin extends JavaPlugin {

    private PassiveConfigLoader configLoader;
    private PassiveManager passiveManager;
    private TriggerSourceCache triggerSources;
    private BukkitTask refreshTask;
    private BukkitTask routingRefreshTask;

    private LuckPerms luckPerms;
    private final List<EventSubscription<?>> luckPermsSubscriptions = new ArrayList<>();

    @Override
    public void onLoad() {
        if (!mythicMobsInstalled()) {
            getLogger().severe("MythicMobs не найден. Плагин не сможет работать без него.");
            return;
        }

        // Здесь триггеры НЕ регистрируем. На момент onLoad() MythicCrucible ещё не
        // включён, и через putIfAbsent() мы первыми заняли бы имена вида onUse,
        // onLeftClick и onPress_F — после этого скиллы предметов MythicCrucible
        // переставали срабатывать. Регистрация происходит в onEnable(), уже после
        // включения MythicMobs и MythicCrucible — см. registerTriggers().
    }

    @Override
    public void onEnable() {
        Plugin mythicMobs = getServer().getPluginManager().getPlugin("MythicMobs");
        if (mythicMobs == null || !mythicMobs.isEnabled()) {
            getLogger().severe("MythicMobs не найден или не включён — MythicPassiveSkills отключается.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        saveDefaultConfig();
        reloadConfig();

        passiveManager = new PassiveManager(this);
        triggerSources = passiveManager.getTriggerSources();
        triggerSources.refresh();

        // MythicMobs и MythicCrucible уже включены — их триггеры зарегистрированы,
        // поэтому putIfAbsent() оставит клики предметов им, а мы получим только ещё
        // не занятые имена (PASSIVE_LEFT_CLICK, onSneakPassive и т.д.).
        registerTriggers();
        registerDelayedTriggers();

        configLoader = new PassiveConfigLoader(this);

        List<PassiveProfile> profiles = configLoader.load();
        passiveManager.reload(profiles);

        registerListeners();
        registerCommand();
        setupLuckPerms();
        startRefreshTask();
        startRoutingRefreshTask();

        getLogger().info("MythicPassiveSkills включён. Папка пассивных навыков: "
                + configLoader.getDirectory().getAbsolutePath());
    }

    @Override
    public void onDisable() {
        stopRefreshTask();
        stopRoutingRefreshTask();
        unsubscribeLuckPerms();

        if (passiveManager != null) {
            passiveManager.shutdown();
        }

        luckPerms = null;
    }

    /**
     * Полная перезагрузка конфигурации плагина и файлов пассивных навыков.
     */
    public void reloadPassives() {
        if (passiveManager == null) {
            return;
        }

        reloadConfig();
        triggerSources.refresh();
        configLoader = new PassiveConfigLoader(this);
        passiveManager.reload(configLoader.load());
        startRefreshTask();
        startRoutingRefreshTask();
    }

    /**
     * Регистрирует кастомные триггеры в MythicMobs.
     */
    public void registerTriggers() {
        PassiveTrigger.registerAll();
    }

    /**
     * Повторяет регистрацию триггеров после включения MythicCrucible.
     * <p>
     * Если Crucible ставится через {@code /crucible load} или грузится позже нас,
     * на момент нашего {@code onEnable()} его ещё нет — тогда часть кликов могла
     * остаться за нами. Через несколько секунд перерегистрируемся и перечитываем
     * список триггеров предметов; уже занятые имена при этом не меняются.
     */
    private void registerDelayedTriggers() {
        for (long delay : new long[]{40L, 200L}) {
            Bukkit.getScheduler().runTaskLater(this, this::refreshTriggerSources, delay);
        }
    }

    /**
     * Перечитывает предметы MythicMobs/MythicCrucible (их триггеры).
     * Вызывается после {@code /mm reload}, {@code /passive reload} и по таймеру.
     */
    public void refreshTriggerSources() {
        if (triggerSources != null) {
            triggerSources.refresh();
        }
    }

    private void registerListeners() {
        PluginManager pluginManager = getServer().getPluginManager();
        pluginManager.registerEvents(new MythicMechanicListener(this), this);
        pluginManager.registerEvents(new PlayerLifecycleListener(this), this);
        pluginManager.registerEvents(new PassiveTriggerListener(this), this);
        pluginManager.registerEvents(new MythicReloadListener(this), this);

        registerPaperJumpListener(pluginManager);
        registerPaperArmorListener(pluginManager);
    }

    /**
     * PlayerArmorChangeEvent есть только на Paper. Класс со слушателем загружаем
     * через рефлексию, чтобы на Spigot не было NoClassDefFoundError.
     */
    private void registerPaperArmorListener(PluginManager pluginManager) {
        if (!classExists("com.destroystokyo.paper.event.player.PlayerArmorChangeEvent")) {
            getLogger().info("PlayerArmorChangeEvent недоступен: триггеры \"equip\"/\"unequip\" будут отключены (нужен Paper).");
            return;
        }

        try {
            Listener listener = (Listener) Class
                    .forName("ru.shokydo.mythicpassiveskills.listener.PaperArmorListener")
                    .getDeclaredConstructor(MythicPassiveSkillsPlugin.class)
                    .newInstance(this);
            pluginManager.registerEvents(listener, this);
        } catch (ReflectiveOperationException exception) {
            getLogger().log(Level.WARNING, "Не удалось включить слушатель брони (PaperArmorListener).", exception);
        }
    }

    /**
     * PlayerJumpEvent есть только на Paper. Класс со слушателем загружаем через
     * рефлексию, чтобы на Spigot не было NoClassDefFoundError.
     */
    private void registerPaperJumpListener(PluginManager pluginManager) {
        if (!classExists("com.destroystokyo.paper.event.player.PlayerJumpEvent")) {
            getLogger().info("PlayerJumpEvent недоступен: триггер \"jump\" будет отключён (нужен Paper).");
            return;
        }

        try {
            Listener listener = (Listener) Class
                    .forName("ru.shokydo.mythicpassiveskills.listener.PaperJumpListener")
                    .getDeclaredConstructor(MythicPassiveSkillsPlugin.class)
                    .newInstance(this);
            pluginManager.registerEvents(listener, this);
        } catch (ReflectiveOperationException exception) {
            getLogger().log(Level.WARNING, "Не удалось включить слушатель прыжков (PaperJumpListener).", exception);
        }
    }

    private void registerCommand() {
        var command = getCommand("passive");
        if (command == null) {
            return;
        }

        PassiveCommand executor = new PassiveCommand(this);
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }

    private void startRefreshTask() {
        stopRefreshTask();

        int ticks = getConfig().getInt("settings.permission-refresh-ticks", 40);
        if (ticks <= 0) {
            return;
        }

        refreshTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (passiveManager != null) {
                passiveManager.refreshAll();
            }
        }, ticks, ticks);
    }

    private void stopRefreshTask() {
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }
    }

    private void startRoutingRefreshTask() {
        stopRoutingRefreshTask();

        int ticks = getConfig().getInt("passive-routing.refresh-ticks", 200);
        if (ticks <= 0) {
            return;
        }

        routingRefreshTask = Bukkit.getScheduler().runTaskTimer(this, this::refreshTriggerSources, ticks, ticks);
    }

    private void stopRoutingRefreshTask() {
        if (routingRefreshTask != null) {
            routingRefreshTask.cancel();
            routingRefreshTask = null;
        }
    }

    private void setupLuckPerms() {
        Plugin luckPermsPlugin = getServer().getPluginManager().getPlugin("LuckPerms");
        if (luckPermsPlugin == null || !luckPermsPlugin.isEnabled()) {
            getLogger().info("LuckPerms не найден — используется периодическая проверка прав.");
            return;
        }

        try {
            luckPerms = LuckPermsProvider.get();
        } catch (Throwable throwable) {
            getLogger().warning("Не удалось подключиться к LuckPerms API: " + throwable.getMessage());
            luckPerms = null;
            return;
        }

        luckPermsSubscriptions.add(luckPerms.getEventBus().subscribe(this, UserDataRecalculateEvent.class, event -> {
            UUID uuid = event.getUser().getUniqueId();
            Bukkit.getScheduler().runTask(this, () -> {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null && player.isOnline() && passiveManager != null) {
                    passiveManager.refresh(player);
                }
            });
        }));

        luckPermsSubscriptions.add(luckPerms.getEventBus().subscribe(this, NodeMutateEvent.class, event -> {
            // Изменение прав группы или пользователя: пересчитываем всех онлайн-игроков.
            Bukkit.getScheduler().runTask(this, () -> {
                if (passiveManager != null) {
                    passiveManager.refreshAll();
                }
            });
        }));

        getLogger().info("Интеграция с LuckPerms включена: права обновляются без задержки.");
    }

    private void unsubscribeLuckPerms() {
        for (EventSubscription<?> subscription : luckPermsSubscriptions) {
            try {
                subscription.close();
            } catch (Throwable ignored) {
                // LuckPerms мог уже выгрузиться вместе с сервером.
            }
        }
        luckPermsSubscriptions.clear();
    }

    private boolean mythicMobsInstalled() {
        return getServer().getPluginManager().getPlugin("MythicMobs") != null;
    }

    private boolean classExists(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }

    public PassiveManager getPassiveManager() {
        return passiveManager;
    }

    public PassiveConfigLoader getConfigLoader() {
        return configLoader;
    }

    /**
     * Кэш «кто владеет кликом»: триггеры предметов MythicMobs/MythicCrucible.
     * Никогда не {@code null} после {@code onEnable()}.
     */
    public TriggerSourceCache getTriggerSources() {
        return triggerSources;
    }

    public boolean isDebugEnabled() {
        return getConfig().getBoolean("settings.debug", false);
    }

    public void debug(String message) {
        if (isDebugEnabled()) {
            getLogger().info("[debug] " + message);
        }
    }
}
