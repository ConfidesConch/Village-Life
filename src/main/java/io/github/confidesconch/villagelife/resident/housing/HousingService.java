package io.github.confidesconch.villagelife.resident.housing;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

import io.github.confidesconch.villagelife.VillageLife;
import io.github.confidesconch.villagelife.building.Building;
import io.github.confidesconch.villagelife.building.BuildingType;
import io.github.confidesconch.villagelife.core.config.SyncedConfig;
import io.github.confidesconch.villagelife.resident.ResidentRecord;
import io.github.confidesconch.villagelife.resident.ResidentService;
import io.github.confidesconch.villagelife.resident.housing.HousingAllocator.Bed;
import io.github.confidesconch.villagelife.resident.needs.NeedsRules;
import io.github.confidesconch.villagelife.village.Village;
import io.github.confidesconch.villagelife.village.VillageManager;
import io.github.confidesconch.villagelife.village.VillageStages;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Keeps homes up to date and lets new residents move in:
 * every {@link #UPDATE_INTERVAL_TICKS} each village reassigns beds (keeping existing ones) and,
 * if beds are free and the immigration interval has passed, a newcomer arrives at the village edge.
 */
public final class HousingService {
    private HousingService() {}

    /** How often (in ticks) every village updates its housing: 10 seconds. */
    static final int UPDATE_INTERVAL_TICKS = 200;

    private static final Random RANDOM = new Random();

    public static void onServerTick(ServerTickEvent.Post event) {
        for (ServerLevel level : event.getServer().getAllLevels()) {
            long now = level.getGameTime();
            VillageManager manager = VillageManager.get(level);
            for (Village village : List.copyOf(manager.all())) {
                // spread the villages over the interval instead of updating all in the same tick
                if (Math.floorMod(now + village.id().hashCode(), UPDATE_INTERVAL_TICKS) == 0) {
                    update(level, village.id(), false);
                }
            }
        }
    }

    /** Free and total beds of a village (for info displays). */
    public static int[] bedCounts(Village village) {
        int total = beds(village).size();
        long used = village.residents().stream().filter(r -> r.bed().isPresent()).count();
        return new int[]{(int) Math.max(0, total - used), total};
    }

    /**
     * Reassigns beds and checks immigration.
     *
     * @param forceImmigration ignore the immigration interval (debug command)
     * @return true if a new resident arrived
     */
    public static boolean update(ServerLevel level, UUID villageId, boolean forceImmigration) {
        VillageManager manager = VillageManager.get(level);
        Optional<Village> found = manager.byId(villageId);
        if (found.isEmpty()) {
            return false;
        }
        Village village = assignBeds(found.get());
        manager.update(village);

        int free = bedCounts(village)[0];
        long now = level.getGameTime();
        // happy villages grow twice as fast, unhappy ones not at all (M10)
        int interval = ResidentService.averageSatisfaction(level, village)
                .stream().map(avg -> NeedsRules.immigrationInterval(SyncedConfig.IMMIGRATION_INTERVAL_TICKS.getAsInt(), avg))
                .findFirst().orElse(SyncedConfig.IMMIGRATION_INTERVAL_TICKS.getAsInt());
        boolean due = forceImmigration
                || (interval > 0 && now - village.lastImmigrationGameTime() >= interval);
        boolean allowed = forceImmigration || village.settings().immigration(); // owner can switch it off (M11c)
        if (free <= 0 || !due || !allowed || !level.isLoaded(village.center())) {
            return false;
        }
        // the village stage (M16) limits the residents, at most the server's limit
        if (village.residents().size() >= Math.min(SyncedConfig.MAX_RESIDENTS_PER_VILLAGE.getAsInt(),
                VillageStages.maxResidents(village.stage()))) {
            return false;
        }
        manager.update(village.withLastImmigration(now));
        ResidentService.SpawnResult result = ResidentService.spawn(level, arrivalPoint(level, village),
                manager.byId(villageId).orElseThrow());
        if (result instanceof ResidentService.SpawnResult.Spawned spawned) {
            // give the newcomer a bed right away
            manager.byId(villageId).ifPresent(v -> manager.update(assignBeds(v)));
            io.github.confidesconch.villagelife.village.EventLog.record(level.getServer(), village, "residents",
                    Component.translatable("villagelife.event.arrived", spawned.resident().getFullName())); // M21b3
            ServerPlayer owner = level.getServer().getPlayerList().getPlayer(village.owner());
            if (owner != null) {
                owner.sendSystemMessage(Component.translatable("villagelife.resident.arrived",
                        spawned.resident().getFullName(), village.displayName()).withStyle(ChatFormatting.GREEN));
            }
            return true;
        }
        return false;
    }

    /** Applies {@link HousingAllocator} to the village's residents (oldest residents first). */
    static Village assignBeds(Village village) {
        List<Bed> beds = beds(village);
        Map<UUID, Bed> current = new HashMap<>();
        for (ResidentRecord r : village.residents()) {
            if (r.home().isPresent() && r.bed().isPresent()) {
                BlockPos p = r.bed().get();
                current.put(r.id(), new Bed(r.home().get(), p.getX(), p.getY(), p.getZ()));
            }
        }
        List<UUID> order = village.residents().stream().map(ResidentRecord::id).toList();
        Map<UUID, UUID> partners = new HashMap<>(); // M18: couples live together
        for (ResidentRecord r : village.residents()) {
            r.partner().ifPresent(partner -> partners.put(r.id(), partner));
        }
        HousingAllocator.Result result = HousingAllocator.allocate(beds, order, current, partners);

        List<ResidentRecord> updated = new ArrayList<>();
        for (ResidentRecord r : village.residents()) {
            Optional<Bed> bed = result.bedOf(r.id());
            updated.add(r.withHome(bed.map(Bed::buildingId), bed.map(b -> new BlockPos(b.x(), b.y(), b.z()))));
            if (r.bed().isPresent() && bed.isEmpty()) {
                VillageLife.LOGGER.debug("Resident {} lost their bed", r.fullName());
            }
        }
        return village.withResidents(updated);
    }

    private static List<Bed> beds(Village village) {
        List<Bed> beds = new ArrayList<>();
        for (Building building : village.buildings()) {
            if (building.type() == BuildingType.HOUSE && building.isValid()) {
                for (BlockPos bed : building.beds()) {
                    beds.add(new Bed(building.id(), bed.getX(), bed.getY(), bed.getZ()));
                }
            }
        }
        return beds;
    }

    /** A dry, loaded spot near the village border; falls back to the village center. */
    private static BlockPos arrivalPoint(ServerLevel level, Village village) {
        BlockPos center = village.center();
        for (int attempt = 0; attempt < 8; attempt++) {
            double angle = RANDOM.nextDouble() * Math.PI * 2;
            int distance = (int) (village.radius() * 0.85);
            int x = center.getX() + (int) Math.round(Math.cos(angle) * distance);
            int z = center.getZ() + (int) Math.round(Math.sin(angle) * distance);
            BlockPos column = new BlockPos(x, center.getY(), z);
            if (!level.isLoaded(column)) {
                continue;
            }
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos pos = new BlockPos(x, y, z);
            if (level.getFluidState(pos).isEmpty() && level.getFluidState(pos.below()).isEmpty()) {
                return pos;
            }
        }
        return center.above();
    }
}
