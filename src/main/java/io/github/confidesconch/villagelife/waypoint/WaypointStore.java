package io.github.confidesconch.villagelife.waypoint;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.github.confidesconch.villagelife.VillageLife;
import io.github.confidesconch.villagelife.core.util.LenientCodecs;

import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * All waypoints of all players of a server (M11e), saved once per world as
 * {@code data/villagelife/waypoints.dat} (not per dimension – each waypoint knows its dimension).
 *
 * <p>Data versions: 1 = waypoints (M11e).
 */
public final class WaypointStore extends SavedData {
    public static final int DATA_VERSION = 1;

    private record PlayerWaypoints(UUID player, List<Waypoint> waypoints) {
        static final Codec<PlayerWaypoints> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                UUIDUtil.CODEC.fieldOf("player").forGetter(PlayerWaypoints::player),
                LenientCodecs.list(Waypoint.CODEC, "waypoint").fieldOf("waypoints").forGetter(PlayerWaypoints::waypoints)
        ).apply(instance, PlayerWaypoints::new));
    }

    private static final Codec<WaypointStore> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("data_version", 1).forGetter(store -> DATA_VERSION),
            LenientCodecs.list(PlayerWaypoints.CODEC, "player waypoints").fieldOf("players").forGetter(WaypointStore::entries)
    ).apply(instance, WaypointStore::new));

    public static final SavedDataType<WaypointStore> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(VillageLife.MOD_ID, "waypoints"), WaypointStore::new, CODEC);

    private final Map<UUID, List<Waypoint>> byPlayer = new HashMap<>();

    public WaypointStore() {}

    private WaypointStore(int loadedDataVersion, List<PlayerWaypoints> loaded) {
        loaded.forEach(entry -> byPlayer.put(entry.player(), List.copyOf(entry.waypoints())));
        if (loadedDataVersion < DATA_VERSION) {
            setDirty();
        }
    }

    /** The server-wide store, created empty on first use. */
    public static WaypointStore get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    private List<PlayerWaypoints> entries() {
        return byPlayer.entrySet().stream()
                .filter(entry -> !entry.getValue().isEmpty())
                .map(entry -> new PlayerWaypoints(entry.getKey(), entry.getValue()))
                .toList();
    }

    public List<Waypoint> of(UUID player) {
        return byPlayer.getOrDefault(player, List.of());
    }

    public void set(UUID player, List<Waypoint> waypoints) {
        if (!waypoints.equals(of(player))) {
            byPlayer.put(player, List.copyOf(waypoints));
            setDirty();
        }
    }
}
