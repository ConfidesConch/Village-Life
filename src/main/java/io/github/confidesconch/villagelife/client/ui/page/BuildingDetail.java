package io.github.confidesconch.villagelife.client.ui.page;

import java.util.ArrayList;
import java.util.List;

import io.github.confidesconch.villagelife.book.BuildingActionPayload;
import io.github.confidesconch.villagelife.client.ui.component.ScrollPanel;
import io.github.confidesconch.villagelife.client.ui.core.Ui;
import io.github.confidesconch.villagelife.client.ui.core.UiRect;
import io.github.confidesconch.villagelife.client.ui.core.UiTheme;
import io.github.confidesconch.villagelife.resident.Job;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * The chosen building in "Buildings" (M21b5), on parchment and scrollable: state (or why it is not
 * valid), type (changeable by owner and managers), what the room has, work area, who lives and works
 * there (a click opens the resident) and the contents of its containers. Same actions as the book.
 */
final class BuildingDetail {
    private static final int LINE = 11;
    private static final String[] TYPES = {"house", "storage", "workplace", "field", "pasture"};

    private final ScrollPanel scroll = new ScrollPanel();
    private String shownId;

    void render(Ui ui, UiRect area, CompoundTag b, CompoundTag section, String villageId) {
        UiTheme.draw(ui.graphics(), UiTheme.PANEL, area);
        UiRect inner = area.inset(10, 8, 6, 8);
        if (b == null) {
            ui.text(Component.translatable("villagelife.ui.buildings.pick"), inner.x(), inner.y(), UiTheme.INK_DIM, inner.w());
            return;
        }
        String id = b.getStringOr("id", "");
        if (!id.equals(shownId)) {
            shownId = id;
            scroll.reset();
        }
        boolean manage = section.getBooleanOr("can_manage", false);
        int x = inner.x();
        int w = ScrollPanel.contentWidth(inner);
        int top = scroll.begin(ui, inner);
        int y = top;
        ui.text(BuildingsPage.name(b), x, y, UiTheme.INK, w);
        y += LINE;
        ui.text(Component.translatable("villagelife.book.building.at", b.getStringOr("pos", "")), x, y, UiTheme.INK_DIM, w);
        y += LINE + 2;
        Component state = b.getBooleanOr("valid", false) ? Component.translatable("villagelife.book.building.valid")
                : Component.translatable(b.getStringOr("problem", ""), b.getIntOr("problem_value", 0));
        y += ui.paragraph(state, x, y, w, b.getBooleanOr("valid", false) ? UiTheme.GOOD_INK : UiTheme.BAD_INK) + 4;
        y = types(ui, x, y, w, b, manage, villageId, id);
        if (manage) {
            ui.button(new UiRect(x, y, Math.min(w, 120), 16), Component.translatable("villagelife.book.building.rescan"), true,
                    () -> send(villageId, id, "rescan"));
            ui.tooltip(new UiRect(x, y, Math.min(w, 120), 16), Component.translatable("villagelife.ui.buildings.rescan_tip"));
            y += 20;
        }
        y = facts(ui, x, y + 2, w, b);
        y = workArea(ui, x, y, w, b, section, manage, villageId, id);
        y = people(ui, x, y, w, b.getListOrEmpty("residents"), "villagelife.book.building.lives_here");
        y = people(ui, x, y, w, b.getListOrEmpty("workers"), "villagelife.book.building.works_here");
        if (b.getIntOr("containers", 0) > 0) {
            y = contents(ui, x, y, w, id, section.getCompoundOrEmpty("contents"));
        }
        scroll.end(ui, y - top);
    }

    /** Type buttons, three per row (M21d: field and pasture below): the current type is gold; others change it. */
    private static int types(Ui ui, int x, int y, int w, CompoundTag b, boolean manage, String villageId, String id) {
        String current = b.getStringOr("type_id", "");
        int buttonWidth = (w - 8) / 3;
        for (int i = 0; i < TYPES.length; i++) {
            String type = TYPES[i];
            UiRect r = new UiRect(x + i % 3 * (buttonWidth + 4), y + i / 3 * 20, buttonWidth, 16);
            boolean isCurrent = type.equals(current);
            ui.button(r, Component.translatable("villagelife.building_type." + type), manage && !isCurrent,
                    () -> send(villageId, id, "type:" + type));
            if (isCurrent) {
                ui.graphics().fill(r.x() + 2, r.bottom() - 2, r.right() - 2, r.bottom() - 1, UiTheme.GOLD);
            }
        }
        return y + 20 * ((TYPES.length + 2) / 3);
    }

