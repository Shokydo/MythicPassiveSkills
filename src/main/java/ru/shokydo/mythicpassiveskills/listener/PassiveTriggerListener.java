package ru.shokydo.mythicpassiveskills.listener;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
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
import ru.shokydo.mythicpassiveskills.manager.PassiveManager.FireResult;
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
        fireCancellable(event.getPlayer(), PassiveTrigger.SNEAK, null, event);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onUnsneak(PlayerToggleSneakEvent event) {
        if (event.isSneaking()) {
            return;
        }
        fireCancellable(event.getPlayer(), PassiveTrigger.UNSNEAK, null, event);
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
        fireCancellable(player, PassiveTrigger.ATTACK, event.getEntity(), event);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamaged(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        fireCancellable(player, PassiveTrigger.DAMAGED, null, event);
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
        fireCancellable(event.getPlayer(), PassiveTrigger.BLOCK_BREAK, null, event);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        fireCancellable(event.getPlayer(), PassiveTrigger.BLOCK_PLACE, null, event);
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

            FireResult clicks = fireClicks(player);
            if (clicks.fired()) {
                // Событие принадлежит пассивке — MythicCrucible клик не увидит.
                event.setCancelled(true);
            }
            return;
        }

        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        FireResult rightClick = fireResult(player, PassiveTrigger.RIGHT_CLICK, null);
        FireResult interact = fireResult(player, PassiveTrigger.INTERACT, null);
        if (rightClick.fired() || interact.fired()) {
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

        FireResult result = fireResult(player, PassiveTrigger.PRESS_F, null);
        if (!result.fired()) {
            return; // пассивки на F нет: ванильный свап остаётся ванильным
        }

        // Явный cancelevent сильнее совместимости с предметом:
        // пользователь попросил отменить событие — отменяем.
        if (result.cancel()) {
            event.setCancelled(true);
            plugin.debug("Свап рук отменён строкой cancelevent у " + player.getName());
            return;
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
     * @return результат: выполненный скилл и/или запрос {@code cancelevent}
     */
    private FireResult fireClicks(Player player) {
        FireResult left = fireResult(player, PassiveTrigger.LEFT_CLICK, null);
        if (!oncePerTick(PRIMARY_KEY + player.getUniqueId())) {
            return left;
        }

        FireResult primary = fireResult(player, PassiveTrigger.PRIMARY_CLICK, null);
        return new FireResult(left.cast() || primary.cast(), left.cancel() || primary.cancel());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDrop(PlayerDropItemEvent event) {
        fireCancellable(event.getPlayer(), PassiveTrigger.PRESS_Q, null, event);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onOpenInventory(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        // Не стреляем при открытии собственного инвентаря (клавиша E).
        if (event.getInventory().getHolder() == player
                || event.getInventory().equals(player.getInventory())) {
            fireCancellable(player, PassiveTrigger.PRESS_E, null, event);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChat(AsyncPlayerChatEvent event) {
        fire(event.getPlayer(), PassiveTrigger.PRESS_T, null);
    }

    /**
     * Смена слота хотбара. Триггеры {@code ~onPressSlot0..8} — это те же
     * {@link PassiveTrigger#PRESS1}…{@link PassiveTrigger#PRESS9}, но с
     * нумерацией слота (клавиша 1 = слот 0).
     * <p>
     * Строка {@code cancelevent ~onPressSlot0} отменяет само событие:
     * игрок остаётся в прежнем слоте, а скиллы с этим триггером всё равно
     * выполняются.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onHotbarSlot(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        boolean cancel = fireResult(player, PassiveTrigger.HOTBAR_SLOT, null).cancel();
        cancel |= switch (event.getNewSlot()) {
            case 0 -> fireResult(player, PassiveTrigger.PRESS1, null).cancel();
            case 1 -> fireResult(player, PassiveTrigger.PRESS2, null).cancel();
            case 2 -> fireResult(player, PassiveTrigger.PRESS3, null).cancel();
            case 3 -> fireResult(player, PassiveTrigger.PRESS4, null).cancel();
            case 4 -> fireResult(player, PassiveTrigger.PRESS5, null).cancel();
            case 5 -> fireResult(player, PassiveTrigger.PRESS6, null).cancel();
            case 6 -> fireResult(player, PassiveTrigger.PRESS7, null).cancel();
            case 7 -> fireResult(player, PassiveTrigger.PRESS8, null).cancel();
            case 8 -> fireResult(player, PassiveTrigger.PRESS9, null).cancel();
            default -> false;
        };

        if (cancel) {
            event.setCancelled(true);
            plugin.debug("Смена слота отменена (cancelevent) у " + player.getName());

            // Paper сам возвращает клиенту прежний слот при отмене
            // PlayerItemHeldEvent (ClientboundSetHeldSlotPacket), поэтому
            // дополнительная синхронизация не нужна.
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        fireCancellable(player, PassiveTrigger.SHOOT, null, event);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        fireCancellable(event.getPlayer(), PassiveTrigger.CONSUME, null, event);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        fire(event.getPlayer(), PassiveTrigger.WORLD_CHANGE, null);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        fireCancellable(event.getPlayer(), PassiveTrigger.FISH, null, event);
    }

    // ------------------------------------------------------------ Предметы

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        fireCancellable(player, PassiveTrigger.PICKUP, null, event);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onThrow(ProjectileLaunchEvent event) {
        if (!(event.getEntity().getShooter() instanceof Player player)) {
            return;
        }
        fireCancellable(player, PassiveTrigger.THROW, null, event);
    }

    // ------------------------------------------------------------ Прочие

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSprintStart(PlayerToggleSprintEvent event) {
        if (event.isSprinting()) {
            fireCancellable(event.getPlayer(), PassiveTrigger.SPRINT_START, null, event);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSprintStop(PlayerToggleSprintEvent event) {
        if (!event.isSprinting()) {
            fireCancellable(event.getPlayer(), PassiveTrigger.SPRINT_STOP, null, event);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHeal(EntityRegainHealthEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        fireCancellable(player, PassiveTrigger.HEAL, null, event);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPortal(PlayerPortalEvent event) {
        fireCancellable(event.getPlayer(), PassiveTrigger.PORTAL, null, event);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.END_PORTAL
                || event.getCause() == PlayerTeleportEvent.TeleportCause.NETHER_PORTAL
                || event.getCause() == PlayerTeleportEvent.TeleportCause.END_GATEWAY) {
            return; // порталы обрабатывает отдельный триггер
        }
        fireCancellable(event.getPlayer(), PassiveTrigger.TELEPORT, null, event);
    }

    // ------------------------------------------------------------ Утилиты

    /**
     * Вызывает действия пассивок игрока.
     *
     * @return {@code true}, если хотя бы одно действие реально выполнилось
     */
    private boolean fire(Player player, PassiveTrigger trigger, org.bukkit.entity.Entity context) {
        return fireResult(player, trigger, context).cast();
    }

    /**
     * Вызывает действия пассивок и возвращает полный результат,
     * включая запрос {@code cancelevent}.
     */
    private FireResult fireResult(Player player, PassiveTrigger trigger,
                                  org.bukkit.entity.Entity context) {
        PassiveManager manager = plugin.getPassiveManager();
        if (manager == null) {
            return FireResult.NONE;
        }
        return manager.fireResult(player, trigger, context, heldItem(player));
    }

    /**
     * Запускает пассивку и отменяет событие, если сработала строка
     * {@code cancelevent} с этим триггером.
     */
    private FireResult fireCancellable(Player player, PassiveTrigger trigger,
                                       org.bukkit.entity.Entity context, Cancellable event) {
        FireResult result = fireResult(player, trigger, context);
        if (result.cancel()) {
            event.setCancelled(true);
            plugin.debug("Событие " + trigger.name() + " отменено (cancelevent) у " + player.getName());
        }
        return result;
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
