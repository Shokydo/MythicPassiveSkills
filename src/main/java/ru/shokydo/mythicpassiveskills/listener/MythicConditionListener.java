package ru.shokydo.mythicpassiveskills.listener;

import io.lumine.mythic.bukkit.events.MythicConditionLoadEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import ru.shokydo.mythicpassiveskills.MythicPassiveSkillsPlugin;
import ru.shokydo.mythicpassiveskills.condition.HotbarSlotCondition;

/**
 * Регистрирует кастомные условия MythicMobs.
 * <p>
 * Сейчас это условия хотбара:
 * <ul>
 *   <li>{@code ?onSlot0}…{@code ?onSlot8} — выбранный слот игрока;</li>
 *   <li>{@code ?inSlot0}…{@code ?inSlot8} — предмет в слоте хотбара;</li>
 *   <li>{@code ?onSlot{slot=0;item=MySword}} — универсальная запись.</li>
 * </ul>
 * MythicMobs создаёт {@code CustomCondition} и присылает событие на следующем
 * тике, поэтому условия успевают зарегистрироваться до первого срабатывания.
 */
public final class MythicConditionListener implements Listener {

    private final MythicPassiveSkillsPlugin plugin;

    public MythicConditionListener(MythicPassiveSkillsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onConditionLoad(MythicConditionLoadEvent event) {
        String name = event.getConditionName();
        HotbarSlotCondition.Spec spec = HotbarSlotCondition.parse(name, event.getConfig());
        if (spec == null) {
            return;
        }
        // Другой аддон уже обработал это имя — не перезаписываем.
        if (event.getCondition().isPresent()) {
            return;
        }

        event.register(new HotbarSlotCondition(
                event.getConfig().getLine(),
                event.getConfig(),
                spec.slot(),
                spec.itemMode()
        ));
        plugin.debug("Зарегистрировано условие хотбара: " + name
                + " (слот " + spec.slot() + (spec.itemMode() ? ", предмет" : "") + ")");
    }
}
