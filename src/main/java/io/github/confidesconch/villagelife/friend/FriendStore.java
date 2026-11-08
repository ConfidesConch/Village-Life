package io.github.confidesconch.villagelife.friend;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
 * Friendships and invitations of all players (M11g), saved once per world as
 * {@code data/villagelife/friends.dat}, plus the last known name of every player in it.
 *
 * <p>Data versions: 1 = friends (M11g).
 */
public final class FriendStore extends SavedData {
    public static final int DATA_VERSION = 1;

    private record Entry(UUID player, String name, Set<UUID> friends, Set<UUID> invitedBy) {
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                UUIDUtil.CODEC.fieldOf("player").forGetter(Entry::player),
                Codec.STRING.optionalFieldOf("name", "?").forGetter(Entry::name),
                UUIDUtil.CODEC_LINKED_SET.optionalFieldOf("friends", Set.of()).forGetter(Entry::friends),
                UUIDUtil.CODEC_LINKED_SET.optionalFieldOf("invited_by", Set.of()).forGetter(Entry::invitedBy)
        ).apply(instance, Entry::new));
    }

    private static final Codec<FriendStore> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("data_version", 1).forGetter(store -> DATA_VERSION),
            LenientCodecs.list(Entry.CODEC, "friend entry").fieldOf("players").forGetter(FriendStore::entries)
    ).apply(instance, FriendStore::new));

    public static final SavedDataType<FriendStore> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(VillageLife.MOD_ID, "friends"), FriendStore::new, CODEC);

    private final FriendGraph graph = new FriendGraph();
    private final Map<UUID, String> names = new HashMap<>();

    public FriendStore() {}

    private FriendStore(int loadedDataVersion, List<Entry> loaded) {
        for (Entry entry : loaded) {
            names.put(entry.player(), entry.name());
            graph.load(entry.player(), entry.friends(), entry.invitedBy());
        }
        if (loadedDataVersion < DATA_VERSION) {
            setDirty();
        }
    }

    public static FriendStore get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    private List<Entry> entries() {
        Set<UUID> players = new java.util.LinkedHashSet<>(graph.players());
        players.addAll(names.keySet());
        return players.stream()
                .map(p -> new Entry(p, nameOf(p), graph.friendsOf(p), graph.invitesTo(p)))
                .filter(e -> !e.friends().isEmpty() || !e.invitedBy().isEmpty())
                .toList();
    }

    /** The graph for reading; changes go through {@link #change}. */
    public FriendGraph graph() {
        return graph;
    }

    /** Runs a change on the graph and marks the data as changed. */
    public <T> T change(java.util.function.Function<FriendGraph, T> action) {
        T result = action.apply(graph);
        setDirty();
        return result;
    }

    public boolean areFriends(UUID a, UUID b) {
        return graph.areFriends(a, b);
    }

    public String nameOf(UUID player) {
        return names.getOrDefault(player, "?");
    }

    /** Remembers the current name of a player (names can change). */
    public void rememberName(UUID player, String name) {
        if (!name.equals(names.put(player, name))) {
            setDirty();
        }
    }
}
