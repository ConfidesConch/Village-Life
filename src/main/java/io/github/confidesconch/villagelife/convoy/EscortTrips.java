package io.github.confidesconch.villagelife.convoy;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import io.github.confidesconch.villagelife.VillageLife;
import io.github.confidesconch.villagelife.convoy.ConvoyStore.Trip;
import io.github.confidesconch.villagelife.market.contract.ContractService;
import io.github.confidesconch.villagelife.market.contract.ContractService.Found;
import io.github.confidesconch.villagelife.market.contract.Shipment;
import io.github.confidesconch.villagelife.profession.Profession;
import io.github.confidesconch.villagelife.profession.Professions;
import io.github.confidesconch.villagelife.profession.work.VillageGates;
import io.github.confidesconch.villagelife.resident.ResidentEntity;
import io.github.confidesconch.villagelife.resident.ResidentRecord;
import io.github.confidesconch.villagelife.village.Village;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityProcessor;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntitySpawnRequest;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.TagValueOutput;

/**
 * Escorts away with a convoy (M20c, owner's choice "3a"): the chosen guards walk out of the village
 * towards the road, and once outside (or after a minute) they are taken off the map and kept in the
 * {@link ConvoyStore} – they are really gone from the village. When the convoy is back (arrival plus
 * the way home) they come back through the gate. Guards whose chunk is not loaded leave as soon as it is.
 */
final class EscortTrips {
    private EscortTrips() {}

    /** Time to walk out of the village before an escort simply leaves. */
    private static final long LEAVE_TICKS = 1200;
    private static final int EXIT_OUTSIDE = 8;

    /** Picks up to {@code wanted} defenders of the sending village who are at home and fit; returns them. */
    static List<UUID> send(ConvoyStore store, Found from, Found to, Shipment shipment, int wanted) {
        if (wanted <= 0) {
            return List.of();
        }
        List<UUID> members = new ArrayList<>();
        for (ResidentRecord record : from.village().residents()) {
            if (members.size() >= wanted) {
                break;
            }
            boolean defender = record.job().flatMap(job -> Professions.get(job.profession())).map(Profession::defender).orElse(false);
            if (!defender || store.isAway(record.id())) {
                continue;
            }
            if (from.level().getEntity(record.id()) instanceof ResidentEntity resident && resident.isKnockedOut()) {
                continue; // stays in bed
            }
            members.add(record.id());
        }
        if (!members.isEmpty()) {
            long journey = Math.max(1, shipment.arrivesAt() - shipment.departedAt());
            store.addTrip(new Trip(from.village().id(), shipment.contractId(), shipment.payment(), shipment.departedAt(),
                    shipment.departedAt() + LEAVE_TICKS, shipment.arrivesAt() + journey, exitPoint(from, to), members, Map.of()));
        }
        return members;
    }

