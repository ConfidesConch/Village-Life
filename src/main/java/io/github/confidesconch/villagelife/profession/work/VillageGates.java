package io.github.confidesconch.villagelife.profession.work;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.github.confidesconch.villagelife.village.Village;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * The gates of a village (M20b2): doors and fence gates near the village border that do not belong to a
 * building – in a wall or fence around the village. Guards patrol them often. Found by looking at the ground along the border, at
 * most every five minutes per village; only loaded ground counts.
 */
public final class VillageGates {
    private VillageGates() {}

    /** How far inside the border gates are looked for. */
    private static final int BAND = 14;
    private static final long RESCAN_TICKS = 6000;
    /** Doors closer than this belong to the same gate (double doors). */
    private static final int SAME_GATE = 3;

    private record Found(List<BlockPos> gates, long scannedAt) {}

    private static final Map<UUID, Found> CACHE = new HashMap<>();

    /** Forgets everything kept in memory for the stopped server (bug review 08.11.2026). */
    public static void clear() {
        CACHE.clear();
    }

    public static List<BlockPos> of(ServerLevel level, Village village) {
        Found found = CACHE.get(village.id());
        long now = level.getGameTime();
        if (found == null || now - found.scannedAt() > RESCAN_TICKS) {
            found = new Found(scan(level, village), now);
            CACHE.put(village.id(), found);
        }
        return found.gates();
    }

    /** Doors of registered buildings (houses near the border) are not gates. */
    private static boolean isBuildingDoor(Village village, BlockPos pos) {
        return village.buildings().stream().anyMatch(b -> pos.getX() >= b.min().getX() - 2 && pos.getX() <= b.max().getX() + 2
                && pos.getZ() >= b.min().getZ() - 2 && pos.getZ() <= b.max().getZ() + 2
                && pos.getY() >= b.min().getY() - 2 && pos.getY() <= b.max().getY() + 2);
    }

    /** True if the path leads through a gate of the village – i.e. out of the walls (M20b2). */
    public static boolean leadsThroughGate(ServerLevel level, Village village, net.minecraft.world.level.pathfinder.Path path) {
        List<BlockPos> gates = of(level, village);
        if (path == null || gates.isEmpty()) {
            return false;
        }
        for (int i = 0; i < path.getNodeCount(); i++) {
            BlockPos node = path.getNode(i).asBlockPos();
            for (BlockPos gate : gates) {
                if (node.closerThan(gate, 2.0)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** A point three blocks inside the gate, towards the village center – where a guard stands watch. */
    public static BlockPos watchPoint(BlockPos gate, BlockPos center) {
        double dx = center.getX() - gate.getX();
        double dz = center.getZ() - gate.getZ();
        double length = Math.max(1.0, Math.sqrt(dx * dx + dz * dz));
        return gate.offset((int) Math.round(dx / length * 3), 0, (int) Math.round(dz / length * 3));
    }

    private static List<BlockPos> scan(ServerLevel level, Village village) {
        List<BlockPos> gates = new ArrayList<>();
        BlockPos center = village.center();
        int outer = village.radius();
        int inner = Math.max(0, outer - BAND);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = -outer; dx <= outer; dx++) {
            for (int dz = -outer; dz <= outer; dz++) {
                int distSqr = dx * dx + dz * dz;
                if (distSqr > outer * outer || distSqr < inner * inner) {
                    continue;
                }
                int x = center.getX() + dx;
                int z = center.getZ() + dz;
                pos.set(x, center.getY(), z);
                if (!level.isLoaded(pos)) {
                    continue;
                }
                int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                for (int y = top - 3; y < top; y++) {
                    BlockState state = level.getBlockState(pos.set(x, y, z));
                    if ((state.getBlock() instanceof DoorBlock || state.getBlock() instanceof FenceGateBlock)
                            && !isBuildingDoor(village, pos) && gates.stream().noneMatch(g -> g.closerThan(pos, SAME_GATE))) {
                        gates.add(pos.immutable());
                    }
                }
            }
        }
        return gates;
    }
}