    private static int facts(Ui ui, int x, int y, int w, CompoundTag b) {
        String type = b.getStringOr("type_id", "");
        if (type.equals("field") || type.equals("pasture")) { // M21d: an open area has only a size
            y = row(ui, x, y, w, "area", String.valueOf(b.getIntOr("volume", 0)));
            y += ui.paragraph(Component.translatable("villagelife.book.building." + type + "_tip"), x, y + 2, w, UiTheme.INK_DIM);
            return y + 6;
        }
        y = row(ui, x, y, w, "volume", String.valueOf(b.getIntOr("volume", 0)));
        y = row(ui, x, y, w, "beds", String.valueOf(b.getIntOr("beds", 0)));
        y = row(ui, x, y, w, "doors", String.valueOf(b.getIntOr("doors", 0)));
        y = row(ui, x, y, w, "containers", String.valueOf(b.getIntOr("containers", 0)));
        y = row(ui, x, y, w, "workstations", String.valueOf(b.getIntOr("workstations", 0)));
        String profession = b.getStringOr("profession", "");
        if (!profession.isEmpty()) {
            y += 2 + ui.paragraph(Component.translatable("villagelife.book.building.workplace_of",
                    Component.translatable(Job.translationKey(profession))), x, y + 2, w, UiTheme.INK);
        }
        if (b.getIntOr("places", 0) > 0) {
            y += ui.paragraph(Component.translatable("villagelife.book.building.places", b.getListOrEmpty("workers").size(),
                    b.getIntOr("places", 0)), x, y, w, UiTheme.INK_DIM);
        }
        if (BuildingsPage.withoutChest(b)) {
            y += ui.paragraph(Component.translatable("villagelife.problem.workplace_without_chest.tip"), x, y, w, UiTheme.BAD_INK);
        }
        return y + 4;
    }

    private static int row(Ui ui, int x, int y, int w, String key, String value) {
        int valueWidth = ui.font().width(value);
        ui.text(Component.translatable("villagelife.book.building." + key), x, y, UiTheme.INK_DIM, w - valueWidth - 6);
        ui.text(Component.literal(value), x + w - valueWidth, y, UiTheme.INK, valueWidth);
        return y + LINE;
    }

    private static int heading(Ui ui, int x, int y, int w, Component title) {
        ui.text(title, x, y, UiTheme.INK, w);
        ui.graphics().fill(x, y + 10, x + w, y + 11, 0x33705A3A);
        return y + LINE + 3;
    }

    /** "◀ Pasture ▶" for professions that need a work area (M13c); missing area in red. */
    private static int workArea(Ui ui, int x, int y, int w, CompoundTag b, CompoundTag section, boolean manage,
                                String villageId, String id) {
        boolean needed = b.getBooleanOr("needs_area", false);
        int current = b.getIntOr("work_area", -1);
        if (!needed && current < 0) {
            return y;
        }
        y = heading(ui, x, y, w, Component.translatable("villagelife.book.building.work_area"));
        ListTag zones = section.getListOrEmpty("zones");
        List<Integer> choices = new ArrayList<>();
        choices.add(-1);
        for (int i = 0; i < zones.size(); i++) {
            choices.add(zones.getCompoundOrEmpty(i).getIntOr("id", -1));
        }
        int index = Math.max(0, choices.indexOf(current));
        boolean canChange = manage && choices.size() > 1;
        int previous = choices.get((index - 1 + choices.size()) % choices.size());
        int next = choices.get((index + 1) % choices.size());
        ui.button(new UiRect(x, y, 16, 16), Component.literal("◀"), canChange, () -> send(villageId, id, "area:" + area(previous)));
        ui.button(new UiRect(x + w - 16, y, 16, 16), Component.literal("▶"), canChange, () -> send(villageId, id, "area:" + area(next)));
        String pasture = b.getStringOr("auto_area", ""); // M21d: nearest marked pasture, used while no zone is chosen
        Component shown = current >= 0 ? Component.literal(b.getStringOr("work_area_name", "?"))
                : !pasture.isEmpty() ? Component.translatable("villagelife.book.building.work_area.pasture", pasture)
                : Component.translatable("villagelife.book.building.work_area.none");
        boolean missing = current < 0 && needed && pasture.isEmpty();
        ui.text(shown, new UiRect(x + 20, y, w - 40, 16), missing ? UiTheme.BAD_INK : UiTheme.INK, 0);
        y += 20;
        if (missing) {
            y += ui.paragraph(Component.translatable(zones.isEmpty() ? "villagelife.book.building.work_area.draw_first"
                    : "villagelife.book.building.work_area.missing"), x, y, w, UiTheme.BAD_INK);
        }
        return y + 4;
    }

