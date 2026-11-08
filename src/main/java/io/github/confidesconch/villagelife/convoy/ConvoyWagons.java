package io.github.confidesconch.villagelife.convoy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import io.github.confidesconch.villagelife.convoy.ConvoyStore.Trip;
import io.github.confidesconch.villagelife.market.contract.ContractService;
import io.github.confidesconch.villagelife.market.contract.ContractService.Found;
import io.github.confidesconch.villagelife.market.contract.ContractStore;
import io.github.confidesconch.villagelife.market.contract.Shipment;
import io.github.confidesconch.villagelife.resident.ResidentEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.equine.Donkey;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * The convoy you can see (M20c, owner's choice "1a"): while a player is near the road, a shipment is a
 * donkey with a chest ("trade wagon") walking from village to village, with its escorts beside it. Is
 * the shipment raided then, the bandits really attack: beaten off – nothing is lost; the donkey killed,
 * all escorts down or after two minutes – the bandits get away with part of the goods. When the players
 * leave, wagon and escorts vanish again (the escorts go back into the {@link ConvoyStore}).
 *
 * <p>Wagons, escorts and bandits on the road are only copies for the eyes: whatever of them is still
 * found in a chunk after a restart is removed when it loads.
 */
final class ConvoyWagons {
    private ConvoyWagons() {}

    static final String COPY_TAG = "villagelife_escort_copy";
    static final String WAGON_TAG = "villagelife_trade_wagon";
    static final String AMBUSH_TAG = "villagelife_ambush";
    static final double SHOW_RANGE = 64;
    private static final double HIDE_RANGE = 96;
    /** Only a wagon stuck far behind is put back on the road (owner: no jumping around). */
    private static final double CATCH_UP = 40;
    private static final int EDGE_OUTSIDE = 8;
    private static final double ESCORT_CATCH_UP = 24;

    /** A wagon on the map with its escorts and, during an attack, its bandits. */
    static final class Wagon {
        final ServerLevel level;
        Shipment shipment;
        UUID donkey;
        final Set<UUID> escorts = new HashSet<>();
        final Set<UUID> bandits = new HashSet<>();
        long ambushUntil;

        Wagon(ServerLevel level, Shipment shipment) {
            this.level = level;
            this.shipment = shipment;
        }
    }

    /** The road of a shipment: both villages in one level. */
    record Road(ServerLevel level, Vec3 from, Vec3 to) {
        Vec3 at(double progress) {
            return from.lerp(to, progress);
        }
    }

    private static final List<Wagon> WAGONS = new ArrayList<>();
    /** Shipments whose wagon was killed: it does not come back (the goods travel on unseen). */
    private static final List<Shipment> WRECKED = new ArrayList<>();
    /** Copies of escorts on the road → their wagon's donkey. */
    private static final Map<UUID, UUID> OUT = new HashMap<>();

    /** The escort is on the road as a copy right now. */
    static boolean isOut(UUID resident) {
        return OUT.containsKey(resident);
    }

    /**
     * The wagon an escort walks with, or null: on the road, or still walking out of the village while
     * its convoy is already seen (then it goes straight to the wagon).
     */
    static Entity wagonOf(ResidentEntity resident) {
        if (!(resident.level() instanceof ServerLevel level)) {
            return null;
        }
        UUID donkey = OUT.get(resident.getUUID());
        if (donkey == null) {
            Wagon wagon = wagonOfTrip(level, EscortTrips.tripOf(level.getServer(), resident.getUUID()));
            donkey = wagon == null ? null : wagon.donkey;
        }
        return donkey == null ? null : level.getEntity(donkey);
    }

    private static Wagon wagonOfTrip(ServerLevel level, Trip trip) {
        if (trip == null) {
            return null;
        }
        return WAGONS.stream().filter(w -> w.level == level && trip.escorts(w.shipment)).findFirst().orElse(null);
    }

    /**
     * An escort just left its village (it is in the store now). If its convoy is seen right now, it
     * walks on beside the wagon at once – as a copy, standing where it left.
     */
    static void adopt(ServerLevel level, Trip trip, UUID member, CompoundTag data, BlockPos at) {
        Wagon wagon = wagonOfTrip(level, trip);
        if (wagon == null) {
            return;
        }
        OUT.put(member, wagon.donkey); // before it joins the level
        if (EscortTrips.load(level, member, data, at, true) != null) {
            wagon.escorts.add(member);
        } else {
            OUT.remove(member);
        }
    }

    // ---- every second --------------------------------------------------------------------------

    static void tick(MinecraftServer server, List<Shipment> shipments, long now) {
        Iterator<Wagon> it = WAGONS.iterator();
        while (it.hasNext()) {
            Wagon wagon = it.next();
            Shipment current = shipments.stream().filter(wagon.shipment::sameTrip).filter(s -> onTheRoad(s, now)).findFirst()
                    .orElse(null);
            Optional<Road> road = current == null ? Optional.empty() : road(server, current);
            if (road.isEmpty() || !playerNear(road.get().level(), road.get().at(current.progress(now)), HIDE_RANGE)) {
                hide(server, wagon);
                it.remove();
                continue;
            }
            wagon.shipment = current;
            if (!update(server, wagon, road.get(), now)) {
                hide(server, wagon);
                it.remove();
            }
        }
        WRECKED.removeIf(gone -> shipments.stream().noneMatch(gone::sameTrip));
        for (Shipment shipment : shipments) {
            if (onTheRoad(shipment, now) && find(shipment) == null && WRECKED.stream().noneMatch(shipment::sameTrip)
                    && ContractStore.get(server).byId(shipment.contractId()).isPresent()) {
                road(server, shipment).ifPresent(road -> {
                    Vec3 at = road.at(shipment.progress(now));
                    if (playerNear(road.level(), at, SHOW_RANGE)) {
                        show(server, shipment, road, at);
                    }
                });
            }
        }
    }

    /** Really travelling: not yet there and not waiting at a full storage. */
    private static boolean onTheRoad(Shipment shipment, long now) {
        return !shipment.waiting() && now < shipment.arrivesAt();
    }

    private static Wagon find(Shipment shipment) {
        return WAGONS.stream().filter(w -> w.shipment.sameTrip(shipment)).findFirst().orElse(null);
    }

    static Optional<Road> road(MinecraftServer server, Shipment shipment) {
        Optional<Found> from = ContractService.find(server, shipment.from());
        Optional<Found> to = ContractService.find(server, shipment.to());
        if (from.isEmpty() || to.isEmpty() || from.get().level() != to.get().level()) {
            return Optional.empty();
        }
        Vec3 a = Vec3.atBottomCenterOf(from.get().village().center());
        Vec3 b = Vec3.atBottomCenterOf(to.get().village().center());
        // the road runs from gate to gate: the wagon never walks through a village (over its roofs)
        return Optional.of(new Road(from.get().level(), edge(a, b, from.get().village().radius()),
                edge(b, a, to.get().village().radius())));
    }

    /** The point just outside a village (center {@code center}) on the way to {@code towards}. */
    private static Vec3 edge(Vec3 center, Vec3 towards, int radius) {
        Vec3 direction = towards.subtract(center);
        double length = direction.horizontalDistance();
        if (length < 1.0) {
            return center;
        }
        double outside = Math.min(length / 2, radius + EDGE_OUTSIDE);
        return center.add(direction.x / length * outside, 0, direction.z / length * outside);
    }

    private static boolean playerNear(ServerLevel level, Vec3 at, double range) {
        for (ServerPlayer player : level.players()) {
            double dx = player.getX() - at.x;
            double dz = player.getZ() - at.z;
            if (!player.isSpectator() && dx * dx + dz * dz < range * range) {
                return true;
            }
        }
        return false;
    }

    static BlockPos ground(ServerLevel level, Vec3 at) {
        BlockPos column = BlockPos.containing(at.x, 0, at.z);
        if (!level.isLoaded(column)) {
            return null;
        }
        return new BlockPos(column.getX(), level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ()),
                column.getZ());
    }

    // ---- showing and hiding --------------------------------------------------------------------

    private static void show(MinecraftServer server, Shipment shipment, Road road, Vec3 at) {
        ServerLevel level = road.level();
        BlockPos pos = ground(level, at);
        Donkey donkey = pos == null ? null : EntityTypes.DONKEY.create(level, EntitySpawnReason.EVENT);
        if (donkey == null) {
            return;
        }
        donkey.setTamed(true);
        donkey.setChest(true);
        donkey.setCustomName(Component.translatable("villagelife.convoy.wagon"));
        donkey.addTag(WAGON_TAG);
        donkey.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0.0F, 0.0F);
        Wagon wagon = new Wagon(level, shipment);
        wagon.donkey = donkey.getUUID();
        if (!level.addFreshEntity(donkey)) {
            return;
        }
        WAGONS.add(wagon);
        ConvoyStore store = ConvoyStore.get(server);
        for (Trip trip : store.trips()) {
            if (!trip.escorts(shipment)) {
                continue;
            }
            for (Map.Entry<UUID, CompoundTag> escort : trip.stashed().entrySet()) {
                OUT.put(escort.getKey(), wagon.donkey); // before it joins the level
                if (EscortTrips.load(level, escort.getKey(), escort.getValue(), pos, true) != null) {
                    wagon.escorts.add(escort.getKey());
                } else {
                    OUT.remove(escort.getKey());
                }
            }
        }
    }

    /** Wagon, escorts and bandits leave the map; the escorts go back into the store as they are now. */
    private static void hide(MinecraftServer server, Wagon wagon) {
        ConvoyStore store = ConvoyStore.get(server);
        for (UUID id : wagon.escorts) {
            OUT.remove(id);
            if (wagon.level.getEntity(id) instanceof ResidentEntity copy) {
                CompoundTag data = EscortTrips.stash(copy);
                for (Trip trip : store.trips()) {
                    if (trip.stashed().containsKey(id)) {
                        store.replaceTrip(trip, trip.withStashed(id, data));
                    }
                }
            }
        }
        ConvoyAmbush.removeBandits(wagon);
        Entity donkey = wagon.level.getEntity(wagon.donkey);
        if (donkey != null) {
            donkey.discard();
        }
    }

    /** Moves the wagon along the road and runs an attack; false if the wagon is gone (it comes again). */
    private static boolean update(MinecraftServer server, Wagon wagon, Road road, long now) {
        if (!(wagon.level.getEntity(wagon.donkey) instanceof Donkey donkey) || !donkey.isAlive()) {
            if (wagon.ambushUntil > 0) {
                ConvoyAmbush.finish(server, wagon, false);
            }
            WRECKED.add(wagon.shipment); // killed (or gone): it does not come back
            return false;
        }
        if (wagon.ambushUntil > 0) {
            ConvoyAmbush.update(server, wagon, now);
            return true; // the wagon stops while it is attacked
        }
        BlockPos target = ground(wagon.level, road.at(wagon.shipment.progress(now)));
        if (target == null) {
            return false;
        }
        if (donkey.distanceToSqr(Vec3.atBottomCenterOf(target)) > CATCH_UP * CATCH_UP) {
            donkey.snapTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, donkey.getYRot(), 0.0F);
            donkey.getNavigation().stop();
        } else {
            donkey.getNavigation().moveTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, 1.0);
        }
        for (UUID id : wagon.escorts) {
            if (wagon.level.getEntity(id) instanceof ResidentEntity escort && !escort.isKnockedOut()
                    && escort.distanceToSqr(donkey) > ESCORT_CATCH_UP * ESCORT_CATCH_UP) {
                escort.snapTo(donkey.getX(), donkey.getY(), donkey.getZ(), escort.getYRot(), 0.0F);
                escort.getNavigation().stop();
            }
        }
        return true;
    }

    // ---- attack on the road --------------------------------------------------------------------

    /** The raid of a shipment is due: true if its wagon is seen – the bandits really come (or already did). */
    static boolean ambush(MinecraftServer server, Shipment shipment) {
        Wagon wagon = find(shipment);
        if (wagon == null) {
            return false;
        }
        if (wagon.ambushUntil == 0 && !ConvoyAmbush.start(server, wagon)) {
            return false;
        }
        return true;
    }

    /** Players cannot hurt a trade wagon or its escorts (they belong to the villages). */
    static void onIncomingDamage(LivingIncomingDamageEvent event) {
        Set<String> tags = event.getEntity().entityTags();
        if ((tags.contains(WAGON_TAG) || tags.contains(COPY_TAG)) && event.getSource().getEntity() instanceof Player) {
            event.setCanceled(true);
        }
    }

    // ---- loading and stopping ------------------------------------------------------------------

    /** Copies left in a chunk from before (restart, crash, the chunk unloaded) do not come back. */
    static void onJoin(EntityJoinLevelEvent event) {
        if (!event.loadedFromDisk()) {
            return;
        }
        Entity entity = event.getEntity();
        Set<String> tags = entity.entityTags();
        UUID id = entity.getUUID();
        boolean known = OUT.containsKey(id) || WAGONS.stream().anyMatch(w -> id.equals(w.donkey) || w.bandits.contains(id));
        if (!known && (tags.contains(COPY_TAG) || tags.contains(WAGON_TAG) || tags.contains(AMBUSH_TAG))) {
            event.setCanceled(true);
        }
    }

    /** Before the world is saved for the last time: escorts back into the store, everything else away. */
    static void stopAll(MinecraftServer server) {
        WAGONS.forEach(wagon -> hide(server, wagon));
        WAGONS.clear();
        OUT.clear();
        WRECKED.clear();
    }
}
