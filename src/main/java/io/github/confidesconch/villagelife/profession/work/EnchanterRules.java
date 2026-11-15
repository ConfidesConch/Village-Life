package io.github.confidesconch.villagelife.profession.work;

/**
 * Pure rules of the enchanter (M26, owner's choice "bookshelves, useful enchantments, players pay emeralds"):
 * the bookshelves around his enchanting table decide how strong he enchants; nothing from the storage is
 * used up. Village tools get efficiency, then unbreaking; players pay emeralds for a random enchantment.
 */
public final class EnchanterRules {
    private EnchanterRules() {}

    /** What he does next to a village tool. */
    public enum Step { NONE, EFFICIENCY, UNBREAKING }

    /** Time one enchantment takes (at normal work speed). */
    public static final int ENCHANT_TICKS = 200;
    /** Most bookshelves that count (like vanilla). */
    public static final int MAX_SHELVES = 15;

    /** Strength 1–3 from the bookshelves around the table: up to 4 → I, 5–11 → II, 12 or more → III. */
    public static int tier(int shelves) {
        return shelves >= 12 ? 3 : shelves >= 5 ? 2 : 1;
    }

    /** Experience level of a player's enchantment at this strength (like the bottom slot of a table). */
    public static int costLevel(int tier) {
        return switch (tier) {
            case 3 -> 30;
            case 2 -> 20;
            default -> 10;
        };
    }

    /** Emeralds a player pays (they go into the village storage). */
    public static int price(int tier) {
        return tier * 4;
    }

    /**
     * Next enchantment for a village tool: efficiency up to the strength (only tools that dig or cut),
     * then unbreaking up to the strength; NONE when both are there.
     */
    public static Step next(int efficiency, int unbreaking, int tier, boolean digs) {
        if (digs && efficiency < tier) {
            return Step.EFFICIENCY;
        }
        return unbreaking < tier ? Step.UNBREAKING : Step.NONE;
    }
}
