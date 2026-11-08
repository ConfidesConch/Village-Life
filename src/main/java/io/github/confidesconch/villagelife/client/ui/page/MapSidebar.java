package io.github.confidesconch.villagelife.client.ui.page;

import java.util.Comparator;
import java.util.List;

import io.github.confidesconch.villagelife.book.EditVillageZonePayload;
import io.github.confidesconch.villagelife.client.map.ClientClaims;
import io.github.confidesconch.villagelife.client.map.ClientWaypoints;
import io.github.confidesconch.villagelife.client.map.MapOverlay;
import io.github.confidesconch.villagelife.client.ui.component.ScrollList;
import io.github.confidesconch.villagelife.client.ui.component.TabBar;
import io.github.confidesconch.villagelife.client.ui.core.Ui;
import io.github.confidesconch.villagelife.client.ui.core.UiRect;
import io.github.confidesconch.villagelife.client.ui.core.UiTheme;
import io.github.confidesconch.villagelife.core.util.MarkerColors;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * Beside the map (M21b8, the book's "Waypoints &amp; zones" chapter since M11e): tab "Waypoints" (show/hide,
 * edit, a click centres the map), tab "Zones" (owner and managers edit) and tab "Legend" – while claiming
 * it shows the claims (M11f) instead.
 */
final class MapSidebar {
    private static final int ROW = 14;

    private final MapPage page;
    private final ScrollList waypoints = new ScrollList();
    private final ScrollList zones = new ScrollList();
    private int tab;

    MapSidebar(MapPage page) {
        this.page = page;
    }

    void showLegend() {
        tab = 2;
    }

    void render(Ui ui, UiRect area, CompoundTag data, boolean claiming) {
        UiTheme.draw(ui.graphics(), UiTheme.PANEL_DARK, area);
        UiRect inner = area.inset(5);
        UiRect body = TabBar.render(ui, inner, List.of(Component.translatable("villagelife.ui.map.tab.waypoints"),
                Component.translatable("villagelife.ui.map.tab.zones"),
                Component.translatable(claiming ? "villagelife.book.map.claims" : "villagelife.book.map.legend")), tab, i -> tab = i);
        switch (tab) {
            case 1 -> zones(ui, body, data);
            case 2 -> {
                if (claiming) {
                    claims(ui, body, data);
                } else {
                    legend(ui, body);
                }
            }
            default -> waypoints(ui, body);
        }
    }

    // ---- waypoints -----------------------------------------------------------------------------

    private void waypoints(Ui ui, UiRect r) {
        ui.text(Component.translatable("villagelife.book.markers.waypoint_count", ClientWaypoints.own().size(), ClientWaypoints.limit()),
                r.x(), r.y(), UiTheme.TEXT_DIM, r.w());
        List<ClientWaypoints.Entry> list = sorted();
        UiRect rows = r.below(12);
        if (list.isEmpty()) {
            ui.paragraph(Component.translatable("villagelife.ui.map.no_waypoints"), rows.x(), rows.y() + 2, rows.w(), UiTheme.TEXT_DIM);
            return;
        }
        String here = here();
        waypoints.render(ui, rows, list.size(), ROW, (u, index, row) -> {
            ClientWaypoints.Entry w = list.get(index);
            boolean sameWorld = w.dimension().equals(here);
            if (u.hovered(row)) {
                u.graphics().fill(row.x(), row.y(), row.right(), row.bottom(), 0x25E7C66B);
            }
            u.graphics().fill(row.x() + 1, row.y() + 4, row.x() + 7, row.y() + 10, w.visible() ? MarkerColors.argb(w.color()) : 0xFF555555);
            Component where = Component.literal(where(w, sameWorld));
            int whereWidth = u.font().width(where);
            int buttons = w.isOwn() ? 26 : 0;
            String label = w.isOwn() ? (w.shared() ? w.name() + " ⇄" : w.name()) : w.name() + " (" + w.owner() + ")";
            u.text(Component.literal(label), row.inset(10, 0, whereWidth + buttons + 6, 0), w.visible() ? UiTheme.TEXT : UiTheme.TEXT_DIM, -1);
            u.text(where, row.inset(0, 0, buttons + 2, 0), UiTheme.TEXT_DIM, 1);
            if (sameWorld) {
                u.clickable(row, () -> page.centerOn(w.x() + 0.5, w.z() + 0.5));
            }
            if (w.isOwn()) {
                UiRect toggle = new UiRect(row.right() - 24, row.y() + 1, 11, 12);
                UiRect edit = new UiRect(row.right() - 12, row.y() + 1, 11, 12);
                u.text(Component.literal(w.visible() ? "●" : "○"), toggle, u.hovered(toggle) ? UiTheme.GOLD : UiTheme.TEXT_DIM, 0);
                u.text(Component.literal("✎"), edit, u.hovered(edit) ? UiTheme.GOLD : UiTheme.TEXT_DIM, 0);
                u.tooltip(toggle, Component.translatable("villagelife.ui.map.toggle"));
                u.tooltip(edit, Component.translatable("villagelife.ui.map.edit"));
                u.clickable(toggle, () -> ClientWaypoints.send("toggle", w.id(), "", 0, 0, 0, 0, false));
                u.clickable(edit, () -> MapPage.openEditor(Component.translatable("villagelife.marker.edit_waypoint"),
                        Component.literal(w.x() + ", " + w.y() + ", " + w.z()), w.name(), w.color(), w.shared(),
                        (name, chosen, shared) -> ClientWaypoints.send("edit", w.id(), name, chosen, 0, 0, 0, shared),
                        () -> ClientWaypoints.send("delete", w.id(), "", 0, 0, 0, 0, false)));
            }
        });
    }

