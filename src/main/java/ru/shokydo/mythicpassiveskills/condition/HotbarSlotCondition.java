package ru.shokydo.mythicpassiveskills.condition;

import io.lumine.mythic.api.adapters.AbstractEntity;
import io.lumine.mythic.api.config.MythicLineConfig;
import io.lumine.mythic.api.skills.conditions.IEntityCondition;
import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.core.skills.SkillCondition;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;

/**
 * Условие хотбара: {@code ?onSlot0}…{@code ?onSlot8} (выбранный слот)
 * и {@code ?inSlot0}…{@code ?inSlot8} (предмет в слоте).
 * <p>
 * Примеры:
 * <pre>
 * ?onSlot0                                  — игрок сейчас в слоте 0 (клавиша 1)
 * ?onSlot0{item=MySword}                    — в слоте 0 лежит Mythic-предмет MySword
 * ?inSlot4                                  — в слоте 4 есть любой предмет
 * ?inSlot4{item=DIAMOND_SWORD}              — в слоте 4 лежит алмазный меч
 * ?onSlot{slot=2}                           — универсальная запись через параметр
 * ?onSlot2{selected=true;item=MySword}      — слот 2 выбран и содержит MySword
 * </pre>
 * <p>
 * Названия условий регистрируются через {@code MythicConditionLoadEvent},
 * поэтому работают и в строках пассивок, и в обычных скиллах MythicMobs.
 */
public final class HotbarSlotCondition extends SkillCondition implements IEntityCondition {

    private final int slot;
    private final boolean itemMode;
    private final boolean requireSelected;
    private final String itemFilter;

    public HotbarSlotCondition(String line, MythicLineConfig config, int slot, boolean itemMode) {
        super(line);
        this.slot = slot;
        this.itemMode = itemMode;
        this.itemFilter = trimToNull(config.getString(new String[]{
                "item", "i", "mythicitem", "mythic", "type", "t", "material", "m", "id", "name"
        }, null));
        this.requireSelected = config.getBoolean(new String[]{"selected", "held"}, false);
    }

    @Override
    public boolean check(AbstractEntity entity) {
        try {
            if (!(entity.getBukkitEntity() instanceof Player player)) {
                return false;
            }
            if (slot < 0 || slot > 8) {
                return false;
            }

            if (!itemMode) {
                // ?onSlotN — выбранный слот. Если указан item=..., дополнительно
                // проверяем предмет именно в этом слоте.
                if (player.getInventory().getHeldItemSlot() != slot) {
                    return false;
                }
                return itemFilter == null || matches(player.getInventory().getItem(slot));
            }

            if (requireSelected && player.getInventory().getHeldItemSlot() != slot) {
                return false;
            }

            return matches(player.getInventory().getItem(slot));
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean matches(ItemStack stack) {
        boolean empty = stack == null || stack.getType().isAir();

        if (itemFilter == null) {
            return !empty;
        }

        String filter = itemFilter.trim();
        String lower = filter.toLowerCase(Locale.ROOT);
        if (lower.equals("any") || lower.equals("*")) {
            return !empty;
        }
        if (lower.equals("empty") || lower.equals("air") || lower.equals("none")) {
            return empty;
        }
        if (empty) {
            return false;
        }

        String materialFilter = filter;
        boolean onlyMythic = false;
        boolean onlyMaterial = false;

        int colon = filter.indexOf(':');
        if (colon > 0) {
            String prefix = filter.substring(0, colon).toLowerCase(Locale.ROOT);
            if (prefix.equals("mythic") || prefix.equals("mm")) {
                onlyMythic = true;
                materialFilter = filter.substring(colon + 1);
            } else if (prefix.equals("material") || prefix.equals("vanilla")
                    || prefix.equals("bukkit") || prefix.equals("minecraft")) {
                onlyMaterial = true;
                materialFilter = filter.substring(colon + 1);
            }
        }

        if (!onlyMaterial) {
            String mythicId = mythicItemId(stack);
            if (mythicId != null && mythicId.equalsIgnoreCase(materialFilter)) {
                return true;
            }
        }

        if (!onlyMythic) {
            String materialName = materialFilter;
            int namespace = materialName.indexOf(':');
            if (namespace >= 0) {
                materialName = materialName.substring(namespace + 1);
            }
            materialName = materialName.toUpperCase(Locale.ROOT).replace(' ', '_');
            Material type = stack.getType();
            return type.name().equals(materialName);
        }

        return false;
    }

    private static String mythicItemId(ItemStack stack) {
        try {
            var itemManager = MythicBukkit.inst().getItemManager();
            if (itemManager == null) {
                return null;
            }
            return itemManager.getMythicTypeFromItem(stack);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * Разбирает имя условия.
     *
     * @return слот и режим проверки предмета или {@code null}, если имя
     *         не относится к хотбару
     */
    public static Spec parse(String rawName, MythicLineConfig config) {
        if (rawName == null || rawName.isBlank()) {
            return null;
        }

        String name = rawName.trim().toLowerCase(Locale.ROOT)
                .replace("_", "")
                .replace("-", "")
                .replace(" ", "");

        boolean itemMode = false;
        String prefix;
        if (name.startsWith("iteminslot")) {
            itemMode = true;
            prefix = "iteminslot";
        } else if (name.startsWith("itemonslot")) {
            itemMode = true;
            prefix = "itemonslot";
        } else if (name.startsWith("inslot")) {
            itemMode = true;
            prefix = "inslot";
        } else if (name.startsWith("itemslot")) {
            itemMode = true;
            prefix = "itemslot";
        } else if (name.startsWith("onhotbarslot")) {
            prefix = "onhotbarslot";
        } else if (name.startsWith("selectedslot")) {
            prefix = "selectedslot";
        } else if (name.startsWith("hotbarslot")) {
            prefix = "hotbarslot";
        } else if (name.startsWith("onslot")) {
            prefix = "onslot";
        } else {
            return null;
        }

        String suffix = name.substring(prefix.length());
        int slot = parseSlot(suffix);
        if (slot < 0) {
            slot = config.getInteger(new String[]{"slot", "s", "n"}, -1);
        }
        if (slot < 0 || slot > 8) {
            return null;
        }

        return new Spec(slot, itemMode);
    }

    private static int parseSlot(String value) {
        if (value == null || value.isEmpty()) {
            return -1;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    /**
     * @param slot     номер слота 0…8
     * @param itemMode {@code true} — проверять предмет в слоте,
     *                 {@code false} — выбранный слот игрока
     */
    public record Spec(int slot, boolean itemMode) {
    }
}