    private static String area(int zoneId) {
        return zoneId < 0 ? "none" : String.valueOf(zoneId);
    }

    /** Names of residents or workers; a click opens the resident in "Residents". */
    private static int people(Ui ui, int x, int y, int w, ListTag people, String headingKey) {
        if (people.isEmpty()) {
            return y;
        }
        y = heading(ui, x, y, w, Component.translatable(headingKey, people.size()));
        for (int i = 0; i < people.size(); i++) {
            CompoundTag p = people.getCompoundOrEmpty(i);
            UiRect r = new UiRect(x, y - 1, w, LINE);
            boolean hover = ui.hovered(r);
            ui.text(Component.literal("• " + p.getStringOr("name", "?")), x, y, hover ? UiTheme.GOOD_INK : UiTheme.INK, w);
            String residentId = p.getStringOr("id", "");
            ui.clickable(r, () -> ui.navigate("citizens:id=" + residentId));
            y += LINE;
        }
        return y + 4;
    }

    private static int contents(Ui ui, int x, int y, int w, String id, CompoundTag contents) {
        y = heading(ui, x, y, w, Component.translatable("villagelife.book.building.contents"));
        String message = null;
        List<ItemStack> stacks = List.of();
        if (!contents.getStringOr("building", "").equals(id)) {
            message = "villagelife.book.building.loading";
        } else if (contents.getIntOr("loaded_containers", 0) == 0) {
            message = "villagelife.book.building.not_loaded";
        } else {
            stacks = stacks(contents.getListOrEmpty("items"));
            if (stacks.isEmpty()) {
                message = "villagelife.book.building.empty";
            }
        }
        if (message != null) {
            return y + ui.paragraph(Component.translatable(message), x, y, w, UiTheme.INK_DIM);
        }
        int perRow = Math.max(1, w / 18);
        for (int i = 0; i < stacks.size(); i++) {
            int sx = x + (i % perRow) * 18;
            int sy = y + (i / perRow) * 18;
            UiTheme.draw(ui.graphics(), UiTheme.SLOT, new UiRect(sx, sy, 18, 18));
            ItemStack stack = stacks.get(i);
            ui.item(stack, sx + 1, sy + 1);
            ui.graphics().itemDecorations(ui.font(), stack, sx + 1, sy + 1);
            if (ui.hovered(new UiRect(sx, sy, 18, 18))) {
                ui.graphics().setTooltipForNextFrame(ui.font(), stack, ui.mouseX(), ui.mouseY());
            }
        }
        return y + ((stacks.size() + perRow - 1) / perRow) * 18 + 4;
    }

    private static List<ItemStack> stacks(ListTag list) {
        List<ItemStack> stacks = new ArrayList<>();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return stacks;
        }
        RegistryOps<net.minecraft.nbt.Tag> ops = RegistryOps.create(NbtOps.INSTANCE, minecraft.level.registryAccess());
        for (int i = 0; i < list.size(); i++) {
            ItemStack.CODEC.parse(ops, list.get(i)).result().ifPresent(stacks::add);
        }
        return stacks;
    }

    private static void send(String villageId, String buildingId, String action) {
        if (!villageId.isEmpty()) {
            ClientPacketDistributor.sendToServer(new BuildingActionPayload(villageId, buildingId, action));
        }
    }

    boolean mouseScrolled(double x, double y, double amount) {
        return scroll.mouseScrolled(x, y, amount);
    }

    boolean mouseDragged(double x, double y) {
        return scroll.mouseDragged(x, y);
    }

    void mouseReleased() {
        scroll.mouseReleased();
    }
}
