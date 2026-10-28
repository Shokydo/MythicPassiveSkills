package ru.shokydo.mythicpassiveskills.listener;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import ru.shokydo.mythicpassiveskills.MythicPassiveSkillsPlugin;
import ru.shokydo.mythicpassiveskills.manager.PassiveManager;
import ru.shokydo.mythicpassiveskills.trigger.PassiveTrigger;
import ru.shokydo.mythicpassiveskills.trigger.TriggerSourceCache;

import java.util.HashSet;
import java.util.Set;

/**
 * Слушатели Bukkit-событий, которые конвертируются в кастомные триггеры MythicMobs.
 * <p>
 * Все события фильтруются по игроку — триггеры срабатывают только для пассивок
 * игрока ({@code ~onSneak}, {@code ~onPressF}, {@code ~onLeftClick} и т.д.).
 * <p>
 * <b>Совместимость с MythicCrucible.</b> Скиллы предметов MythicCrucible
 * работают от собственных событий (свап рук → {@code onPressF}/{@code onPress_F},
 * левый клик → {@code onLeftClick}, правый клик → {@code onUse}), поэтому
 * пассивки не должны ни занимать эти события целиком, ни перекрывать их:
 * <ul>
 *   <li>все клики и бой слушаются на {@link EventPriority#MONITOR} — пассивки
 *       никогда не получают событие раньше аддонов и не отменяют чужие события;</li>
 *   <li>событие отменяется, только если оно принадлежит пассивке — тогда
 *       MythicCrucible его не увидит и способность предмета не задвоится;</li>
 *   <li>левый клик приходит двумя событиями (Interact + Animation), поэтому
 *       пассивка запускается один раз за тик.</li>
 * </ul>
 * Без предмета в руках (ванильный, не Mythic и без скиллов) пассивки получают
 * все события как обычно — проверки предмета выполняются только при наличии
 * Mythic-предмета.
 */
public final class PassiveTriggerListener implements Listener {

    private static final String LEFT_CLICK_KEY = "left:";
    private static final String PRIMARY_KEY = "primary:";

    private final MythicPassiveSkillsPlugin plugin;
    private final TriggerSourceCache.PerTick perTick;

    /** Клики, уже обработанные в этом тике: Interact и Animation дублируют друг друга. */
    private final Set<String> seen = new HashSet<>();
    private long seenTick = Long.MIN_VALUE;

    public PassiveTriggerListener(MythicPassiveSkillsPlugin plugin) {
        this.plugin = plugin;
        this.perTick = new TriggerSourceCache.PerTick(plugin.getTriggerSources());
    }

    // ------------------------------------------------------------ Базовые

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSneak(PlayerToggleSneakEvent event) {
        if (!event.isSneaking()) {
            return;
        }
        fire(event.getPlayer(), PassiveTrigger.SNEAK, null);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onUnsneak(PlayerToggleSneakEvent event) {
        if (event.isSneaking()) {
            return;
        }
        fire(event.getPlayer(), PassiveTrigger.UNSNEAK, null);
    }

    /*
     * ~onAttack / ~onDamaged / ~onKill раньше слушались на LOWEST, чтобы опередить
     * аддоны, которые могут отменить урон. Из-за этого скилл MythicCrucible на том
     * же ударе мог не сработать. Теперь мы только наблюдаем событие (MONITOR).
     */

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) {
            return;
        }
        fire(player, PassiveTrigger.ATTACK, event.getEntity());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamaged(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        fire(player, PassiveTrigger.DAMAGED, null);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onKill(EntityDeathEvent event) {
        if (event.getEntity().getKiller() == null) {
            return;
        }
        fire(event.getEntity().getKiller(), PassiveTrigger.KILL, event.getEntity());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        plugin.getTriggerSources().clear(event.getEntity());
        fire(event.getEntity(), PassiveTrigger.DEATH, null);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        plugin.getTriggerSources().clear(event.getPlayer());
        fire(event.getPlayer(), PassiveTrigger.RESPAWN, null);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        fire(event.getPlayer(), PassiveTrigger.BLOCK_BREAK, null);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        fire(event.getPlayer(), PassiveTrigger.BLOCK_PLACE, null);
    }

    // ------------------------------------------------------------ Клики

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInteract(PlayerInteractEvent event) {
        // Слушаем только основную руку, чтобы не дублировать события.
        // ignoreCancelled не ставим: с пустой рукой событие часто cancelled,
        // а пассивка должна срабатывать по клику в любом случае.
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Player player = event.getPlayer();
        Action action = event.getAction();

        if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
            // После первого удара один клик приходит дважды: PlayerInteractEvent
            // и PlayerAnimationEvent. На анимацию MythicCrucible вешает onLeftClick,
            // а мы в пределах одного тика запускаем пассивку только один раз.
            if (!oncePerTick(LEFT_CLICK_KEY + player.getUniqueId())) {
                return;
            }

            if (fireClicks(player)) {
                // Событие принадлежит пассивке — MythicCrucible клик не увидит.
                event.setCancelled(true);
            }
            return;
        }

        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        if (fire(player, PassiveTrigger.RIGHT_CLICK, null) | fire(player, PassiveTrigger.INTERACT, null)) {
            event.setCancelled(true);
            return;
        }

        if (!plugin.getConfig().getBoolean("passive-routing.item-use-click", true)) {
            return;
        }

        // У предмета есть onUse: отменённый правый клик MythicMobs запускает как onUse,
        // иначе (не отменён) тот же клик породил бы второй запуск нажатием ПКМ.
        ItemStack held = heldItem(player);
        if (plugin.getTriggerSources().shouldCancelRightClick(player, held)) {
            event.setCancelled(true);
            event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
            event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
            plugin.debug("Правый клик отменён: onUse предмета " + sourceName(held) + " у " + player.getName());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onOffhandClick(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.OFF_HAND) {
            return;
        }
        Action action = event.getAction();
        if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
            fire(event.getPlayer(), PassiveTrigger.OFFHAND_CLICK, null);
        }
    }

