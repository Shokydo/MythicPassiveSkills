package ru.shokydo.mythicpassiveskills.listener;

import com.destroystokyo.paper.event.player.PlayerJumpEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import ru.shokydo.mythicpassiveskills.MythicPassiveSkillsPlugin;
import ru.shokydo.mythicpassiveskills.manager.PassiveManager;
import ru.shokydo.mythicpassiveskills.trigger.PassiveTrigger;

/**
 * Отдельный слушатель для Paper-события прыжка.
 * <p>
 * Класс регистрируется только если {@code PlayerJumpEvent} присутствует на сервере,
 * поэтому плагин остаётся совместимым со Spigot (без триггера jump).
 */
public final class PaperJumpListener implements Listener {

    private final MythicPassiveSkillsPlugin plugin;

    public PaperJumpListener(MythicPassiveSkillsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onJump(PlayerJumpEvent event) {
        PassiveManager manager = plugin.getPassiveManager();
        if (manager != null) {
            manager.fire(event.getPlayer(), PassiveTrigger.JUMP, null);
            manager.fire(event.getPlayer(), PassiveTrigger.PRESS_SPACE, null);
        }
    }
}
