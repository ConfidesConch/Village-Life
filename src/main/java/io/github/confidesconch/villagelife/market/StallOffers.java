package io.github.confidesconch.villagelife.market;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

import io.github.confidesconch.villagelife.VillageLife;

import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;

/**
 * Loads what the daily market trader may sell from data packs ({@code data/<namespace>/stall_offer/*.json},
 * M28c), reloaded with {@code /reload}. Broken files are skipped with a warning; without any offer the
 * trader does not come.
 */
public final class StallOffers extends SimpleJsonResourceReloadListener<StallOffer> {

    private static volatile List<StallOffer> current = List.of();

    private StallOffers() {
        super(StallOffer.CODEC, FileToIdConverter.json("stall_offer"));
    }

    public static void onAddReloadListeners(AddServerReloadListenersEvent event) {
        event.addListener(Identifier.fromNamespaceAndPath(VillageLife.MOD_ID, "stall_offers"), new StallOffers());
    }

    /** All offers, sorted by item id. */
    public static List<StallOffer> all() {
        return current;
    }

    @Override
    protected void apply(Map<Identifier, StallOffer> loaded, ResourceManager manager, ProfilerFiller profiler) {
        current = loaded.values().stream().sorted(Comparator.comparing(offer -> offer.item().toString())).toList();
        VillageLife.LOGGER.info("Loaded {} market stall offer(s)", current.size());
    }
}