    /**
     * Свап рук — кнопка F. Для MythicCrucible это {@code onPressF} предмета
     * (его слушатель сидит на этом же событии), поэтому:
     * <ul>
     *   <li>если {@code onPressF} объявлен у предмета в руке, свап отдаётся
     *       MythicCrucible нетронутым;</li>
     *   <li>если у игрока есть пассивка на F — свап отменяется и событие
     *       целиком принадлежит пассивке;</li>
     *   <li>если свап никому из них не нужен — работает как обычный свап рук.</li>
     * </ul>
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();

        if (!fire(player, PassiveTrigger.PRESS_F, null)) {
            return; // пассивки на F нет: ванильный свап остаётся ванильным
        }

        if (shouldPreserveSwap(player)) {
            plugin.debug("Свап рук оставлен предмету MythicCrucible у " + player.getName());
            return;
        }

        event.setCancelled(true);
        plugin.debug("Свап рук отменён: F — кнопка пассивки у " + player.getName());
    }

    /**
     * Последний по порядку обработчик: если MythicCrucible после нас снова
     * отменил свап, значит способность предмета всё-таки сработала —
     * возвращаем руки так, как их ждёт плагин.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onSwapHandsLast(PlayerSwapHandItemsEvent event) {
        if (!event.isCancelled()) {
            return;
        }

        if (plugin.getTriggerSources().reapSwapIfConverted(event.getPlayer()) != null) {
            plugin.debug("Свап рук возвращён после onPressF MythicCrucible у "
                    + event.getPlayer().getName());
        }
    }

    /**
     * @return {@code true}, если у предмета в руке объявлен собственный
     *         {@code onPressF}: событие свапа принадлежит MythicCrucible
     */
    private boolean shouldPreserveSwap(Player player) {
        ItemStack main = player.getInventory().getItemInMainHand();
        ItemStack off = player.getInventory().getItemInOffHand();

        String mainId = itemId(main);
        String offId = itemId(off);

        return plugin.getTriggerSources().itemHasTrigger(mainId, "PRESS_F")
                || plugin.getTriggerSources().itemHasTrigger(mainId, "PRESS_F_HAND")
                || plugin.getTriggerSources().itemHasTrigger(offId, "PRESS_F")
                || plugin.getTriggerSources().itemHasTrigger(offId, "PRESS_F_OFFHAND");
    }

