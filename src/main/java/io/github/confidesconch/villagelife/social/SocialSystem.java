package io.github.confidesconch.villagelife.social;

import io.github.confidesconch.villagelife.core.util.ModProfiler;
import io.github.confidesconch.villagelife.book.BookSections;
import io.github.confidesconch.villagelife.resident.ai.ResidentHooks;

import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Wires up relationships (M17): talks and moods, meeting friends and the tavern in free time, the book section. */
public final class SocialSystem {
    private SocialSystem() {}

    public static void init() {
        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class,
                event -> ModProfiler.time(ModProfiler.VILLAGE_LIFE, () -> SocialService.onServerTick(event)));
        BookSections.register("relations", SocialService::write);
        // priority 4: before strolling around alone (5) during leisure
        ResidentHooks.addGoalProvider(resident -> resident.goalSelector.addGoal(4, new MeetFriendGoal(resident, 0.6)));
        // M17b: evenings in the tavern come before meeting friends elsewhere
        ResidentHooks.addGoalProvider(resident -> resident.goalSelector.addGoal(3, new TavernVisitGoal(resident, 0.6)));
        // M23: the bard's work (priority 4 like all work goals)
        ResidentHooks.addGoalProvider(resident -> resident.goalSelector.addGoal(4, new BardGoal(resident, 0.6)));
    }
}
