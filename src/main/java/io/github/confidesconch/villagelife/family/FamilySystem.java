package io.github.confidesconch.villagelife.family;

import io.github.confidesconch.villagelife.core.util.ModProfiler;
import io.github.confidesconch.villagelife.resident.ai.ResidentHooks;

import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Wires up families (M18b/c): births and growing up, children playing and going to school. */
public final class FamilySystem {
    private FamilySystem() {}

    public static void init() {
        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class,
                event -> ModProfiler.time(ModProfiler.VILLAGE_LIFE, () -> FamilyService.onServerTick(event)));
        // priority 4, like meeting friends: before strolling around alone (5)
        ResidentHooks.addGoalProvider(resident -> resident.goalSelector.addGoal(4, new PlayGoal(resident, 0.8)));
        // M18c: school comes before playing
        ResidentHooks.addGoalProvider(resident -> resident.goalSelector.addGoal(3, new SchoolGoal(resident, 0.6)));
    }
}
