package io.github.confidesconch.villagelife.profession.work;

import java.util.List;

/**
 * Decides what a crafting worker (baker, carpenter …, M14a) does next (Minecraft-free, unit tested):
 * the first recipe whose output the village has fewer of than its target stock – craft it if the
 * inputs are carried, otherwise fetch them; recipes whose inputs cannot be had are skipped.
 */
public final class CraftPlanner {
    private CraftPlanner() {}

    /** One recipe as seen right now. */
    public record State(int stock, int target, boolean hasInputs, boolean canFetchInputs) {
        boolean wanted() {
            return stock < target;
        }
    }

    public enum Action { CRAFT, FETCH, NONE }

    public record Choice(int index, Action action) {
        static final Choice NONE = new Choice(-1, Action.NONE);
    }

    public static Choice choose(List<State> recipes) {
        for (int i = 0; i < recipes.size(); i++) {
            State state = recipes.get(i);
            if (!state.wanted()) {
                continue;
            }
            if (state.hasInputs()) {
                return new Choice(i, Action.CRAFT);
            }
            if (state.canFetchInputs()) {
                return new Choice(i, Action.FETCH);
            }
        }
        return Choice.NONE;
    }

    /** True if something is wanted but nothing that is wanted can be made (shown as a problem). */
    public static boolean missingMaterial(List<State> recipes) {
        boolean anyWanted = false;
        for (State state : recipes) {
            if (state.wanted()) {
                anyWanted = true;
                if (state.hasInputs() || state.canFetchInputs()) {
                    return false;
                }
            }
        }
        return anyWanted;
    }

    /** How many crafts are still needed to reach the target (rounded up), never negative. */
    public static int craftsWanted(int stock, int target, int outputPerCraft) {
        int missing = target - stock;
        return missing <= 0 ? 0 : (missing + outputPerCraft - 1) / Math.max(1, outputPerCraft);
    }
}
