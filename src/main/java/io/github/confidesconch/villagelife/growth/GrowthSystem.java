package io.github.confidesconch.villagelife.growth;

import io.github.confidesconch.villagelife.core.util.ModProfiler;
import io.github.confidesconch.villagelife.book.BookSections;

import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Wires up village growth (M16): stage check, stage parcels from data packs and the book section. */
public final class GrowthSystem {
    private GrowthSystem() {}

    public static void init() {
        NeoForge.EVENT_BUS.addListener(StageRewards::onAddReloadListeners);
        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class,
                event -> ModProfiler.time(ModProfiler.VILLAGE_LIFE, () -> GrowthService.onServerTick(event)));
        BookSections.register("growth", GrowthService::write);
    }
}
