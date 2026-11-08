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
 * Loads the market's goods and prices from data packs ({@code data/<namespace>/market_good/*.json},
 * M15a), reloaded with {@code /reload}. Broken files are skipped with a warning.
 */
public final class MarketGoods extends SimpleJsonResourceReloadListener<MarketGood> {

    private static volatile List<MarketGood> current = List.of();

    private MarketGoods() {
        super(MarketGood.CODEC, FileToIdConverter.json("market_good"));
    }

    public static void onAddReloadListeners(AddServerReloadListenersEvent event) {
        event.addListener(Identifier.fromNamespaceAndPath(VillageLife.MOD_ID, "market_goods"), new MarketGoods());
    }

    /** All goods, sorted by item id. */
    public static List<MarketGood> all() {
        return current;
    }

    @Override
    protected void apply(Map<Identifier, MarketGood> loaded, ResourceManager manager, ProfilerFiller profiler) {
        current = loaded.values().stream().sorted(Comparator.comparing(good -> good.item().toString())).toList();
        VillageLife.LOGGER.info("Loaded {} market good(s)", current.size());
    }
}
