package io.github.confidesconch.villagelife.client.map;

import java.util.List;

import io.github.confidesconch.villagelife.core.util.MarkerColors;
import io.github.confidesconch.villagelife.resident.ResidentEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

/** Draws markers on top of a {@link MapImage}: village border, zones, buildings, waypoints, residents and the player. */
public final class MapOverlay {
    private MapOverlay() {}

    public static final int HOUSE = 0xFF3FA34D;
    public static final int STORAGE = 0xFF3D6FD1;
    public static final int WORKPLACE = 0xFFE08A1E;
    /** M21d: fields and pastures marked with a marker. */
    public static final int FIELD = 0xFFC9B23A;
    public static final int PASTURE = 0xFF8FD16A;
    public static final int INVALID = 0xFFD03A2F;
    public static final int RESIDENT = 0xFFFFD84A;
    public static final int PLAYER = 0xFFFFFFFF;
    public static final int BORDER = 0xFFB3261E;

    /** The village border (a circle) from the book's overview section. */
    public static void villageBorder(GuiGraphicsExtractor g, MapImage map, int x, int y, int scaledSize, CompoundTag overview) {
        if (!overview.contains("radius")) {
            return;
        }
        double cx = overview.getIntOr("center_x", 0) + 0.5;
        double cz = overview.getIntOr("center_z", 0) + 0.5;
        int radius = overview.getIntOr("radius", 0);
        int steps = Math.max(64, radius * 4);
        for (int i = 0; i < steps; i++) {
            double angle = 2 * Math.PI * i / steps;
            dot(g, map, x, y, scaledSize, cx + Math.cos(angle) * radius, cz + Math.sin(angle) * radius, 1, BORDER);
        }
    }

