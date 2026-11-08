package io.github.confidesconch.villagelife.client.map;

import java.util.ArrayList;
import java.util.List;

import io.github.confidesconch.villagelife.waypoint.WaypointActionPayload;
import io.github.confidesconch.villagelife.waypoint.WaypointSyncPayload;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * The player's waypoints and those his friends share (M11e/M11g) on the client: a copy of the server's list, asked for once after
 * joining and replaced after every change. Map, minimap and the book read from here.
 */
public final class ClientWaypoints {
    private ClientWaypoints() {}

    /** One waypoint as the client sees it. */
    /** {@code owner} is empty for own waypoints, else the name of the friend who shares it (M11g). */
    public record Entry(int id, String name, int color, String dimension, int x, int y, int z, boolean visible,
                        boolean shared, String owner) {
        public boolean isOwn() {
            return owner.isEmpty();
        }
    }

    private static List<Entry> entries = List.of();
    private static int limit;
    private static boolean requested;

    /** Call every client tick: asks the server for the list once per connection. */
    public static void tick(Minecraft minecraft) {
        if (minecraft.player == null || minecraft.getConnection() == null) {
            requested = false;
            entries = List.of();
            return;
        }
        if (!requested) {
            requested = true;
            ClientPacketDistributor.sendToServer(WaypointActionPayload.sync());
        }
    }

    public static void onSync(WaypointSyncPayload payload, IPayloadContext context) {
        ListTag list = payload.data().getListOrEmpty("list");
        List<Entry> loaded = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompoundOrEmpty(i);
            loaded.add(new Entry(t.getIntOr("id", 0), t.getStringOr("name", "?"), t.getIntOr("color", 0),
                    t.getStringOr("dimension", ""), t.getIntOr("x", 0), t.getIntOr("y", 0), t.getIntOr("z", 0),
                    t.getBooleanOr("visible", true), t.getBooleanOr("shared", false), t.getStringOr("owner", "")));
        }
        entries = List.copyOf(loaded);
        limit = payload.data().getIntOr("limit", 0);
    }

    public static List<Entry> all() {
        return entries;
    }

    /** Only the player's own waypoints (the limit counts these). */
    public static List<Entry> own() {
        return entries.stream().filter(Entry::isOwn).toList();
    }

    public static int limit() {
        return limit;
    }

    /** Visible waypoints in the dimension the player is in now. */
    public static List<Entry> shownHere() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return List.of();
        }
        String dimension = minecraft.level.dimension().identifier().toString();
        return entries.stream().filter(e -> e.visible() && e.dimension().equals(dimension)).toList();
    }

    public static void send(String action, int id, String name, int color, int x, int y, int z, boolean shared) {
        ClientPacketDistributor.sendToServer(new WaypointActionPayload(action, id, name, color, x, y, z, shared));
    }
}
