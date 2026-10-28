package ru.shokydo.mythicpassiveskills.listener;

import com.destroystokyo.paper.event.player.PlayerArmorChangeEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import ru.shokydo.mythicpassiveskills.MythicPassiveSkillsPlugin;
import ru.shokydo.mythicpassiveskills.manager.PassiveManager;
import ru.shokydo.mythicpassiveskills.trigger.PassiveTrigger;

/**
 * Paper-only: триггеры {@code ~onEquip} и {@code ~onUnequip} для брони.
 * <p>
 * Класс загружается через рефлексию (см. {@code MythicPassiveSkillsPlugin}),
 * чтобы на Spigot не было {@code NoClassDefFoundError}.
 */
public final class PaperArmorListener implements Listener {

    private final MythicPassiveSkillsPlugin plugin;

    public PaperArmorListener(MythicPassiveSkillsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onArmorChange(PlayerArmorChangeEvent event) {
        Player player = event.getPlayer();
        ItemStack oldItem = event.getOldItem();
        ItemStack newItem = event.getNewItem();

        boolean had = oldItem != null && !oldItem.getType().isAir();
        boolean has = newItem != null && !newItem.getType().isAir();

        PassiveManager manager = plugin.getPassiveManager();
        if (manager == null) {
            return;
        }

        if (has) {
            manager.fire(player, PassiveTrigger.EQUIP, null);
        }
        if (had && !has) {
            manager.fire(player, PassiveTrigger.UNEQUIP, null);
        }
    }
}