    /** Building outlines from the book's buildings section, coloured by type. */
    public static void buildings(GuiGraphicsExtractor g, MapImage map, int x, int y, int scaledSize, CompoundTag buildings) {
        ListTag list = buildings.getListOrEmpty("list");
        float factor = (float) scaledSize / map.size();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag b = list.getCompoundOrEmpty(i);
            if (!b.contains("min_x")) {
                continue;
            }
            int color = !b.getBooleanOr("valid", false) ? INVALID : switch (b.getStringOr("type", "")) {
                case "villagelife.building_type.house" -> HOUSE;
                case "villagelife.building_type.storage" -> STORAGE;
                case "villagelife.building_type.field" -> FIELD;
                case "villagelife.building_type.pasture" -> PASTURE;
                default -> WORKPLACE;
            };
            int x0 = x + (int) (map.pixelX(b.getIntOr("min_x", 0) - 1) * factor);
            int z0 = y + (int) (map.pixelZ(b.getIntOr("min_z", 0) - 1) * factor);
            int x1 = x + (int) (map.pixelX(b.getIntOr("max_x", 0) + 2) * factor);
            int z1 = y + (int) (map.pixelZ(b.getIntOr("max_z", 0) + 2) * factor);
            if (x1 < x || z1 < y || x0 > x + scaledSize || z0 > y + scaledSize) {
                continue;
            }
            x0 = Math.max(x, x0);
            z0 = Math.max(y, z0);
            x1 = Math.min(x + scaledSize, Math.max(x1, x0 + 2));
            z1 = Math.min(y + scaledSize, Math.max(z1, z0 + 2));
            g.fill(x0, z0, x1, z1, (color & 0x00FFFFFF) | 0x66000000);
            g.outline(x0, z0, x1 - x0, z1 - z0, color);
        }
    }

    public static final int OTHER_PLAYER = 0xFFB0B0B0;
    public static final int FRIEND = 0xFF4FD8FF;

    /**
     * Other players (M11g): everybody the client sees nearby (grey, friends light blue) and friends
     * farther away from the server's position updates.
     */
    public static void otherPlayers(GuiGraphicsExtractor g, MapImage map, int x, int y, int scaledSize) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }
        java.util.Set<java.util.UUID> drawn = new java.util.HashSet<>();
        for (Player other : minecraft.level.players()) {
            if (other != minecraft.player) {
                drawn.add(other.getUUID());
                int color = ClientFriends.isFriend(other.getUUID()) ? FRIEND : OTHER_PLAYER;
                outlinedDot(g, map, x, y, scaledSize, other.getX(), other.getZ(), color);
            }
        }
        for (ClientFriends.Position friend : ClientFriends.positionsHere()) {
            if (!drawn.contains(friend.id())) {
                outlinedDot(g, map, x, y, scaledSize, friend.x() + 0.5, friend.z() + 0.5, FRIEND);
            }
        }
    }

    /** Name of the player under the mouse (within 3 pixels), or null. */
    public static String playerAt(MapImage map, int x, int y, int scaledSize, int mouseX, int mouseY) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return null;
        }
        float factor = (float) scaledSize / map.size();
        for (Player other : minecraft.level.players()) {
            if (other != minecraft.player && near(map, x, y, factor, other.getX(), other.getZ(), mouseX, mouseY)) {
                return other.getName().getString();
            }
        }
        for (ClientFriends.Position friend : ClientFriends.positionsHere()) {
            if (near(map, x, y, factor, friend.x() + 0.5, friend.z() + 0.5, mouseX, mouseY)) {
                return friend.name();
            }
        }
        return null;
    }

    private static boolean near(MapImage map, int x, int y, float factor, double worldX, double worldZ, int mouseX, int mouseY) {
        float px = x + map.pixelX(worldX) * factor;
        float pz = y + map.pixelZ(worldZ) * factor;
        return Math.abs(px - mouseX) <= 3 && Math.abs(pz - mouseY) <= 3;
    }

    private static void outlinedDot(GuiGraphicsExtractor g, MapImage map, int x, int y, int scaledSize,
                                    double worldX, double worldZ, int color) {
        dot(g, map, x, y, scaledSize, worldX, worldZ, 5, 0xFF000000);
        dot(g, map, x, y, scaledSize, worldX, worldZ, 3, color);
    }

    /** Residents the client can see (loaded nearby) and the player. */
    public static void residentsAndPlayer(GuiGraphicsExtractor g, MapImage map, int x, int y, int scaledSize) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (minecraft.level == null || player == null) {
            return;
        }
        float factor = (float) scaledSize / map.size();
        double half = map.size() / 2.0 / factor * 4; // generous search box
        AABB box = player.getBoundingBox().inflate(Math.max(64, half * 4));
        for (ResidentEntity resident : minecraft.level.getEntitiesOfClass(ResidentEntity.class, box)) {
            dot(g, map, x, y, scaledSize, resident.getX(), resident.getZ(), 2, RESIDENT);
        }
        dot(g, map, x, y, scaledSize, player.getX(), player.getZ(), 3, PLAYER);
    }

    /** The village's zones (M11e): translucent rectangles with outline and, if there is room, the name. */
    public static void zones(GuiGraphicsExtractor g, MapImage map, int x, int y, int scaledSize, CompoundTag zones) {
        ListTag list = zones.getListOrEmpty("list");
        for (int i = 0; i < list.size(); i++) {
            CompoundTag z = list.getCompoundOrEmpty(i);
            rectangle(g, map, x, y, scaledSize, z.getIntOr("min_x", 0), z.getIntOr("min_z", 0),
                    z.getIntOr("max_x", 0), z.getIntOr("max_z", 0), MarkerColors.argb(z.getIntOr("color", 0)),
                    z.getStringOr("name", ""));
        }
    }

    /** A rectangle over the given block corners (inclusive); {@code label} is drawn inside if it fits. */
    public static void rectangle(GuiGraphicsExtractor g, MapImage map, int x, int y, int scaledSize,
                                 int minX, int minZ, int maxX, int maxZ, int color, String label) {
        float factor = (float) scaledSize / map.size();
        int x0 = Math.max(x, x + (int) (map.pixelX(minX) * factor));
        int z0 = Math.max(y, y + (int) (map.pixelZ(minZ) * factor));
        int x1 = Math.min(x + scaledSize, x + (int) Math.ceil(map.pixelX(maxX + 1) * factor));
        int z1 = Math.min(y + scaledSize, y + (int) Math.ceil(map.pixelZ(maxZ + 1) * factor));
        if (x1 <= x0 || z1 <= z0) {
            return;
        }
        g.fill(x0, z0, x1, z1, (color & 0x00FFFFFF) | 0x44000000);
        g.outline(x0, z0, x1 - x0, z1 - z0, color);
        var font = Minecraft.getInstance().font;
        if (!label.isEmpty() && font.width(label) + 4 <= x1 - x0 && font.lineHeight + 2 <= z1 - z0) {
            g.text(font, label, x0 + 2, z0 + 2, 0xFFFFFFFF, true);
        }
    }

    /**
     * Waypoints as small coloured flags. With {@code clampToEdge} (minimap) waypoints outside the
     * map are drawn smaller at the edge, in their direction.
     */
    public static void waypoints(GuiGraphicsExtractor g, MapImage map, int x, int y, int scaledSize,
                                 List<ClientWaypoints.Entry> waypoints, boolean clampToEdge) {
        float factor = (float) scaledSize / map.size();
        for (ClientWaypoints.Entry w : waypoints) {
            float px = x + map.pixelX(w.x() + 0.5) * factor;
            float pz = y + map.pixelZ(w.z() + 0.5) * factor;
            boolean outside = px < x || pz < y || px >= x + scaledSize || pz >= y + scaledSize;
            int color = MarkerColors.argb(w.color());
            if (!outside) {
                flag(g, (int) px, (int) pz, color);
            } else if (clampToEdge) {
                int ex = (int) Math.max(x + 1, Math.min(x + scaledSize - 2, px));
                int ez = (int) Math.max(y + 1, Math.min(y + scaledSize - 2, pz));
                g.fill(ex - 2, ez - 2, ex + 2, ez + 2, 0xFF000000);
                g.fill(ex - 1, ez - 1, ex + 1, ez + 1, color);
            }
        }
    }

    public static final int CLAIM_OWN = 0xFF2E9E3E;
    public static final int CLAIM_OTHER = 0xFFD03A2F;

    /** Claimed chunks (M11f): own green, friends' light blue (M11g), others red; with {@code grid} also the chunk lines. */
    public static void claims(GuiGraphicsExtractor g, MapImage map, int x, int y, int scaledSize, boolean grid) {
        float factor = (float) scaledSize / map.size();
        int minChunkX = map.worldX(0) >> 4;
        int maxChunkX = map.worldX(map.size() - 1) >> 4;
        int minChunkZ = map.worldZ(0) >> 4;
        int maxChunkZ = map.worldZ(map.size() - 1) >> 4;
        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                ClientClaims.Entry claim = ClientClaims.at(cx, cz);
                if (claim == null) {
                    continue;
                }
                int color = ClientClaims.isMine(claim) ? CLAIM_OWN
                        : ClientFriends.isFriend(claim.owner()) ? FRIEND : CLAIM_OTHER;
                int x0 = Math.max(x, x + Math.round(map.pixelX(cx << 4) * factor));
                int z0 = Math.max(y, y + Math.round(map.pixelZ(cz << 4) * factor));
                int x1 = Math.min(x + scaledSize, x + Math.round(map.pixelX((cx + 1) << 4) * factor));
                int z1 = Math.min(y + scaledSize, y + Math.round(map.pixelZ((cz + 1) << 4) * factor));
                if (x1 > x0 && z1 > z0) {
                    g.fill(x0, z0, x1, z1, (color & 0x00FFFFFF) | 0x55000000);
                }
            }
        }
        float spacing = (map.pixelX(16) - map.pixelX(0)) * factor; // screen pixels per chunk
        if (!grid || spacing < 4) {
            return;
        }
        for (int cx = minChunkX; cx <= maxChunkX + 1; cx++) {
            int px = x + Math.round(map.pixelX(cx << 4) * factor);
            if (px >= x && px < x + scaledSize) {
                g.fill(px, y, px + 1, y + scaledSize, 0x40000000);
            }
        }
        for (int cz = minChunkZ; cz <= maxChunkZ + 1; cz++) {
            int pz = y + Math.round(map.pixelZ(cz << 4) * factor);
            if (pz >= y && pz < y + scaledSize) {
                g.fill(x, pz, x + scaledSize, pz + 1, 0x40000000);
            }
        }
    }

    /** The waypoint under the mouse (within 4 pixels), or null. */
    public static ClientWaypoints.Entry waypointAt(MapImage map, int x, int y, int scaledSize,
                                                   List<ClientWaypoints.Entry> waypoints, int mouseX, int mouseY) {
        float factor = (float) scaledSize / map.size();
        for (ClientWaypoints.Entry w : waypoints) {
            float px = x + map.pixelX(w.x() + 0.5) * factor;
            float pz = y + map.pixelZ(w.z() + 0.5) * factor;
            if (Math.abs(px - mouseX) <= 4 && Math.abs(pz - 3 - mouseY) <= 5) {
                return w;
            }
        }
        return null;
    }

    /** A little flag: pole from the point upwards with a coloured banner. */
    private static void flag(GuiGraphicsExtractor g, int px, int pz, int color) {
        g.fill(px, pz - 7, px + 1, pz + 1, 0xFF2A1E12);
        g.fill(px + 1, pz - 7, px + 6, pz - 3, 0xFF2A1E12);
        g.fill(px + 1, pz - 6, px + 5, pz - 4, color);
    }

    private static void dot(GuiGraphicsExtractor g, MapImage map, int x, int y, int scaledSize,
                            double worldX, double worldZ, int size, int color) {
        float factor = (float) scaledSize / map.size();
        int px = x + (int) (map.pixelX(worldX) * factor);
        int pz = y + (int) (map.pixelZ(worldZ) * factor);
        if (px < x || pz < y || px >= x + scaledSize || pz >= y + scaledSize) {
            return;
        }
        int half = size / 2;
        g.fill(px - half, pz - half, px - half + size, pz - half + size, color);
    }
}
