package ru.shokydo.mythicpassiveskills.listener;

import io.lumine.mythic.bukkit.events.MythicMechanicLoadEvent;
import io.lumine.mythic.core.skills.SkillExecutor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import ru.shokydo.mythicpassiveskills.MythicPassiveSkillsPlugin;
import ru.shokydo.mythicpassiveskills.mechanic.PassiveMechanic;

import java.util.logging.Level;

/**
 * Регистрирует кастомную механику {@code passive{...}} при загрузке скиллов MythicMobs.
 * <p>
 * Слушатель регистрируется в {@code onEnable()}. Неизвестная механика в MythicMobs
 * создаётся лениво: если при первом разборе скилла кастомная механика не найдена,
 * MythicMobs повторяет попытку на следующем тике — к этому моменту слушатель уже активен.
 */
public final class MythicMechanicListener implements Listener {

    private final MythicPassiveSkillsPlugin plugin;

    public MythicMechanicListener(MythicPassiveSkillsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onMechanicLoad(MythicMechanicLoadEvent event) {
        if (!event.getMechanicName().equalsIgnoreCase("passive")) {
            return;
        }

        try {
            SkillExecutor manager = event.getContainer().getManager();
            event.register(new PassiveMechanic(
                    plugin,
                    manager,
                    event.getContainer().getInternalName(),
                    event.getConfig()
            ));
            plugin.debug("Зарегистрирована механика passive.");
        } catch (Throwable throwable) {
            plugin.getLogger().log(Level.SEVERE,
                    "Не удалось зарегистрировать механику passive.", throwable);
        }
    }
}