    /** Where the escorts walk to: a bit outside the border, towards the partner village. */
    private static BlockPos exitPoint(Found from, Found to) {
        Village village = from.village();
        BlockPos center = village.center();
        double angle = from.level() == to.level()
                ? Math.atan2(to.village().center().getZ() - center.getZ(), to.village().center().getX() - center.getX()) : 0;
        int x = center.getX() + (int) Math.round(Math.cos(angle) * (village.radius() + EXIT_OUTSIDE));
        int z = center.getZ() + (int) Math.round(Math.sin(angle) * (village.radius() + EXIT_OUTSIDE));
        int y = from.level().isLoaded(new BlockPos(x, center.getY(), z))
                ? from.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) : center.getY();
        return new BlockPos(x, y, z);
    }

    /** Where an escort walking out goes, or null if the resident is not leaving. */
    static BlockPos exitOf(MinecraftServer server, ResidentEntity resident) {
        Trip trip = tripOf(server, resident.getUUID());
        return trip == null ? null : trip.exit();
    }

    /** The trip of an escort that is still walking out of its village, or null. */
    static Trip tripOf(MinecraftServer server, UUID resident) {
        for (Trip trip : ConvoyStore.get(server).trips()) {
            if (trip.members().contains(resident) && !trip.stashed().containsKey(resident)) {
                return trip;
            }
        }
        return null;
    }

    // ---- every second --------------------------------------------------------------------------

    static void tick(MinecraftServer server, ConvoyStore store, long now) {
        for (Trip trip : store.trips()) {
            Optional<Found> home = ContractService.find(server, trip.village());
            if (home.isEmpty()) {
                store.replaceTrip(trip, null); // the village is gone
                continue;
            }
            Trip updated = trip;
            for (UUID member : trip.members()) {
                updated = update(home.get(), updated, member, now);
            }
            if (!updated.equals(trip)) {
                store.replaceTrip(trip, updated);
            }
        }
    }

    private static Trip update(Found home, Trip trip, UUID member, long now) {
        ServerLevel level = home.level();
        CompoundTag stashed = trip.stashed().get(member);
        if (stashed != null) {
            if (now < trip.returnAt() || ConvoyWagons.isOut(member)) {
                return trip;
            }
            BlockPos at = returnPoint(level, home.village(), trip.exit());
            return at != null && unstash(level, member, stashed, at) ? trip.without(member) : trip; // else: when the gate is loaded
        }
        if (now >= trip.returnAt()) {
            return trip.without(member); // never left (its chunk was not loaded): simply stays home
        }
        if (!(level.getEntity(member) instanceof ResidentEntity resident)) {
            return trip; // not loaded: leaves as soon as it is
        }
        if (resident.isKnockedOut()) {
            return trip.without(member);
        }
        if (now >= trip.leaveUntil() || isOutside(resident, home.village())) {
            BlockPos at = resident.blockPosition();
            CompoundTag data = stash(resident);
            Trip left = trip.withStashed(member, data);
            ConvoyWagons.adopt(level, left, member, data, at); // convoy seen: walks on beside the wagon
            return left;
        }
        return trip;
    }

    private static boolean isOutside(ResidentEntity resident, Village village) {
        double dx = resident.getX() - village.center().getX();
        double dz = resident.getZ() - village.center().getZ();
        return Math.sqrt(dx * dx + dz * dz) > village.radius() + 2;
    }

    // ---- off the map and back -------------------------------------------------------------------

    /** Saves the resident and takes it off the map. */
    static CompoundTag stash(ResidentEntity resident) {
        resident.stopRiding();
        resident.getNavigation().stop();
        resident.removeTag(ConvoyWagons.COPY_TAG);
        CompoundTag data;
        try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(VillageLife.LOGGER)) {
            TagValueOutput output = TagValueOutput.createWithContext(reporter, resident.registryAccess());
            resident.save(output);
            data = output.buildResult();
        }
        resident.discard();
        return data;
    }

    /** Puts a stashed resident back into the world at {@code at} ({@code copy}: only to be seen on the road); null if that failed. */
    static Entity load(ServerLevel level, UUID id, CompoundTag data, BlockPos at, boolean copy) {
        Entity existing = level.getEntity(id);
        if (existing != null) {
            if (!existing.entityTags().contains(ConvoyWagons.COPY_TAG)) {
                return null; // the resident is somehow there already: do not double it
            }
            existing.discard();
        }
        Entity entity = EntityType.loadEntityRecursive(data, level, new EntitySpawnRequest(EntitySpawnReason.LOAD, false),
                EntityProcessor.NOP);
        if (entity == null) {
            return null;
        }
        entity.removeTag(ConvoyWagons.COPY_TAG);
        if (copy) {
            entity.addTag(ConvoyWagons.COPY_TAG);
        }
        entity.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, entity.getYRot(), 0.0F);
        return level.addFreshEntity(entity) ? entity : null;
    }

    private static boolean unstash(ServerLevel level, UUID id, CompoundTag data, BlockPos at) {
        if (!level.isLoaded(at)) {
            return false;
        }
        Entity entity = load(level, id, data, at, false);
        if (entity == null) {
            VillageLife.LOGGER.warn("Escort could not come back at {}; trying again", at);
        }
        return entity != null;
    }

    /** Back home through the gate nearest to where they left (inside), else just inside the border. */
    private static BlockPos returnPoint(ServerLevel level, Village village, BlockPos exit) {
        List<BlockPos> gates = VillageGates.of(level, village);
        if (!gates.isEmpty()) {
            BlockPos gate = gates.stream().min(Comparator.comparingDouble(g -> g.distSqr(exit))).orElseThrow();
            return VillageGates.watchPoint(gate, village.center());
        }
        BlockPos center = village.center();
        double angle = Math.atan2(exit.getZ() - center.getZ(), exit.getX() - center.getX());
        int distance = Math.max(2, village.radius() - 4);
        int x = center.getX() + (int) Math.round(Math.cos(angle) * distance);
        int z = center.getZ() + (int) Math.round(Math.sin(angle) * distance);
        if (!level.isLoaded(new BlockPos(x, center.getY(), z))) {
            return null;
        }
        return new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
    }
}
