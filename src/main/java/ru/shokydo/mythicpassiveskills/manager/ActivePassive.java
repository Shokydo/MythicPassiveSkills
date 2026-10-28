package ru.shokydo.mythicpassiveskills.manager;

import org.bukkit.scheduler.BukkitTask;
import ru.shokydo.mythicpassiveskills.config.PassiveProfile;

import java.util.ArrayList;
import java.util.List;

/**
 * Активная пассивка конкретного игрока.
 * <p>
 * У профиля может быть несколько interval-действий с разными периодами,
 * поэтому задач тоже может быть несколько.
 */
final class ActivePassive {

    private final PassiveProfile profile;
    private final boolean mechanic;
    private final List<BukkitTask> tasks = new ArrayList<>();

    ActivePassive(PassiveProfile profile, boolean mechanic) {
        this.profile = profile;
        this.mechanic = mechanic;
    }

    PassiveProfile profile() {
        return profile;
    }

    /**
     * {@code true} — пассивка создана механикой {@code passive{...}},
     * а не загружена из файла конфигурации.
     */
    boolean mechanic() {
        return mechanic;
    }

    void addTask(BukkitTask task) {
        if (task != null) {
            tasks.add(task);
        }
    }

    void cancel() {
        for (BukkitTask task : tasks) {
            task.cancel();
        }
        tasks.clear();
    }
}
