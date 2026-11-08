package io.github.confidesconch.villagelife.client.map;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import io.github.confidesconch.villagelife.friend.FriendActionPayload;
import io.github.confidesconch.villagelife.friend.FriendPositionsPayload;
import io.github.confidesconch.villagelife.friend.FriendSyncPayload;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Friends on the client (M11g): the friend list and invitations (asked for after joining and when
 * the book's friends chapter opens) and where online friends are (pushed by the server every second).
 */
public final class ClientFriends {
    private ClientFriends() {}

    /** A player in one of the lists. */
    public record Person(UUID id, String name, boolean online) {}

    /** Where a friend is. */
    public record Position(UUID id, String name, String dimension, int x, int z) {}

    private static List<Person> friends = List.of();
    private static List<Person> invitesIn = List.of();
    private static List<Person> invitesOut = List.of();
    private static List<Person> online = List.of();
    private static List<Position> positions = List.of();
    private static int limit;
    private static boolean requested;

    public static void tick(Minecraft minecraft) {
        if (minecraft.player == null || minecraft.getConnection() == null) {
            requested = false;
            friends = List.of();
            positions = List.of();
            return;
        }
        if (!requested) {
            requested = true;
            refresh();
        }
    }

    /** Asks the server for the current lists (e.g. when the friends chapter is opened). */
    public static void refresh() {
        send("sync", null);
    }

    public static void send(String action, UUID target) {
        ClientPacketDistributor.sendToServer(new FriendActionPayload(action, target == null ? "" : target.toString()));
    }

    public static void onSync(FriendSyncPayload payload, IPayloadContext context) {
        CompoundTag data = payload.data();
        friends = people(data.getListOrEmpty("friends"));
        invitesIn = people(data.getListOrEmpty("invites_in"));
        invitesOut = people(data.getListOrEmpty("invites_out"));
        online = people(data.getListOrEmpty("online"));
        limit = data.getIntOr("limit", 0);
    }

    public static void onPositions(FriendPositionsPayload payload, IPayloadContext context) {
        ListTag list = payload.data().getListOrEmpty("list");
        List<Position> loaded = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompoundOrEmpty(i);
            UUID id = parse(t.getStringOr("id", ""));
            if (id != null) {
                loaded.add(new Position(id, t.getStringOr("name", "?"), t.getStringOr("dimension", ""),
                        t.getIntOr("x", 0), t.getIntOr("z", 0)));
            }
        }
        positions = List.copyOf(loaded);
    }

    private static List<Person> people(ListTag list) {
        List<Person> result = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompoundOrEmpty(i);
            UUID id = parse(t.getStringOr("id", ""));
            if (id != null) {
                result.add(new Person(id, t.getStringOr("name", "?"), t.getBooleanOr("online", false)));
            }
        }
        result.sort((a, b) -> a.online() != b.online() ? (a.online() ? -1 : 1) : a.name().compareToIgnoreCase(b.name()));
        return List.copyOf(result);
    }

    private static UUID parse(String text) {
        try {
            return UUID.fromString(text);
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }

    public static boolean isFriend(UUID player) {
        return friends.stream().anyMatch(f -> f.id().equals(player));
    }

    public static List<Person> friends() {
        return friends;
    }

    public static List<Person> invitesIn() {
        return invitesIn;
    }

    public static List<Person> invitesOut() {
        return invitesOut;
    }

    /** Online players who are neither friends nor already invited by me. */
    public static List<Person> invitable() {
        return online.stream()
                .filter(p -> !isFriend(p.id()) && invitesOut.stream().noneMatch(o -> o.id().equals(p.id())))
                .toList();
    }

    public static int limit() {
        return limit;
    }

    /** Friends in the dimension the player is in now. */
    public static List<Position> positionsHere() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return List.of();
        }
        String here = minecraft.level.dimension().identifier().toString();
        return positions.stream().filter(p -> p.dimension().equals(here)).toList();
    }
}
