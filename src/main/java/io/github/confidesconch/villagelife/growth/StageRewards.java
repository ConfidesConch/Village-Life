package io.github.confidesconch.villagelife.growth;

import java.util.ArrayList;
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
 * Loads the stage parcels from data packs ({@code data/<namespace>/stage_reward/*.json}, M16), reloaded
 * with {@code /reload}. Several files for the same stage add up.
 */
public final class StageRewards extends SimpleJsonResourceReloadListener<StageReward> {

    private static volatile List<StageReward> current = List.of();

    private StageRewards() {
        super(StageReward.CODEC, FileToIdConverter.json("stage_reward"));
    }

    public static void onAddReloadListeners(AddServerReloadListenersEvent event) {
        event.addListener(Identifier.fromNamespaceAndPath(VillageLife.MOD_ID, "stage_rewards"), new StageRewards());
    }

    /** Everything a village gets for reaching {@code stage}. */
    public static List<StageReward.Stack> forStage(int stage) {
        List<StageReward.Stack> items = new ArrayList<>();
        current.stream().filter(reward -> reward.stage() == stage).forEach(reward -> items.addAll(reward.items()));
        return items;
    }

    @Override
    protected void apply(Map<Identifier, StageReward> loaded, ResourceManager manager, ProfilerFiller profiler) {
        current = List.copyOf(loaded.values());
        VillageLife.LOGGER.info("Loaded {} stage reward(s)", current.size());
    }
}
