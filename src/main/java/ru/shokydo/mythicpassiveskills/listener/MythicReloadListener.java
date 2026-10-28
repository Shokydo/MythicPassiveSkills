package ru.shokydo.mythicpassiveskills.listener;

import io.lumine.mythic.bukkit.events.MythicPostReloadedEvent;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import ru.shokydo.mythicpassiveskills.MythicPassiveSkillsPlugin;
import ru.shokydo.mythicpassiveskills.trigger.PassiveTrigger;

/**
 * Перезагружает профили после {@code /mm reload}.
 * <p>
 * Сами скиллы MythicMobs к этому моменту уже перечитаны, поэтому обновляем профили
 * на следующем тике — так мы видим актуальный список скиллов.
 */
public final class MythicReloadListener implements Listener {

    private final MythicPassiveSkillsPlugin plugin;

    public MythicReloadListener(MythicPassiveSkillsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMythicReload(MythicPostReloadedEvent event) {
        PassiveTrigger.registerAll();
        Bukkit.getScheduler().runTask(plugin, () -> {
            // Предметы (и их триггеры MythicCrucible) могли измениться — перечитываем.
            plugin.refreshTriggerSources();
            plugin.reloadPassives();
        });
    }
}
