package io.github.confidesconch.villagelife.village;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import io.github.confidesconch.villagelife.VillageLife;
import io.github.confidesconch.villagelife.core.config.SyncedConfig;

import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import net.neoforged.neoforge.common.world.chunk.TicketController;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Optionally keeps every village loaded while the server runs (hotfix 1.5.1, owner's choice "as a
 * setting", {@code keepVillagesLoaded}, off by default): the chunks within the village border get a
 * ticking ticket, so residents keep working and fields keep growing when nobody is near. Costs server
 * performance per village. The tickets are not kept over a restart (removed when the world loads and set
 * again while the setting is on), so turning the setting off frees the chunks at the next update.
 */
public final class VillageChunkLoader {
    private VillageChunkLoader() {}

    private static final int UPDATE_INTERVAL_TICKS = 200;

    /** Our tickets are only valid while the server runs: drop the stored ones when a world loads. */
    private static final TicketController CONTROLLER = new TicketController(
            Identifier.fromNamespaceAndPath(VillageLife.MOD_ID, "village"),
            (level, helper) -> List.copyOf(helper.getEntityTickets().keySet()).forEach(helper::removeAllTickets));

    /** Chunks we hold per dimension and village. */
    private static final Map<ResourceKey<Level>, Map<UUID, Set<Long>>> FORCED = new HashMap<>();

    /** For {@code /villagelife debug perf}: chunks held loaded right now, over all dimensions. */
    public static int heldChunks() {
        return FORCED.values().stream().flatMap(map -> map.values().stream()).mapToInt(Set::size).sum();
    }

    /** For {@code /villagelife debug perf}: villages held loaded right now. */
    public static int heldVillages() {
        return (int) FORCED.values().stream().flatMap(map -> map.values().stream()).filter(set -> !set.isEmpty()).count();
    }

    public static void onRegisterTicketControllers(RegisterTicketControllersEvent event) {
        event.register(CONTROLLER);
    }

    public static void clear() {
        FORCED.clear();
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % UPDATE_INTERVAL_TICKS != 0) {
            return;
        }
        boolean on = SyncedConfig.KEEP_VILLAGES_LOADED.getAsBoolean();
        for (ServerLevel level : event.getServer().getAllLevels()) {
            Map<UUID, Set<Long>> held = FORCED.computeIfAbsent(level.dimension(), key -> new HashMap<>());
            Map<UUID, Set<Long>> wanted = new HashMap<>();
            if (on) {
                for (Village village : VillageManager.get(level).all()) {
                    wanted.put(village.id(), chunksOf(village));
                }
            }
            for (Map.Entry<UUID, Set<Long>> entry : held.entrySet()) { // release what is no longer wanted
                Set<Long> keep = wanted.getOrDefault(entry.getKey(), Set.of());
                for (long chunk : entry.getValue()) {
                    if (!keep.contains(chunk)) {
                        force(level, entry.getKey(), chunk, false);
                    }
                }
            }
            for (Map.Entry<UUID, Set<Long>> entry : wanted.entrySet()) { // and hold the new ones
                Set<Long> had = held.getOrDefault(entry.getKey(), Set.of());
                for (long chunk : entry.getValue()) {
                    if (!had.contains(chunk)) {
                        force(level, entry.getKey(), chunk, true);
                    }
                }
            }
            held.clear();
            held.putAll(wanted);
        }
    }

    /** The chunks covering the village border (a square around the center). */
    private static Set<Long> chunksOf(Village village) {
        Set<Long> chunks = new HashSet<>();
        int minX = (village.center().getX() - village.radius()) >> 4;
        int maxX = (village.center().getX() + village.radius()) >> 4;
        int minZ = (village.center().getZ() - village.radius()) >> 4;
        int maxZ = (village.center().getZ() + village.radius()) >> 4;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                chunks.add(ChunkPos.pack(x, z));
            }
        }
        return chunks;
    }

    private static void force(ServerLevel level, UUID village, long chunk, boolean add) {
        CONTROLLER.forceChunk(level, village, ChunkPos.getX(chunk), ChunkPos.getZ(chunk), add, true);
    }
}
