package io.github.confidesconch.villagelife.client.ui.page;

import java.util.List;

import io.github.confidesconch.villagelife.book.EditVillageZonePayload;
import io.github.confidesconch.villagelife.client.book.MarkerEditScreen;
import io.github.confidesconch.villagelife.client.map.ClientClaims;
import io.github.confidesconch.villagelife.client.map.ClientWaypoints;
import io.github.confidesconch.villagelife.client.map.MapImage;
import io.github.confidesconch.villagelife.client.map.MapOverlay;
import io.github.confidesconch.villagelife.client.ui.core.Ui;
import io.github.confidesconch.villagelife.client.ui.core.UiPage;
import io.github.confidesconch.villagelife.client.ui.core.UiRect;
import io.github.confidesconch.villagelife.client.ui.core.UiTheme;
import io.github.confidesconch.villagelife.core.util.MarkerColors;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * "Map" of the village window (M21b8): one big map instead of the book's small one. Drag to move,
 * mouse wheel to zoom, right click for a waypoint. Tools: look, draw a zone (owner and managers),
 * claim chunks. Buttons centre on the village or on the player. Beside it {@link MapSidebar} with
 * waypoints, zones and the legend (or the claims while claiming). Works without a village too.
 */
public final class MapPage implements UiPage {
    /** Largest map picture (one texture pixel per GUI pixel, so the map stays sharp). */
    private static final int MAX_PIXELS = 512;
    /** A full repaint takes this many frames. */
    private static final int REPAINT_FRAMES = 6;
    private static final float MIN_SCALE = 0.25F;
    private static final float MAX_SCALE = 16;
    private static final int TOOLBAR = 18;

    /** What a left click on the map does. */
    enum Tool { LOOK, ZONE, CLAIM }

    private final MapSidebar sidebar = new MapSidebar(this);
    private MapImage map;
    private Tool tool = Tool.LOOK;
    /** Centre of the view (world) and blocks per pixel of the texture; null centre = not set yet. */
    private double[] center;
    private float scale = 1;
    /** Where the map was drawn this frame, and its size on screen. */
    private UiRect mapRect = new UiRect(0, 0, 0, 0);
    /** The square map picture behind {@link #mapRect} (it fills the rectangle, its overhang is cut off). */
    private UiRect square = new UiRect(0, 0, 0, 0);
    /** Mouse pressed on the map: where, the centre then, and whether it was moved (drag) since. */
    private double[] press;
    private double[] pressCenter;
    private boolean moved;
    private int[] zoneCorner;
    private CompoundTag data = new CompoundTag();

    @Override
    public String id() {
        return "map";
    }

    @Override
    public ItemStack icon() {
        return new ItemStack(Items.MAP);
    }

    @Override
    public Component title() {
        return Component.translatable("villagelife.ui.page.map");
    }

    @Override
    public int order() {
        return 70;
    }

    @Override
    public boolean needsVillage() {
        return false;
    }

    @Override
    public List<String> sections() {
        return List.of("zones", "buildings");
    }

    @Override
    public void onOpen() {
        tool = Tool.LOOK;
        zoneCorner = null;
    }

