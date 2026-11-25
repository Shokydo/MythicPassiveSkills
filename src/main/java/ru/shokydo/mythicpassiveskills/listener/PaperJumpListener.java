package ru.shokydo.mythicpassiveskills.listener;

import com.destroystokyo.paper.event.player.PlayerJumpEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInputEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import ru.shokydo.mythicpassiveskills.MythicPassiveSkillsPlugin;
import ru.shokydo.mythicpassiveskills.manager.PassiveManager;
import ru.shokydo.mythicpassiveskills.trigger.PassiveTrigger;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Слушатель нажатия пробела (~onJump / ~onPressSpace / ~onPressJump).
 * <p>
 * {@link PlayerJumpEvent} Paper вызывает только когда игрок реально прыгнул
 * от земли — пробел в воздухе до него не доходит. Поэтому триггер ловится
 * тремя путями:
 * <ul>
 *   <li>{@link PlayerJumpEvent} — обычный прыжок от земли;</li>
 *   <li>{@link PlayerInputEvent} — клиент сообщил «клавиша прыжка нажата»,
 *       а игрок при этом не на земле (прыжок/толчок в воздухе);</li>
 *   <li>{@link PlayerToggleFlightEvent} — пробел в воздухе при
 *       {@code allowFlight = true, flying = false}: классическая механика
 *       креативного «двойного прыжка» прилетает с клиента как запрос полёта
 *       (отмена события снаружи, например double-jump-плагином, не мешает).</li>
 * </ul>
 * Все три пути сходятся в {@link #pressSpace(Player)} с короткой защитой от
 * повторного срабатывания в течение одного касания: события могут прийти
 * пачкой в одном тике.
 */
public final class PaperJumpListener implements Listener {

    /** Сколько тиков одно касание пробела игнорирует повторные события. */
    private static final long SAME_PRESS_DEDUPE_TICKS = 4;

    private final MythicPassiveSkillsPlugin plugin;

    /** Последний тик, когда нажатие пробела было засчитано. */
    private final Map<UUID, Long> lastSpacePress = new HashMap<>();

    /** Удерживается ли клавиша прыжка: реагируем только на нажатие, не на удержание. */
    private final Map<UUID, Boolean> jumpHeld = new HashMap<>();

    public PaperJumpListener(MythicPassiveSkillsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onJump(PlayerJumpEvent event) {
        Player player = event.getPlayer();
        PassiveManager manager = plugin.getPassiveManager();
        if (manager != null) {
            manager.fire(player, PassiveTrigger.JUMP, null);
        }
        pressSpace(player);
    }

    /**
     * Запуск полёта по пробелу в воздухе: если пассивка/плагин выдала игроку
     * {@code allowFlight} со снятым {@code flying}, клиент при нажатии пробела
     * в воздухе просит начать полёт — это и есть «прыжок вне земли».
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onToggleFlight(PlayerToggleFlightEvent event) {
        if (!event.isFlying()) {
            return; // выключение полёта — не нажатие пробела
        }
        if (event.getPlayer().isOnGround()) {
            return; // полёт с земли — обычный старт полёта, а не прыжок
        }
        pressSpace(event.getPlayer());
    }

    /**
     * Нажатие клавиши прыжка, сообщённое клиентом. Интересует только нажатие
     * в воздухе: на земле прыжок обслуживает {@link PlayerJumpEvent}.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onInput(PlayerInputEvent event) {
        Player player = event.getPlayer();
        boolean jump = event.getInput().isJump();

        Boolean heldBefore = jumpHeld.put(player.getUniqueId(), jump);
        if (!jump || Boolean.TRUE.equals(heldBefore)) {
            return; // отпускание, удержание или «пустой» пакет ввода
        }
        if (player.isOnGround() || player.isFlying() || player.isInsideVehicle() || player.isDead()) {
            return;
        }
        pressSpace(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        lastSpacePress.remove(uuid);
        jumpHeld.remove(uuid);
    }

    /**
     * Пробел засчитан. За одно касание триггер срабатывает один раз, даже если
     * несколько событий пришли в один тик (клиент может отправить и input, и
     * запрос полёта одновременно).
     */
    private void pressSpace(Player player) {
        long tick = Bukkit.getCurrentTick();
        UUID uuid = player.getUniqueId();

        Long last = lastSpacePress.put(uuid, tick);
        if (last != null && tick - last < SAME_PRESS_DEDUPE_TICKS) {
            plugin.debug("Пробел от " + player.getName() + " пропущен: уже сработал "
                    + (tick - last) + " тик(а) назад.");
            return;
        }

        PassiveManager manager = plugin.getPassiveManager();
        if (manager != null) {
            manager.fire(player, PassiveTrigger.PRESS_SPACE, null);
        }
    }
}
