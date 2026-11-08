package io.github.confidesconch.villagelife.profession.work;

import java.util.Locale;
import java.util.Optional;

/**
 * What the smith makes and what it costs (M13g, Minecraft-free, unit tested). The smith makes better
 * tools for the village's professions: the kind follows from the profession's basic tool (a wooden
 * axe → axes), the material is iron or copper (shears only from iron). Costs as in Minecraft's
 * crafting recipes. He keeps {@link #RESERVE} of each needed tool in storage – workers take one when
 * it is better than theirs, so the smith stops once everybody has one.
 */
public final class SmithRules {
    private SmithRules() {}

    public static final int RESERVE = 2;

    public enum Kind {
        PICKAXE(3, 2), AXE(3, 2), HOE(2, 2), SWORD(2, 1), SHEARS(2, 0);

        private final int ingots;
        private final int sticks;

        Kind(int ingots, int sticks) {
            this.ingots = ingots;
            this.sticks = sticks;
        }

        public int ingots() {
            return ingots;
        }

        public int sticks() {
            return sticks;
        }

        /** Item path of this tool in a material, or empty if it does not exist (copper shears). */
        public Optional<String> itemPath(String material) {
            if (this == SHEARS) {
                return material.equals("iron") ? Optional.of("shears") : Optional.empty();
            }
            return Optional.of(material + "_" + name().toLowerCase(Locale.ROOT));
        }
    }

    /** The kind of tool a profession uses, from the item path of its basic tool (e.g. {@code wooden_axe}). */
    public static Optional<Kind> kindOf(String basicToolPath) {
        String path = basicToolPath.toLowerCase(Locale.ROOT);
        if (path.endsWith("_pickaxe")) {
            return Optional.of(Kind.PICKAXE);
        }
        if (path.endsWith("_axe")) {
            return Optional.of(Kind.AXE);
        }
        if (path.endsWith("_hoe")) {
            return Optional.of(Kind.HOE);
        }
        if (path.endsWith("_sword")) {
            return Optional.of(Kind.SWORD);
        }
        if (path.equals("shears")) {
            return Optional.of(Kind.SHEARS);
        }
        return Optional.empty(); // e.g. a fishing rod or a bowl: nothing to forge
    }

    /** Sticks a stack of planks (2 each, as 2 planks make 4 sticks) or logs (8 each) give. */
    public static int sticksFromPlanks(int planks) {
        return planks * 2;
    }

    public static int sticksFromLogs(int logs) {
        return logs * 8;
    }

    /** The smith repairs tools with less than half of their durability left (M14d). */
    public static boolean needsRepair(int damage, int maxDamage) {
        return maxDamage > 0 && damage * 2 > maxDamage;
    }

    /** Ingots for a full repair: like an anvil, one ingot restores a quarter of the durability. */
    public static int ingotsToRepair(int damage, int maxDamage) {
        int perIngot = Math.max(1, maxDamage / 4);
        return Math.min(4, (damage + perIngot - 1) / perIngot);
    }

    /** Damage left after repairing with {@code ingots}. */
    public static int damageAfterRepair(int damage, int maxDamage, int ingots) {
        return Math.max(0, damage - ingots * Math.max(1, maxDamage / 4));
    }

    /** Ingot material of a tool by its item path ({@code iron_axe} → iron, {@code shears} → iron), or empty. */
    public static Optional<String> materialOf(String toolPath) {
        if (toolPath.equals("shears") || toolPath.startsWith("iron_")) {
            return Optional.of("iron");
        }
        return toolPath.startsWith("copper_") ? Optional.of("copper") : Optional.empty();
    }
}