    @Override
    public void render(Ui ui, UiRect area, CompoundTag data) {
        this.data = data;
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (minecraft.level == null || player == null) {
            return;
        }
        CompoundTag overview = data.getCompoundOrEmpty("overview");
        boolean village = overview.contains("radius");
        if (center == null) {
            if (village) {
                centerOnVillage();
            } else {
                center = new double[]{player.getX(), player.getZ()};
            }
        }
        if (!manageZones() && tool == Tool.ZONE) {
            tool = Tool.LOOK;
        }
        int sideWidth = Math.max(130, Math.min(200, area.w() * 34 / 100));
        UiRect left = area.left(area.w() - sideWidth - 6);
        toolbar(ui, left.top(TOOLBAR));
        UiRect mapArea = left.below(TOOLBAR + 4);
        UiTheme.draw(ui.graphics(), UiTheme.PANEL, mapArea.inset(0, 0, 0, 12)); // parchment around the map
        UiRect inner = mapArea.inset(5, 5, 5, 17);
        int w = Math.min(MAX_PIXELS, Math.max(16, inner.w()));
        int h = Math.min(MAX_PIXELS, Math.max(16, inner.h()));
        mapRect = new UiRect(inner.x() + (inner.w() - w) / 2, inner.y() + (inner.h() - h) / 2, w, h);
        int size = Math.max(w, h); // the picture is square; it fills the whole rectangle
        square = new UiRect(mapRect.x() - (size - w) / 2, mapRect.y() - (size - h) / 2, size, size);
        if (map == null || map.size() != size) { // one texture pixel per GUI pixel: sharp at every window size
            if (map != null) {
                minecraft.getTextureManager().release(map.textureId());
            }
            map = new MapImage("window_" + size, size, 0xFFD9C9A3, true);
        }
        map.panTo(minecraft.level, center[0], center[1], scale);
        map.paintRows(minecraft.level, Math.max(8, size / REPAINT_FRAMES));
        paint(ui);
        ui.text(Component.translatable(hint()), new UiRect(mapArea.x(), mapArea.bottom() - 10, mapArea.w(), 10),
                tool == Tool.LOOK ? UiTheme.TEXT_DIM : UiTheme.GOLD, 0);
        sidebar.render(ui, area.rightOf(area.w() - sideWidth), data, tool == Tool.CLAIM);
    }

    private void toolbar(Ui ui, UiRect bar) {
        int x = bar.x();
        x = tool(ui, x, bar, Tool.LOOK, "villagelife.ui.map.tool.look", true);
        x = tool(ui, x, bar, Tool.ZONE, "villagelife.ui.map.tool.zone", manageZones());
        x = tool(ui, x, bar, Tool.CLAIM, "villagelife.ui.map.tool.claim", true);
        x += 6;
        x = small(ui, x, bar, "−", "villagelife.ui.map.zoom_out", scale < MAX_SCALE, () -> zoom(2));
        x = small(ui, x, bar, "+", "villagelife.ui.map.zoom_in", scale > MIN_SCALE, () -> zoom(0.5F));
        x = small(ui, x, bar, "⌂", "villagelife.ui.map.center_village", data.getCompoundOrEmpty("overview").contains("radius"),
                this::centerOnVillage);
        x = small(ui, x, bar, "☺", "villagelife.book.map.center_on_me", true, () -> {
            Player player = Minecraft.getInstance().player;
            if (player != null) {
                center = new double[]{player.getX(), player.getZ()};
            }
        });
        small(ui, x, bar, "⚑", "villagelife.book.map.waypoint_here", true, () -> {
            Player player = Minecraft.getInstance().player;
            if (player != null) {
                newWaypoint(player.getBlockX(), player.getBlockZ());
            }
        });
    }

    private int tool(Ui ui, int x, UiRect bar, Tool target, String key, boolean enabled) {
        Component label = Component.translatable(key);
        UiRect r = new UiRect(x, bar.y(), ui.font().width(label) + 12, 16);
        ui.button(r, label, enabled && tool != target, () -> {
            tool = target;
            zoneCorner = null;
            if (target == Tool.CLAIM) {
                sidebar.showLegend();
            }
        });
        if (tool == target) {
            ui.graphics().fill(r.x() + 2, r.bottom() - 2, r.right() - 2, r.bottom() - 1, UiTheme.GOLD);
        }
        return r.right() + 3;
    }

    private static int small(Ui ui, int x, UiRect bar, String symbol, String tooltipKey, boolean enabled, Runnable action) {
        UiRect r = new UiRect(x, bar.y(), 16, 16);
        if (r.right() > bar.right()) {
            return x;
        }
        ui.button(r, Component.literal(symbol), enabled, action);
        ui.tooltip(r, Component.translatable(tooltipKey));
        return r.right() + 2;
    }

