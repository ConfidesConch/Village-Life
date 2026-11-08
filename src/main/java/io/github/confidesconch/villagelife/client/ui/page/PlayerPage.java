package io.github.confidesconch.villagelife.client.ui.page;

import java.util.List;
import java.util.UUID;

import io.github.confidesconch.villagelife.book.OpenVillageBookPayload;
import io.github.confidesconch.villagelife.book.VillageBookMenu;
import io.github.confidesconch.villagelife.client.map.ClientFriends;
import io.github.confidesconch.villagelife.client.ui.component.ScrollList;
import io.github.confidesconch.villagelife.client.ui.component.ScrollPanel;
import io.github.confidesconch.villagelife.client.ui.component.TabBar;
import io.github.confidesconch.villagelife.client.ui.core.Ui;
import io.github.confidesconch.villagelife.client.ui.core.UiPage;
import io.github.confidesconch.villagelife.client.ui.core.UiRect;
import io.github.confidesconch.villagelife.client.ui.core.UiTheme;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * "Player" of the village window (M21b10a) – the book's character and friends chapters: tab "Inventory"
 * (your figure and your real inventory, owner's choice "3b", plus your numbers), "My villages" (all
 * villages you own or manage; a click opens one) and "Friends" (invites, friends, invite players).
 * Works without a village.
 */
public final class PlayerPage implements UiPage {
    /** The figure needs at least this much height. */
    private static final int MIN_FIGURE = 60;
    private static final int TABS = 20;
    private static final int ROW = 24;

    private final ScrollList villages = new ScrollList();
    private final ScrollPanel friends = new ScrollPanel();
    private int tab;
    private UUID confirmRemove;

    @Override
    public String id() {
        return "player";
    }

    @Override
    public ItemStack icon() {
        return new ItemStack(Items.ARMOR_STAND);
    }

    @Override
    public Component title() {
        return Component.translatable("villagelife.ui.page.player");
    }

    @Override
    public int order() {
        return 90;
    }

    @Override
    public boolean needsVillage() {
        return false;
    }

    @Override
    public List<String> sections() {
        return List.of("character");
    }

    @Override
    public void onOpen() {
        confirmRemove = null;
        ClientFriends.refresh();
    }

    /** "player:villages", "player:friends". */
    @Override
    public void focus(String hint) {
        tab = hint.equals("villages") ? 1 : hint.equals("friends") ? 2 : 0;
    }

    /** The inventory fills the bottom middle of the left panel, the figure the space above it – if there is room. */
    @Override
    public int[] inventoryOrigin(UiRect area) {
        if (tab != 0) {
            return null;
        }
        return origin(slotPanel(area.below(TABS)));
    }

    /** The left panel of the inventory tab (the numbers go to the right of it). */
    private static UiRect slotPanel(UiRect body) {
        int side = body.w() >= 360 ? Math.min(180, body.w() * 34 / 100) : 0;
        return side == 0 ? body : body.left(body.w() - side - 6);
    }

    /** Corner of the slot block: centred, at the bottom of the panel; null if the panel is too small. */
    private static int[] origin(UiRect panel) {
        if (panel.w() < VillageBookMenu.LAYOUT_WIDTH + 16 || panel.h() < VillageBookMenu.LAYOUT_HEIGHT + MIN_FIGURE + 50) {
            return null;
        }
        return new int[]{panel.x() + (panel.w() - VillageBookMenu.LAYOUT_WIDTH) / 2, panel.bottom() - 12 - VillageBookMenu.LAYOUT_HEIGHT};
    }

    @Override
    public void render(Ui ui, UiRect area, CompoundTag data) {
        UiRect body = TabBar.render(ui, area, List.of(Component.translatable("villagelife.ui.player.tab.inventory"),
                Component.translatable("villagelife.book.character.villages"), Component.translatable("villagelife.book.friends")),
                tab, i -> {
                    tab = i;
                    confirmRemove = null;
                    if (i == 2) {
                        ClientFriends.refresh();
                    }
                });
        CompoundTag c = data.getCompoundOrEmpty("character");
        switch (tab) {
            case 1 -> villageList(ui, body, c.getListOrEmpty("villages"));
            case 2 -> PlayerFriends.render(ui, body, friends, this);
            default -> inventory(ui, body, c);
        }
    }

