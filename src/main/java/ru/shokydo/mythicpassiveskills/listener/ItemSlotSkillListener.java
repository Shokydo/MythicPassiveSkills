package ru.shokydo.mythicpassiveskills.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.ItemStack;
import ru.shokydo.mythicpassiveskills.MythicPassiveSkillsPlugin;

/**
 * Скиллы предмета MythicCrucible на клавиши хотбара: {@code ~onPressSlot0..8}.
 * <p>
 * MythicCrucible вызывает скиллы предмета только при смене предмета в руке
 * ({@code onHeld}/{@code onUnheld}) и не умеет «нажали клавишу 1». Этот
 * слушатель закрывает разрыв: по {@link PlayerItemHeldEvent} запускает скиллы
 * предмета из нажатого слота и предмета в руке.
 * <p>
 * {@code cancelevent} в таком скилле отменяет само событие: игрок остаётся
 * в прежнем слоте, а способность всё равно выполняется.
 */
public final class ItemSlotSkillListener implements Listener {

    private final MythicPassiveSkillsPlugin plugin;

    public ItemSlotSkillListener(MythicPassiveSkillsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onItemHeld(PlayerItemHeldEvent event) {
        if (!plugin.isItemSlotSkillsEnabled()) {
            return;
        }

        int slot = event.getNewSlot();
        if (slot < 0 || slot > 8) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack pressed = player.getInventory().getItem(slot);
        ItemStack held = player.getInventory().getItem(event.getPreviousSlot());

        boolean cancel = plugin.getItemSkills().fireSlotSkills(player, slot, pressed, held);
        if (cancel) {
            event.setCancelled(true);
            plugin.debug("Смена слота отменена скиллом предмета у " + player.getName()
                    + " (слот " + slot + ")");
        }
    }
}