    private void paint(Ui ui) {
        var g = ui.graphics();
        UiRect r = mapRect;
        UiRect sq = square;
        int size = sq.w();
        if (size <= 0 || r.w() <= 0) {
            return;
        }
        g.outline(r.x() - 1, r.y() - 1, r.w() + 2, r.h() + 2, 0xFF5A4630);
        g.blit(RenderPipelines.GUI_TEXTURED, map.textureId(), r.x(), r.y(), r.x() - sq.x(), r.y() - sq.y(), r.w(), r.h(), size, size);
        g.enableScissor(r.x(), r.y(), r.right(), r.bottom());
        MapOverlay.villageBorder(g, map, sq.x(), sq.y(), size, data.getCompoundOrEmpty("overview"));
        MapOverlay.claims(g, map, sq.x(), sq.y(), size, tool == Tool.CLAIM);
        MapOverlay.zones(g, map, sq.x(), sq.y(), size, data.getCompoundOrEmpty("zones"));
        MapOverlay.buildings(g, map, sq.x(), sq.y(), size, data.getCompoundOrEmpty("buildings"));
        MapOverlay.waypoints(g, map, sq.x(), sq.y(), size, ClientWaypoints.shownHere(), false);
        MapOverlay.otherPlayers(g, map, sq.x(), sq.y(), size);
        MapOverlay.residentsAndPlayer(g, map, sq.x(), sq.y(), size);
        int mx = ui.mouseX();
        int my = ui.mouseY();
        boolean inside = ui.hovered(r);
        if (inside) {
            int wx = worldX(mx);
            int wz = worldZ(my);
            if (tool == Tool.ZONE && zoneCorner != null) {
                MapOverlay.rectangle(g, map, sq.x(), sq.y(), size, Math.min(wx, zoneCorner[0]), Math.min(wz, zoneCorner[1]),
                        Math.max(wx, zoneCorner[0]), Math.max(wz, zoneCorner[1]), 0xFFFFFFFF, "");
            } else if (tool == Tool.CLAIM) {
                int cx = (wx >> 4) << 4;
                int cz = (wz >> 4) << 4;
                MapOverlay.rectangle(g, map, sq.x(), sq.y(), size, cx, cz, cx + 15, cz + 15, 0xFFFFFFFF, "");
            }
        }
        g.disableScissor();
        if (inside) {
            ui.tooltip(r, tooltip(mx, my));
        }
        ui.clickable(r, () -> {
            press = new double[]{mx, my};
            pressCenter = center.clone();
            moved = false;
        }, () -> newWaypoint(worldX(mx), worldZ(my)));
    }

    private Component tooltip(int mx, int my) {
        int wx = worldX(mx);
        int wz = worldZ(my);
        if (tool == Tool.CLAIM) {
            ClientClaims.Entry claim = ClientClaims.at(wx >> 4, wz >> 4);
            Component state = claim == null ? Component.translatable("villagelife.book.map.claim_free")
                    : ClientClaims.isMine(claim) ? Component.translatable("villagelife.book.map.claim_mine")
                    : Component.translatable("villagelife.book.map.claim_of", claim.name());
            return Component.translatable("villagelife.book.map.claim_chunk", wx >> 4, wz >> 4, state);
        }
        int size = square.w();
        String player = MapOverlay.playerAt(map, square.x(), square.y(), size, mx, my);
        if (player != null) {
            return Component.literal(player);
        }
        ClientWaypoints.Entry w = MapOverlay.waypointAt(map, square.x(), square.y(), size, ClientWaypoints.shownHere(), mx, my);
        if (w != null) {
            return Component.literal(w.name() + (w.isOwn() ? "" : " – " + w.owner()) + " (" + w.x() + ", " + w.y() + ", " + w.z() + ")");
        }
        return Component.literal(wx + ", " + wz);
    }

    private String hint() {
        return switch (tool) {
            case ZONE -> zoneCorner == null ? "villagelife.book.map.zone_first" : "villagelife.book.map.zone_second";
            case CLAIM -> "villagelife.book.map.claim_hint";
            default -> "villagelife.ui.map.hint";
        };
    }

    /** A click without dragging: what the tool does there. */
    private void click(int wx, int wz) {
        switch (tool) {
            case CLAIM -> {
                ClientClaims.Entry claim = ClientClaims.at(wx >> 4, wz >> 4);
                boolean free = claim == null;
                ClientClaims.send(free ? "claim" : (ClientClaims.isMine(claim) || ClientClaims.admin() ? "unclaim" : "claim"),
                        wx >> 4, wz >> 4, "");
            }
            case ZONE -> {
                if (zoneCorner == null) {
                    zoneCorner = new int[]{wx, wz};
                    return;
                }
                int[] first = zoneCorner;
                zoneCorner = null;
                tool = Tool.LOOK;
                String villageId = data.getStringOr("village_id", "");
                Component size = Component.translatable("villagelife.book.map.zone_size", Math.abs(wx - first[0]) + 1, Math.abs(wz - first[1]) + 1);
                openEditor(Component.translatable("villagelife.marker.new_zone"), size, "", 5, null, (name, color, shared) ->
                        ClientPacketDistributor.sendToServer(new EditVillageZonePayload(villageId, "add", 0, name, color,
                                first[0], first[1], wx, wz)), null);
            }
            default -> { }
        }
    }