    private static String here() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level == null ? "" : minecraft.level.dimension().identifier().toString();
    }

    /** Distance in this dimension, otherwise the dimension's name. */
    private static String where(ClientWaypoints.Entry w, boolean sameWorld) {
        Player player = Minecraft.getInstance().player;
        if (player == null) {
            return "";
        }
        if (!sameWorld) {
            String path = w.dimension().substring(w.dimension().indexOf(':') + 1);
            return path.equals("the_nether") ? "Nether" : path.equals("the_end") ? "End" : path;
        }
        double dx = w.x() + 0.5 - player.getX();
        double dz = w.z() + 0.5 - player.getZ();
        int meters = (int) Math.round(Math.sqrt(dx * dx + dz * dz));
        return meters >= 10000 ? (meters / 1000) + " km" : meters + " m";
    }

    /** Own first, this dimension first, nearest first; others after, by name. */
    private static List<ClientWaypoints.Entry> sorted() {
        Player player = Minecraft.getInstance().player;
        String here = here();
        return ClientWaypoints.all().stream().sorted(Comparator
                .comparing((ClientWaypoints.Entry w) -> !w.isOwn())
                .thenComparing((ClientWaypoints.Entry w) -> !w.dimension().equals(here))
                .thenComparingDouble(w -> player == null || !w.dimension().equals(here) ? 0
                        : player.distanceToSqr(w.x() + 0.5, player.getY(), w.z() + 0.5))
                .thenComparing(ClientWaypoints.Entry::name)).toList();
    }

    // ---- zones ---------------------------------------------------------------------------------

    private void zones(Ui ui, UiRect r, CompoundTag data) {
        if (data.getBooleanOr("none", false)) {
            ui.paragraph(Component.translatable("villagelife.book.markers.zones_no_village"), r.x(), r.y(), r.w(), UiTheme.TEXT_DIM);
            return;
        }
        CompoundTag section = data.getCompoundOrEmpty("zones");
        ListTag list = section.getListOrEmpty("list");
        boolean manage = section.getBooleanOr("can_manage", false);
        ui.text(Component.translatable("villagelife.book.markers.zone_count", list.size(), section.getIntOr("limit", 0)),
                r.x(), r.y(), UiTheme.TEXT_DIM, r.w());
        UiRect rows = r.below(12);
        if (list.isEmpty()) {
            ui.paragraph(Component.translatable(manage ? "villagelife.ui.map.no_zones_manage" : "villagelife.book.markers.no_zones"),
                    rows.x(), rows.y() + 2, rows.w(), UiTheme.TEXT_DIM);
            return;
        }
        String villageId = data.getStringOr("village_id", "");
        zones.render(ui, rows, list.size(), ROW, (u, index, row) -> {
            CompoundTag z = list.getCompoundOrEmpty(index);
            int id = z.getIntOr("id", 0);
            String name = z.getStringOr("name", "?");
            int color = z.getIntOr("color", 0);
            int minX = z.getIntOr("min_x", 0);
            int minZ = z.getIntOr("min_z", 0);
            int maxX = z.getIntOr("max_x", 0);
            int maxZ = z.getIntOr("max_z", 0);
            if (u.hovered(row)) {
                u.graphics().fill(row.x(), row.y(), row.right(), row.bottom(), 0x25E7C66B);
            }
            u.graphics().fill(row.x() + 1, row.y() + 4, row.x() + 7, row.y() + 10, MarkerColors.argb(color));
            Component size = Component.literal((maxX - minX + 1) + "×" + (maxZ - minZ + 1));
            int sizeWidth = u.font().width(size);
            int edit = manage ? 12 : 0;
            u.text(Component.literal(name), row.inset(10, 0, sizeWidth + edit + 6, 0), UiTheme.TEXT, -1);
            u.text(size, row.inset(0, 0, edit + 2, 0), UiTheme.TEXT_DIM, 1);
            u.clickable(row, () -> page.centerOn((minX + maxX + 1) / 2.0, (minZ + maxZ + 1) / 2.0));
            if (manage) {
                UiRect pen = new UiRect(row.right() - 12, row.y() + 1, 11, 12);
                u.text(Component.literal("✎"), pen, u.hovered(pen) ? UiTheme.GOLD : UiTheme.TEXT_DIM, 0);
                u.tooltip(pen, Component.translatable("villagelife.ui.map.edit"));
                u.clickable(pen, () -> MapPage.openEditor(Component.translatable("villagelife.marker.edit_zone"), size, name, color, null,
                        (newName, chosen, shared) -> ClientPacketDistributor.sendToServer(
                                new EditVillageZonePayload(villageId, "edit", id, newName, chosen, 0, 0, 0, 0)),
                        () -> ClientPacketDistributor.sendToServer(new EditVillageZonePayload(villageId, "delete", id, "", 0, 0, 0, 0, 0))));
            }
        });
    }

    // ---- legend & claims -----------------------------------------------------------------------

    private static void legend(Ui ui, UiRect r) {
        int[][] colors = {{MapOverlay.PLAYER, MapOverlay.RESIDENT}, {MapOverlay.HOUSE, MapOverlay.STORAGE},
                {MapOverlay.WORKPLACE, MapOverlay.INVALID}, {MapOverlay.FIELD, MapOverlay.PASTURE},
                {MapOverlay.BORDER, MarkerColors.argb(3)},
                {MapOverlay.FRIEND, MapOverlay.OTHER_PLAYER}};
        String[][] keys = {{"villagelife.book.map.legend.player", "villagelife.book.map.legend.resident"},
                {"villagelife.building_type.house", "villagelife.building_type.storage"},
                {"villagelife.building_type.workplace", "villagelife.book.map.legend.invalid"},
                {"villagelife.building_type.field", "villagelife.building_type.pasture"},
                {"villagelife.book.map.legend.border", "villagelife.book.map.legend.waypoint"},
                {"villagelife.book.map.legend.friend", "villagelife.book.map.legend.other_player"}};
        int y = r.y();
        for (int i = 0; i < colors.length; i++) {
            for (int j = 0; j < 2; j++) {
                y = entry(ui, r.x(), y, r.w(), colors[i][j], keys[i][j]);
            }
        }
        ui.paragraph(Component.translatable("villagelife.ui.map.controls"), r.x(), y + 6, r.w(), UiTheme.TEXT_DIM);
    }

    private static int entry(Ui ui, int x, int y, int w, int color, String key) {
        ui.graphics().fill(x + 1, y + 1, x + 7, y + 7, color);
        ui.graphics().outline(x, y, 8, 8, 0xFF000000);
        ui.text(Component.translatable(key), x + 12, y, UiTheme.TEXT, w - 12);
        return y + 11;
    }

    /** While claiming: counters, "claim the whole village" and the colours. */
    private static void claims(Ui ui, UiRect r, CompoundTag data) {
        int general = ClientClaims.outside() + Math.max(0, ClientClaims.inVillage() - ClientClaims.limitVillage());
        int y = r.y();
        y += ui.paragraph(Component.translatable("villagelife.book.map.claim_hint"), r.x(), y, r.w(), UiTheme.TEXT_DIM) + 4;
        y = counter(ui, r, y, "villagelife.book.map.claims_general", general + "/" + ClientClaims.limitOutside(),
                general >= ClientClaims.limitOutside() ? UiTheme.BAD : UiTheme.TEXT);
        y = counter(ui, r, y, "villagelife.book.map.claims_village",
                Math.min(ClientClaims.inVillage(), ClientClaims.limitVillage()) + "/" + ClientClaims.limitVillage(), UiTheme.TEXT);
        if (data.getCompoundOrEmpty("zones").getBooleanOr("can_manage", false) && data.contains("village_id")) {
            String villageId = data.getStringOr("village_id", "");
            ui.button(new UiRect(r.x(), y + 2, r.w(), 16), Component.translatable("villagelife.book.map.claim_village"), true,
                    () -> ClientClaims.send("claim_village", 0, 0, villageId));
            y += 22;
        }
        y = entry(ui, r.x(), y + 4, r.w(), MapOverlay.CLAIM_OWN, "villagelife.book.map.legend.claim_own");
        entry(ui, r.x(), y, r.w(), MapOverlay.CLAIM_OTHER, "villagelife.book.map.legend.claim_other");
    }

    private static int counter(Ui ui, UiRect r, int y, String key, String value, int color) {
        int valueWidth = ui.font().width(value);
        ui.text(Component.translatable(key), r.x(), y, UiTheme.TEXT_DIM, r.w() - valueWidth - 6);
        ui.text(Component.literal(value), r.right() - valueWidth, y, color, valueWidth);
        return y + 11;
    }

    boolean mouseScrolled(double x, double y, double amount) {
        return waypoints.mouseScrolled(x, y, amount) || zones.mouseScrolled(x, y, amount);
    }

    boolean mouseDragged(double x, double y) {
        return waypoints.mouseDragged(x, y) || zones.mouseDragged(x, y);
    }

    void mouseReleased() {
        waypoints.mouseReleased();
        zones.mouseReleased();
    }
}
