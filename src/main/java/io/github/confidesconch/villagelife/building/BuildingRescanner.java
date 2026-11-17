package io.github.confidesconch.villagelife.building;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import io.github.confidesconch.villagelife.core.config.SyncedConfig;
import io.github.confidesconch.villagelife.core.util.DueQueue;
import io.github.confidesconch.villagelife.village.VillageManager;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Keeps registered buildings up to date without lag:
 * <ul>
 *   <li>a player placing or breaking a block next to/inside a building queues it for a rescan
 *       (after a short delay, so many quick changes lead to one scan);</li>
 *   <li>every building is also rescanned periodically ({@code buildingRescanIntervalSeconds}),
 *       which catches explosions, fire, pistons etc.</li>
 * </ul>
 * Scans run at the end of a server tick until the per-tick time budget ({@code tickBudgetMicros}) is used up.
 */
public final class BuildingRescanner {
    private BuildingRescanner() {}

    /** Wait this many ticks after a change before rescanning (0.5 s). */
    private static final long CHANGE_DELAY_TICKS = 10;
    /** Hard cap of scans per tick, in addition to the time budget. */
    private static final int MAX_SCANS_PER_TICK = 8;

    /** After a chunk with buildings comes back in, check them once it has settled (5 s, hotfix 1.5.1). */
    private static final long LOAD_DELAY_TICKS = 100;
    /** A building that suddenly looks broken is checked once more before it counts as invalid (hotfix 1.5.1). */
    static final long CONFIRM_DELAY_TICKS = 100;

    private static final Map<ResourceKey<Level>, DueQueue<BlockPos>> QUEUES = new HashMap<>();
    /** Chunks that were loaded since the last server tick, per dimension. */
    private static final Map<ResourceKey<Level>, Set<Long>> LOADED_CHUNKS = new ConcurrentHashMap<>();

    public static void clear() {
        QUEUES.clear();
        LOADED_CHUNKS.clear();
        BuildingService.clearSuspects();
    }

    /** Hotfix 1.5.1: buildings in a chunk that was just loaded again are rechecked shortly after. */
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level) {
            LOADED_CHUNKS.computeIfAbsent(level.dimension(), key -> ConcurrentHashMap.newKeySet()).add(event.getChunk().getPos().pack());
        }
    }

    /** Checks the building again after {@code delayTicks} (used to confirm that it really became invalid). */
    static void scheduleRecheck(ServerLevel level, BlockPos markerPos, long delayTicks) {
        QUEUES.computeIfAbsent(level.dimension(), key -> new DueQueue<>()).add(markerPos, level.getGameTime() + delayTicks);
    }

    private static void queueReloaded(ServerLevel level) {
        Set<Long> chunks = LOADED_CHUNKS.get(level.dimension());
        if (chunks == null || chunks.isEmpty()) {
            return;
        }
        Set<Long> loaded = new HashSet<>(chunks);
        chunks.removeAll(loaded);
        for (VillageManager.Located located : VillageManager.get(level).allBuildings()) {
            BlockPos marker = located.building().markerPos();
            if (loaded.contains(ChunkPos.pack(marker.getX() >> 4, marker.getZ() >> 4))) {
                scheduleRecheck(level, marker, LOAD_DELAY_TICKS);
            }
        }
    }

    public static void forget(ServerLevel level, BlockPos markerPos) {
        DueQueue<BlockPos> queue = QUEUES.get(level.dimension());
        if (queue != null) {
            queue.remove(markerPos);
        }
    }

    /** Queues every building whose walls/inside contain {@code changed}. */
    public static void onBlockChanged(ServerLevel level, BlockPos changed) {
        long due = level.getGameTime() + CHANGE_DELAY_TICKS;
        for (VillageManager.Located located : VillageManager.get(level).allBuildings()) {
            if (located.building().touches(changed)) {
                QUEUES.computeIfAbsent(level.dimension(), key -> new DueQueue<>()).add(located.building().markerPos(), due);
            }
        }
    }

    public static void onBreak(BreakBlockEvent event) {
        if (event.getLevel() instanceof ServerLevel level) {
            onBlockChanged(level, event.getPos());
        }
    }

    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel() instanceof ServerLevel level) {
            onBlockChanged(level, event.getPos());
        }
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        long deadline = System.nanoTime() + SyncedConfig.TICK_BUDGET_MICROS.getAsInt() * 1000L;
        long intervalTicks = SyncedConfig.BUILDING_RESCAN_INTERVAL_SECONDS.getAsInt() * 20L;
        int scans = 0;
        for (ServerLevel level : event.getServer().getAllLevels()) {
            long now = level.getGameTime();
            queueReloaded(level);
            DueQueue<BlockPos> queue = QUEUES.get(level.dimension());
            // 1) buildings changed by players
            while (queue != null && scans < MAX_SCANS_PER_TICK && System.nanoTime() < deadline) {
                Optional<BlockPos> next = queue.pollDue(now);
                if (next.isEmpty()) {
                    break;
                }
                BuildingService.rescan(level, next.get());
                scans++;
            }
            // 2) periodic check of the building that was scanned longest ago
            while (scans < MAX_SCANS_PER_TICK && System.nanoTime() < deadline) {
                // unloaded buildings are skipped and checked once their area is loaded again
                Optional<VillageManager.Located> stalest = VillageManager.get(level).allBuildings().stream()
                        .filter(l -> now - l.building().scannedGameTime() >= intervalTicks)
                        .filter(l -> level.isLoaded(l.building().markerPos()))
                        .min((a, b) -> Long.compare(a.building().scannedGameTime(), b.building().scannedGameTime()));
                if (stalest.isEmpty()) {
                    break;
                }
                BuildingService.rescan(level, stalest.get().building().markerPos());
                scans++;
            }
        }
    }
}
