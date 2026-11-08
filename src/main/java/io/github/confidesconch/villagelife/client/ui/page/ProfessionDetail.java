package io.github.confidesconch.villagelife.client.ui.page;

import java.util.ArrayList;
import java.util.List;

import io.github.confidesconch.villagelife.client.ui.component.ScrollPanel;
import io.github.confidesconch.villagelife.client.ui.core.Ui;
import io.github.confidesconch.villagelife.client.ui.core.UiRect;
import io.github.confidesconch.villagelife.client.ui.core.UiTheme;
import io.github.confidesconch.villagelife.core.registry.ModItems;
import io.github.confidesconch.villagelife.resident.Job;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The chosen profession in "Work &amp; professions" (M21b6), on parchment and scrollable: how many work
 * and how many places are free, how to build its workplace, what must be nearby, tool, materials and
 * products, and every workplace with its worker (a click opens the resident) or "free" (a click opens
 * the building), with work problems in red. Same content as the book's profession pages.
 */
final class ProfessionDetail {
    private static final int LINE = 11;

    /** An item with the text shown when hovering it. */
    private record Icon(ItemStack stack, Component tooltip) {}

    private final ScrollPanel scroll = new ScrollPanel();
    private String shownId;

    void render(Ui ui, UiRect area, CompoundTag p) {
        UiTheme.draw(ui.graphics(), UiTheme.PANEL, area);
        UiRect inner = area.inset(10, 8, 6, 8);
        if (p == null) {
            ui.text(Component.translatable("villagelife.ui.jobs.pick"), inner.x(), inner.y(), UiTheme.INK_DIM, inner.w());
            return;
        }
        String id = p.getStringOr("id", "");
        if (!id.equals(shownId)) {
            shownId = id;
            scroll.reset();
        }
        int x = inner.x();
        int w = ScrollPanel.contentWidth(inner);
        int top = scroll.begin(ui, inner);
        int y = top;
        ui.text(JobsPage.name(id), x, y, UiTheme.INK, w);
        y += LINE;
        ListTag places = p.getListOrEmpty("workplaces");
        int free = JobsPage.free(p);
        Component status = places.isEmpty() ? Component.translatable("villagelife.book.professions.not_built")
                : Component.translatable("villagelife.book.professions.tile_status", places.size() - free, free);
        ui.text(status, x, y, places.isEmpty() ? UiTheme.INK_DIM : free > 0 ? UiTheme.INK : UiTheme.GOOD_INK, w);
        y += LINE + 2;
        Component tip = Component.translatableWithFallback(Job.translationKey(id) + ".tip", "");
        if (!tip.getString().isEmpty()) {
            y += ui.paragraph(tip, x, y, w, UiTheme.INK_DIM) + 4;
        }
        y = guide(ui, x, y, w, p);
        String tool = p.getStringOr("tool", "");
        if (!tool.isEmpty()) {
            y = items(ui, x, y, w, "villagelife.book.profession.tool", List.of(JobsPage.stack(tool)));
        }
        y = items(ui, x, y, w, "villagelife.book.profession.materials", JobsPage.stacks(p.getListOrEmpty("materials")));
        y = items(ui, x, y, w, "villagelife.book.profession.products", JobsPage.stacks(p.getListOrEmpty("products")));
        y = workplaces(ui, x, y, w, places);
        scroll.end(ui, y - top);
    }

    private static int heading(Ui ui, int x, int y, int w, Component title) {
        ui.text(title, x, y, UiTheme.INK, w);
        ui.graphics().fill(x, y + 10, x + w, y + 11, 0x33705A3A);
        return y + LINE + 3;
    }

