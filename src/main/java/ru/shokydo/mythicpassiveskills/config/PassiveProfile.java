package ru.shokydo.mythicpassiveskills.config;

import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Описание пассивного навыка из {@code plugins/MythicMobs/PassiveSkills/*.yml}.
 * <p>
 * Профиль содержит одно или несколько действий ({@link PassiveAction}). Каждое действие
 * имеет собственный триггер и условия, поэтому в одном профиле можно держать,
 * например, ауру на таймере и срабатывание по приседанию.
 */
public record PassiveProfile(
        String id,
        String source,
        boolean enabled,
        List<String> permissions,
        List<PassiveAction> actions,
        List<String> conditionLines
) {

    public PassiveProfile {
        permissions = List.copyOf(permissions);
        actions = List.copyOf(actions);
        conditionLines = List.copyOf(conditionLines);
    }

    /**
     * Проверяет permission-ноды игрока. Если нод не задано, навык доступен всем.
     * Достаточно любой из перечисленных нод (логика ИЛИ).
     */
    public boolean isGranted(Player player) {
        if (permissions.isEmpty()) {
            return true;
        }

        for (String permission : permissions) {
            if (permission != null && !permission.isBlank() && player.hasPermission(permission)) {
                return true;
            }
        }
        return false;
    }

    public String permissionDisplay() {
        return permissions.isEmpty() ? "<всем>" : String.join(", ", permissions);
    }

    /**
     * Уникальные триггеры профиля в порядке объявления — для {@code /passive list}.
     */
    public List<String> triggerSummary() {
        Set<String> result = new LinkedHashSet<>();
        for (PassiveAction action : actions) {
            result.add(action.triggerDisplay());
        }
        return new ArrayList<>(result);
    }

    public boolean hasTrigger(ru.shokydo.mythicpassiveskills.trigger.PassiveTrigger trigger) {
        for (PassiveAction action : actions) {
            if (action.trigger() == trigger) {
                return true;
            }
        }
        return false;
    }
}