    /** Opens the dialog for a new waypoint at x/z (height from the loaded terrain, else the player's). */
    private void newWaypoint(int x, int z) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }
        if (ClientWaypoints.own().size() >= ClientWaypoints.limit()) {
            minecraft.player.sendOverlayMessage(Component.translatable("villagelife.waypoint.limit", ClientWaypoints.limit()));
            return;
        }
        int y = minecraft.level.hasChunk(x >> 4, z >> 4) ? minecraft.level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z)
                : minecraft.player.getBlockY();
        int color = ClientWaypoints.own().size() % MarkerColors.COUNT;
        openEditor(Component.translatable("villagelife.marker.new_waypoint"), Component.literal(x + ", " + y + ", " + z), "", color, null,
                (name, chosen, shared) -> ClientWaypoints.send("add", 0, name, chosen, x, y, z, false), null);
    }

    static void openEditor(Component title, Component subtitle, String name, int color, Boolean shared,
                           MarkerEditScreen.Result onDone, Runnable onDelete) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.gui.setScreen(new MarkerEditScreen(minecraft.gui.screen(), title, subtitle, name, color, shared, onDone, onDelete));
    }

    // ---- view ----------------------------------------------------------------------------------

    /** The whole village fits the map. */
    void centerOnVillage() {
        CompoundTag overview = data.getCompoundOrEmpty("overview");
        if (!overview.contains("radius")) {
            return;
        }
        center = new double[]{overview.getIntOr("center_x", 0) + 0.5, overview.getIntOr("center_z", 0) + 0.5};
        int pixels = mapRect.w() > 0 ? Math.min(mapRect.w(), mapRect.h()) : 192; // the village fits the shorter side
        scale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, (2F * overview.getIntOr("radius", 64) + 16) / pixels));
    }

    /** Centres the view on a world position (e.g. a waypoint from the list). */
    void centerOn(double x, double z) {
        center = new double[]{x, z};
    }

    boolean manageZones() {
        return data.getCompoundOrEmpty("zones").getBooleanOr("can_manage", false) && data.getCompoundOrEmpty("overview").contains("radius");
    }

    private void zoom(float factor) {
        scale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, scale * factor));
    }

    /** Blocks per screen pixel of the drawn map. */
    private double screenScale() {
        return scale; // one texture pixel per GUI pixel
    }

    private int worldX(double screenX) {
        return (int) Math.floor(center[0] + (screenX - square.x() - square.w() / 2.0) * screenScale());
    }

    private int worldZ(double screenY) {
        return (int) Math.floor(center[1] + (screenY - square.y() - square.h() / 2.0) * screenScale());
    }

    // ---- input ---------------------------------------------------------------------------------

    @Override
    public boolean mouseScrolled(double x, double y, double amount) {
        if (mapRect.contains(x, y) && amount != 0) {
            zoom(amount > 0 ? 0.7071F : 1.4142F); // finer steps with the wheel
            return true;
        }
        return sidebar.mouseScrolled(x, y, amount);
    }

    @Override
    public boolean mouseDragged(double x, double y) {
        if (press != null) {
            double dx = x - press[0];
            double dy = y - press[1];
            if (moved || Math.abs(dx) + Math.abs(dy) > 3) {
                moved = true;
                center = new double[]{pressCenter[0] - dx * screenScale(), pressCenter[1] - dy * screenScale()};
            }
            return true;
        }
        return sidebar.mouseDragged(x, y);
    }

    @Override
    public void mouseReleased() {
        if (press != null && !moved) {
            click(worldX(press[0]), worldZ(press[1]));
        }
        press = null;
        sidebar.mouseReleased();
    }
}
