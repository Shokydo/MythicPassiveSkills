package ru.shokydo.mythicpassiveskills.listener;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import ru.shokydo.mythicpassiveskills.MythicPassiveSkillsPlugin;
import ru.shokydo.mythicpassiveskills.manager.PassiveManager;

/**
 * Останавливает задачи пассивок при выходе игрока и выгрузке мира,
 * а также применяет пассивки при входе.
 */
public final class PlayerLifecycleListener implements Listener {

    private final MythicPassiveSkillsPlugin plugin;

    public PlayerLifecycleListener(MythicPassiveSkillsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        // Права игрока могут быть ещё не загружены в момент входа — ждём один тик.
        Bukkit.getScheduler().runTask(plugin, () -> {
            PassiveManager manager = plugin.getPassiveManager();
            if (manager != null && event.getPlayer().isOnline()) {
                manager.refresh(event.getPlayer());
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        PassiveManager manager = plugin.getPassiveManager();
        if (manager != null) {
            manager.clear(event.getPlayer());
        }
        if (plugin.getTriggerSources() != null) {
            plugin.getTriggerSources().clear(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldUnload(WorldUnloadEvent event) {
        PassiveManager manager = plugin.getPassiveManager();
        if (manager != null) {
            manager.clearWorld(event.getWorld());
        }
    }
}