    UUID confirmRemove() {
        return confirmRemove;
    }

    void setConfirmRemove(UUID id) {
        confirmRemove = id;
    }

    // ---- inventory -----------------------------------------------------------------------------

    private static void inventory(Ui ui, UiRect body, CompoundTag c) {
        Player player = Minecraft.getInstance().player;
        UiRect panel = slotPanel(body);
        UiTheme.draw(ui.graphics(), UiTheme.PANEL, panel);
        int[] origin = origin(panel);
        if (origin == null) {
            UiRect inner = panel.inset(10);
            ui.paragraph(Component.translatable("villagelife.ui.player.too_small"), inner.x(), inner.y(), inner.w(), UiTheme.INK_DIM);
        } else if (player != null) {
            int x = origin[0];
            int y = origin[1];
            Component name = Component.literal(player.getName().getString());
            ui.text(name, new UiRect(panel.x() + 8, panel.y() + 6, panel.w() - 16, 10), UiTheme.INK, 0);
            // the figure: as big as the room above the equipment allows, in the middle
            int figureTop = panel.y() + 20;
            int figureBottom = y - 18;
            int height = figureBottom - figureTop;
            int width = Math.min(panel.w() - 20, height * 3 / 4);
            int cx = panel.x() + panel.w() / 2;
            ui.graphics().fill(cx - width / 2, figureBottom - 2, cx + width / 2, figureBottom - 1, 0x33705A3A); // ground line
            InventoryScreen.extractEntityInInventoryFollowsMouse(ui.graphics(), cx - width / 2, figureTop, cx + width / 2, figureBottom,
                    Math.max(20, height * 2 / 5), 0.0625F, ui.mouseX(), ui.mouseY(), player);
            ui.text(Component.translatable("villagelife.ui.player.equipment"), x, y - 11, UiTheme.INK_DIM, VillageBookMenu.LAYOUT_WIDTH);
            for (int i = 0; i < 4; i++) {
                slotBack(ui, x + VillageBookMenu.ARMOR_X + i * VillageBookMenu.ARMOR_STEP, y + VillageBookMenu.ARMOR_Y);
            }
            slotBack(ui, x + VillageBookMenu.OFFHAND_X, y + VillageBookMenu.OFFHAND_Y);
            for (int row = 0; row < 4; row++) {
                for (int col = 0; col < 9; col++) {
                    int sy = VillageBookMenu.INVENTORY_Y + (row < 3 ? row * 18 : 58);
                    slotBack(ui, x + VillageBookMenu.INVENTORY_X + col * 18, y + sy);
                }
            }
        }
        if (panel.w() == body.w()) {
            return;
        }
        UiRect side = body.rightOf(panel.w() + 6);
        UiTheme.draw(ui.graphics(), UiTheme.PANEL_DARK, side);
        UiRect inner = side.inset(8);
        ui.text(Component.translatable("villagelife.book.character.numbers"), inner.x(), inner.y(), UiTheme.GOLD, inner.w());
        int y = inner.y() + 16;
        y = number(ui, inner, y, "villagelife.book.character.claims", c.getIntOr("claims_general", 0) + "/" + c.getIntOr("claims_general_limit", 0));
        y = number(ui, inner, y, "villagelife.book.character.claims_village", c.getIntOr("claims_village", 0) + "/" + c.getIntOr("claims_village_limit", 0));
        y = number(ui, inner, y, "villagelife.book.character.waypoints", c.getIntOr("waypoints", 0) + "/" + c.getIntOr("waypoints_limit", 0));
        y = number(ui, inner, y, "villagelife.book.character.friends", c.getIntOr("friends", 0) + "/" + c.getIntOr("friends_limit", 0));
        y = number(ui, inner, y, "villagelife.book.character.residents", String.valueOf(c.getIntOr("residents", 0)));
        y += 8;
        ui.text(Component.translatable("villagelife.book.character.reputation"), inner.x(), y, UiTheme.GOLD, inner.w());
        ui.paragraph(Component.translatable("villagelife.book.character.later"), inner.x(), y + 14, inner.w(), UiTheme.TEXT_DIM);
    }

