package io.github.confidesconch.villagelife.friend;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import io.github.confidesconch.villagelife.core.config.SyncedConfig;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server side of friendships (M11g): invitations from the book, the friend list for the client,
 * friend positions for map and minimap, and a hook so other systems (shared waypoints) can react.
 */
public final class FriendService {
    private FriendService() {}

    private static final int POSITION_INTERVAL_TICKS = 20;
    private static final List<Consumer<ServerPlayer>> CHANGE_LISTENERS = new ArrayList<>();

    /** Called for both players whenever a friendship starts or ends (e.g. to resend shared waypoints). */
    public static synchronized void onFriendsChanged(Consumer<ServerPlayer> listener) {
        CHANGE_LISTENERS.add(listener);
    }

    /** True if both players are friends (used by claims and waypoints). */
    public static boolean areFriends(MinecraftServer server, UUID a, UUID b) {
        return FriendStore.get(server).areFriends(a, b);
    }

    /** Current name if online, else the last known one. */
    public static String nameOf(MinecraftServer server, UUID player) {
        ServerPlayer online = server.getPlayerList().getPlayer(player);
        return online != null ? online.getName().getString() : FriendStore.get(server).nameOf(player);
    }

    public static Set<UUID> friendsOf(MinecraftServer server, UUID player) {
        return FriendStore.get(server).graph().friendsOf(player);
    }

    static void onAction(FriendActionPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        MinecraftServer server = player.level().getServer();
        FriendStore store = FriendStore.get(server);
        store.rememberName(player.getUUID(), player.getName().getString());
        UUID parsed;
        try {
            parsed = payload.target().isEmpty() ? null : UUID.fromString(payload.target());
        } catch (IllegalArgumentException invalid) {
            return;
        }
        final UUID target = parsed;
        int limit = SyncedConfig.MAX_FRIENDS.get();
        UUID me = player.getUUID();
        FriendGraph.Result result = target == null ? null : switch (payload.action()) {
            case "invite" -> invite(server, store, player, target, limit);
            case "accept" -> store.change(g -> g.accept(me, target, limit));
            case "decline" -> store.change(g -> g.decline(me, target));
            case "remove" -> store.change(g -> g.remove(me, target));
            default -> null;
        };
        if (result != null) {
            answer(server, store, player, target, result);
        }
        send(player);
    }

    private static FriendGraph.Result invite(MinecraftServer server, FriendStore store, ServerPlayer player, UUID target, int limit) {
        ServerPlayer other = server.getPlayerList().getPlayer(target);
        if (other == null) {
            return null; // only online players can be invited
        }
        store.rememberName(target, other.getName().getString());
        FriendGraph.Result result = store.change(g -> g.invite(player.getUUID(), target, limit));
        if (result == FriendGraph.Result.INVITED) {
            other.sendSystemMessage(Component.translatable("villagelife.friend.invited_you", player.getName().getString()));
        }
        return result;
    }

    /** Tells the player what happened and updates the other player and the listeners. */
    private static void answer(MinecraftServer server, FriendStore store, ServerPlayer player, UUID target, FriendGraph.Result result) {
        String key = "villagelife.friend.result." + result.name().toLowerCase();
        player.sendOverlayMessage(Component.translatable(key, store.nameOf(target)));
        ServerPlayer other = server.getPlayerList().getPlayer(target);
        if (other != null) {
            send(other);
            if (result == FriendGraph.Result.BECAME_FRIENDS) {
                other.sendSystemMessage(Component.translatable("villagelife.friend.now_friends", player.getName().getString()));
            }
        }
        if (result == FriendGraph.Result.BECAME_FRIENDS || result == FriendGraph.Result.REMOVED) {
            List<Consumer<ServerPlayer>> listeners = List.copyOf(CHANGE_LISTENERS);
            for (ServerPlayer p : other == null ? List.of(player) : List.of(player, other)) {
                listeners.forEach(listener -> listener.accept(p));
            }
        }
    }

    /** Sends friends, invitations and the players online now to the player. */
    static void send(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        FriendStore store = FriendStore.get(server);
        FriendGraph graph = store.graph();
        UUID me = player.getUUID();
        CompoundTag data = new CompoundTag();
        data.put("friends", people(server, store, graph.friendsOf(me)));
        data.put("invites_in", people(server, store, graph.invitesTo(me)));
        data.put("invites_out", people(server, store, graph.invitesFrom(me)));
        ListTag online = new ListTag();
        for (ServerPlayer other : server.getPlayerList().getPlayers()) {
            if (!other.getUUID().equals(me)) {
                online.add(person(other.getUUID(), other.getName().getString(), true));
            }
        }
        data.put("online", online);
        data.putInt("limit", SyncedConfig.MAX_FRIENDS.get());
        PacketDistributor.sendToPlayer(player, new FriendSyncPayload(data));
    }

    private static ListTag people(MinecraftServer server, FriendStore store, Set<UUID> ids) {
        ListTag list = new ListTag();
        for (UUID id : ids) {
            ServerPlayer online = server.getPlayerList().getPlayer(id);
            list.add(person(id, online != null ? online.getName().getString() : store.nameOf(id), online != null));
        }
        return list;
    }

    private static CompoundTag person(UUID id, String name, boolean online) {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", id.toString());
        tag.putString("name", name);
        tag.putBoolean("online", online);
        return tag;
    }

    /** About once per second: each player with friends gets the positions of his online friends. */
    static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % POSITION_INTERVAL_TICKS != 0) {
            return;
        }
        FriendStore store = FriendStore.get(server);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Set<UUID> friends = store.graph().friendsOf(player.getUUID());
            if (friends.isEmpty()) {
                continue;
            }
            ListTag list = new ListTag();
            for (UUID id : friends) {
                ServerPlayer friend = server.getPlayerList().getPlayer(id);
                if (friend != null) {
                    CompoundTag tag = person(id, friend.getName().getString(), true);
                    tag.putString("dimension", friend.level().dimension().identifier().toString());
                    tag.putInt("x", friend.getBlockX());
                    tag.putInt("z", friend.getBlockZ());
                    list.add(tag);
                }
            }
            CompoundTag data = new CompoundTag();
            data.put("list", list);
            PacketDistributor.sendToPlayer(player, new FriendPositionsPayload(data));
        }
    }
}