    /** "How to build it": room › marker › workstation › chest, then what must be nearby. */
    private static int guide(Ui ui, int x, int y, int w, CompoundTag p) {
        y = heading(ui, x, y, w, Component.translatable("villagelife.book.profession.build"));
        ItemStack workstation = JobsPage.workstation(p);
        List<Icon> steps = List.of(
                new Icon(new ItemStack(Items.OAK_DOOR), Component.translatable("villagelife.book.profession.step.room")),
                new Icon(new ItemStack(ModItems.BUILDING_MARKER.get()), Component.translatable("villagelife.book.profession.step.marker")),
                new Icon(workstation, Component.translatable("villagelife.book.profession.workstation", workstation.getHoverName())),
                new Icon(new ItemStack(Items.CHEST), Component.translatable("villagelife.book.profession.step.chest")));
        y = icons(ui, x, y, w, steps, true);
        List<Icon> nearby = new ArrayList<>();
        for (ItemStack stack : JobsPage.stacks(p.getListOrEmpty("nearby"))) {
            nearby.add(new Icon(stack, stack.getHoverName()));
        }
        if (p.getBooleanOr("work_area", false)) {
            nearby.add(new Icon(new ItemStack(Items.FILLED_MAP), Component.translatable("villagelife.book.profession.work_area")));
        }
        if (!nearby.isEmpty()) {
            ui.text(Component.translatable("villagelife.book.profession.nearby", p.getIntOr("radius", 0)), x, y, UiTheme.INK_DIM, w);
            y = icons(ui, x, y + LINE, w, nearby, false);
        }
        return y + 2;
    }

    /** A row of icons that wraps; with "›" between them for steps. */
    private static int icons(Ui ui, int x, int y, int w, List<Icon> icons, boolean arrows) {
        int step = arrows ? 28 : 20;
        int perRow = Math.max(1, (w + step - 18) / step);
        for (int i = 0; i < icons.size(); i++) {
            int ix = x + (i % perRow) * step;
            int iy = y + (i / perRow) * 20;
            ui.slot(icons.get(i).stack(), ix, iy, icons.get(i).tooltip());
            if (arrows && i + 1 < icons.size() && (i + 1) % perRow != 0) {
                ui.graphics().text(ui.font(), "›", ix + 20, iy + 5, UiTheme.INK_DIM, false);
            }
        }
        return y + ((icons.size() + perRow - 1) / perRow) * 20 + 2;
    }

    /** "Needs: [items]"; nothing if the list is empty. */
    private static int items(Ui ui, int x, int y, int w, String labelKey, List<ItemStack> stacks) {
        if (stacks.isEmpty()) {
            return y;
        }
        ui.text(Component.translatable(labelKey), x, y, UiTheme.INK_DIM, w);
        y += LINE;
        int perRow = Math.max(1, w / 18);
        for (int i = 0; i < stacks.size(); i++) {
            ui.slot(stacks.get(i), x + (i % perRow) * 18, y + (i / perRow) * 18);
        }
        return y + ((stacks.size() + perRow - 1) / perRow) * 18 + 4;
    }

    /** "● Name" (click: the resident) or "○ free – x, y, z" (click: the building); problems below in red. */
    private static int workplaces(Ui ui, int x, int y, int w, ListTag places) {
        y = heading(ui, x, y, w, Component.translatable("villagelife.book.profession.workplaces", places.size()));
        if (places.isEmpty()) {
            return y + ui.paragraph(Component.translatable("villagelife.book.profession.no_workplace"), x, y, w, UiTheme.INK_DIM);
        }
        for (int i = 0; i < places.size(); i++) {
            CompoundTag place = places.getCompoundOrEmpty(i);
            String workerId = place.getStringOr("worker_id", "");
            String issue = place.getStringOr("issue", "");
            String pos = place.getStringOr("pos", "?");
            int color = !issue.isEmpty() ? UiTheme.BAD_INK : workerId.isEmpty() ? UiTheme.INK_DIM : UiTheme.GOOD_INK;
            UiRect r = new UiRect(x, y - 1, w, LINE);
            boolean hover = ui.hovered(r);
            Component text = workerId.isEmpty() ? Component.translatable("villagelife.book.profession.free_at", pos)
                    : Component.literal(place.getStringOr("worker_name", "?") + "  ·  " + pos);
            ui.text(Component.literal(workerId.isEmpty() ? "○ " : "● ").append(text), x, y, hover ? UiTheme.GOLD : color, w);
            String target = workerId.isEmpty() ? "buildings:at:" + pos : "citizens:id=" + workerId;
            ui.clickable(r, () -> ui.navigate(target));
            ui.tooltip(r, Component.translatable("villagelife.ui.overview.goto",
                    Component.translatable(workerId.isEmpty() ? "villagelife.ui.page.buildings" : "villagelife.ui.page.citizens")));
            y += LINE;
            if (!issue.isEmpty()) {
                y += ui.paragraph(Component.literal("   ").append(Component.translatableWithFallback(
                        "villagelife.problem.work." + issue + ".short", issue)), x, y, w, UiTheme.BAD_INK);
            }
        }
        return y + 4;
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