    private static void slotBack(Ui ui, int x, int y) {
        UiTheme.draw(ui.graphics(), UiTheme.SLOT, new UiRect(x - 1, y - 1, 18, 18));
    }

    private static int number(Ui ui, UiRect r, int y, String key, String value) {
        int valueWidth = ui.font().width(value);
        ui.text(Component.translatable(key), r.x(), y, UiTheme.TEXT_DIM, r.w() - valueWidth - 6);
        ui.text(Component.literal(value), r.right() - valueWidth, y, UiTheme.TEXT, valueWidth);
        return y + 12;
    }

    // ---- my villages ---------------------------------------------------------------------------

    private void villageList(Ui ui, UiRect body, ListTag list) {
        UiTheme.draw(ui.graphics(), UiTheme.PANEL_DARK, body);
        UiRect rows = body.inset(6);
        if (list.isEmpty()) {
            ui.paragraph(Component.translatable("villagelife.book.character.no_villages"), rows.x(), rows.y(), rows.w(), UiTheme.TEXT_DIM);
            return;
        }
        villages.render(ui, rows, list.size(), ROW, (u, index, row) -> {
            CompoundTag v = list.getCompoundOrEmpty(index);
            boolean here = v.getBooleanOr("here", false);
            if (here && u.hovered(row)) {
                u.graphics().fill(row.x(), row.y(), row.right(), row.bottom(), 0x25E7C66B);
            }
            u.item(new ItemStack(v.getBooleanOr("owner", false) ? Items.BELL : Items.WRITABLE_BOOK), row.x() + 2, row.y() + 4);
            u.text(EconomyPage.villageName(v), row.x() + 24, row.y() + 2, here ? UiTheme.GOLD : UiTheme.TEXT, row.w() - 30);
            int satisfaction = v.getIntOr("satisfaction", -1);
            Component details = Component.translatable("villagelife.book.character.village_line",
                    Component.translatable(v.getBooleanOr("owner", false) ? "villagelife.book.character.role_owner"
                            : "villagelife.book.character.role_manager"),
                    v.getIntOr("residents", 0), satisfaction < 0 ? "–" : satisfaction + "%", where(v));
            u.text(details, row.x() + 24, row.y() + 13, UiTheme.TEXT_DIM, row.w() - 30);
            if (here) {
                String id = v.getStringOr("id", "");
                u.tooltip(row, Component.translatable("villagelife.ui.player.open_village"));
                u.clickable(row, () -> ClientPacketDistributor.sendToServer(new OpenVillageBookPayload(id, true)));
            }
        });
    }

    private static String where(CompoundTag v) {
        Player player = Minecraft.getInstance().player;
        if (!v.getBooleanOr("here", false) || player == null) {
            String dimension = v.getStringOr("dimension", "");
            String path = dimension.substring(dimension.indexOf(':') + 1);
            return path.equals("the_nether") ? "Nether" : path.equals("the_end") ? "End" : path;
        }
        double dx = v.getIntOr("x", 0) + 0.5 - player.getX();
        double dz = v.getIntOr("z", 0) + 0.5 - player.getZ();
        return Math.round(Math.sqrt(dx * dx + dz * dz)) + " m";
    }

    // ---- input ---------------------------------------------------------------------------------

    @Override
    public boolean mouseScrolled(double x, double y, double amount) {
        return villages.mouseScrolled(x, y, amount) || friends.mouseScrolled(x, y, amount);
    }

    @Override
    public boolean mouseDragged(double x, double y) {
        return villages.mouseDragged(x, y) || friends.mouseDragged(x, y);
    }

    @Override
    public void mouseReleased() {
        villages.mouseReleased();
        friends.mouseReleased();
    }
}