    /**
     * Левый клик по воздуху без цели иногда не приходит как
     * {@code PlayerInteractEvent}, но анимация удара приходит всегда.
     * Анимацию не отменяем — на неё MythicCrucible вешает {@code onLeftClick}.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAnimation(PlayerAnimationEvent event) {
        if (event.getAnimationType() != PlayerAnimationType.ARM_SWING) {
            return;
        }

        Player player = event.getPlayer();
        if (!oncePerTick(LEFT_CLICK_KEY + player.getUniqueId())) {
            return;
        }

        fireClicks(player);
    }

    /**
     * Запускает оба «левых» триггера пассивки.
     *
     * @return {@code true}, если хотя бы одна пассивка реально сработала
     */
    private boolean fireClicks(Player player) {
        boolean fired = fire(player, PassiveTrigger.LEFT_CLICK, null);
        if (oncePerTick(PRIMARY_KEY + player.getUniqueId())) {
            fired |= fire(player, PassiveTrigger.PRIMARY_CLICK, null);
        }
        return fired;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDrop(PlayerDropItemEvent event) {
        fire(event.getPlayer(), PassiveTrigger.PRESS_Q, null);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onOpenInventory(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        // Не стреляем при открытии собственного инвентаря (клавиша E).
        if (event.getInventory().getHolder() == player
                || event.getInventory().equals(player.getInventory())) {
            fire(player, PassiveTrigger.PRESS_E, null);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChat(AsyncPlayerChatEvent event) {
        fire(event.getPlayer(), PassiveTrigger.PRESS_T, null);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onHotbarSlot(PlayerItemHeldEvent event) {
        fire(event.getPlayer(), PassiveTrigger.HOTBAR_SLOT, null);
        switch (event.getNewSlot()) {
            case 0 -> fire(event.getPlayer(), PassiveTrigger.PRESS1, null);
            case 1 -> fire(event.getPlayer(), PassiveTrigger.PRESS2, null);
            case 2 -> fire(event.getPlayer(), PassiveTrigger.PRESS3, null);
            case 3 -> fire(event.getPlayer(), PassiveTrigger.PRESS4, null);
            case 4 -> fire(event.getPlayer(), PassiveTrigger.PRESS5, null);
            case 5 -> fire(event.getPlayer(), PassiveTrigger.PRESS6, null);
            case 6 -> fire(event.getPlayer(), PassiveTrigger.PRESS7, null);
            case 7 -> fire(event.getPlayer(), PassiveTrigger.PRESS8, null);
            case 8 -> fire(event.getPlayer(), PassiveTrigger.PRESS9, null);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        fire(player, PassiveTrigger.SHOOT, null);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        fire(event.getPlayer(), PassiveTrigger.CONSUME, null);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        fire(event.getPlayer(), PassiveTrigger.WORLD_CHANGE, null);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        fire(event.getPlayer(), PassiveTrigger.FISH, null);
    }

    // ------------------------------------------------------------ Предметы

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        fire(player, PassiveTrigger.PICKUP, null);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onThrow(ProjectileLaunchEvent event) {
        if (!(event.getEntity().getShooter() instanceof Player player)) {
            return;
        }
        fire(player, PassiveTrigger.THROW, null);
    }

    // ------------------------------------------------------------ Прочие

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSprintStart(PlayerToggleSprintEvent event) {
        if (event.isSprinting()) {
            fire(event.getPlayer(), PassiveTrigger.SPRINT_START, null);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSprintStop(PlayerToggleSprintEvent event) {
        if (!event.isSprinting()) {
            fire(event.getPlayer(), PassiveTrigger.SPRINT_STOP, null);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHeal(EntityRegainHealthEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        fire(player, PassiveTrigger.HEAL, null);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPortal(PlayerPortalEvent event) {
        fire(event.getPlayer(), PassiveTrigger.PORTAL, null);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.END_PORTAL
                || event.getCause() == PlayerTeleportEvent.TeleportCause.NETHER_PORTAL
                || event.getCause() == PlayerTeleportEvent.TeleportCause.END_GATEWAY) {
            return; // порталы обрабатывает отдельный триггер
        }
        fire(event.getPlayer(), PassiveTrigger.TELEPORT, null);
    }

    // ------------------------------------------------------------ Утилиты

    /**
     * Вызывает действия пассивок игрока.
     *
     * @return {@code true}, если хотя бы одно действие реально выполнилось
     */
    private boolean fire(Player player, PassiveTrigger trigger, org.bukkit.entity.Entity context) {
        PassiveManager manager = plugin.getPassiveManager();
        if (manager == null) {
            return false;
        }
        return manager.fire(player, trigger, context, heldItem(player));
    }

    /**
     * Предмет в основной руке. Читается один раз за тик: на один клик Bublit
     * присылает два события, и MythicMobs не должен опрашиваться дважды.
     */
    private ItemStack heldItem(Player player) {
        return perTick.heldItem(player);
    }

    private String itemId(ItemStack item) {
        return plugin.getTriggerSources().mythicItemId(item).orElse(null);
    }

    private String sourceName(ItemStack item) {
        String id = itemId(item);
        return id != null ? id : String.valueOf(item.getType());
    }

    /**
     * @return {@code true}, если ключ впервые встречается в текущем тике
     */
    private boolean oncePerTick(String key) {
        long tick = Bukkit.getCurrentTick();
        if (tick != seenTick) {
            seenTick = tick;
            seen.clear();
        }
        return seen.add(key);
    }
}
