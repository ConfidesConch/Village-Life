package io.github.confidesconch.villagelife.client.ui.page;

import java.util.ArrayList;
import java.util.List;

import io.github.confidesconch.villagelife.book.UpdateVillageSettingsPayload;
import io.github.confidesconch.villagelife.client.ui.core.Ui;
import io.github.confidesconch.villagelife.client.ui.core.UiPage;
import io.github.confidesconch.villagelife.client.ui.core.UiRect;
import io.github.confidesconch.villagelife.client.ui.core.UiTheme;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * "Overview" of the village window (M21b3): the village at a glance. Left on parchment the stage and
 * what the next one needs, the most important values (each one leads to its area when clicked) and the
 * alarm button; right the problems (clickable, with tip) and the event log ({@link OverviewLists}).
 */
public final class OverviewPage implements UiPage {
    /** Amber that is readable on parchment. */
    private static final int WARN_INK = 0xFF9A5B12;
    private static final int LINE = 11;

    private final OverviewLists lists = new OverviewLists();

    /** One value of the overview: label key, value, colour, area it leads to. */
    private record Stat(String key, String value, int color, String target) {}

    /** One requirement of the next stage. */
    private record Requirement(String key, boolean met, String value) {}

    @Override
    public String id() {
        return "overview";
    }

    @Override
    public ItemStack icon() {
        return new ItemStack(Items.BELL);
    }

    @Override
    public Component title() {
        return Component.translatable("villagelife.ui.page.overview");
    }

    @Override
    public int order() {
        return 10;
    }

    @Override
    public List<String> sections() {
        return List.of("problems", "events");
    }

    @Override
    public void render(Ui ui, UiRect area, CompoundTag data) {
        int leftWidth = area.w() * 52 / 100;
        UiRect left = area.left(leftWidth);
        UiRect right = area.rightOf(leftWidth + 6);
        UiTheme.draw(ui.graphics(), UiTheme.PANEL, left);
        UiRect inner = left.inset(10, 8, 10, 8);
        UiRect button = inner.bottomPart(20);
        int y = renderStage(ui, inner, data);
        ui.graphics().fill(inner.x(), y + 2, inner.right(), y + 3, 0x33705A3A);
        renderStats(ui, new UiRect(inner.x(), y + 7, inner.w(), Math.max(0, button.y() - y - 11)), data);
        renderAlarm(ui, button, data);
        lists.render(ui, right, data);
    }

    // ---- stage ---------------------------------------------------------------------------------

    /** Stage, day and the next stage's requirements; returns the y below. */
    private static int renderStage(Ui ui, UiRect r, CompoundTag data) {
        CompoundTag overview = data.getCompoundOrEmpty("overview");
        CompoundTag growth = data.getCompoundOrEmpty("growth");
        int stage = growth.getIntOr("stage", 0);
        int y = r.y();
        ui.text(Component.translatable("villagelife.ui.overview.stage", Component.translatable("villagelife.stage." + stage)),
                r.x(), y, UiTheme.INK, r.w());
        y += LINE;
        ui.text(Component.translatable("villagelife.ui.overview.day", overview.getLongOr("day", 1),
                overview.getLongOr("founded_day", 1)), r.x(), y, UiTheme.INK_DIM, r.w());
        y += LINE + 4;
        if (!growth.contains("next")) {
            ui.text(Component.translatable("villagelife.book.growth.top"), r.x(), y, UiTheme.GOOD_INK, r.w());
            y += LINE;
        } else {
            List<Requirement> reqs = requirements(growth);
            int met = (int) reqs.stream().filter(Requirement::met).count();
            ui.text(Component.translatable("villagelife.book.growth.next", Component.translatable("villagelife.stage." + (stage + 1))),
                    r.x(), y, UiTheme.INK, r.w());
            y += LINE;
            Component progress = Component.translatable("villagelife.book.growth.progress", met, reqs.size());
            int progressWidth = ui.font().width(progress);
            UiTheme.bar(ui.graphics(), new UiRect(r.x(), y + 1, Math.max(0, r.w() - progressWidth - 6), 6),
                    met / (float) reqs.size(), met == reqs.size() ? UiTheme.GOOD : UiTheme.GOLD);
            ui.text(progress, r.right() - progressWidth, y, UiTheme.INK_DIM, progressWidth);
            y += LINE;
            int half = r.w() / 2;
            for (int i = 0; i < reqs.size(); i++) {
                Requirement req = reqs.get(i);
                UiRect cell = new UiRect(r.x() + (i % 2) * half, y + (i / 2) * LINE, half - 6, LINE);
                requirement(ui, cell, req);
            }
            y += (reqs.size() + 1) / 2 * LINE;
        }
        if (growth.getBooleanOr("below", false)) {
            y += 2 + ui.paragraph(Component.translatable("villagelife.ui.overview.below"), r.x(), y + 2, r.w(), UiTheme.BAD_INK);
        }
        return y;
    }

    private static void requirement(Ui ui, UiRect cell, Requirement req) {
        Component mark = Component.literal(req.met() ? "✔ " : "✖ ");
        int markWidth = ui.font().width(mark);
        int valueWidth = ui.font().width(req.value());
        ui.text(mark, cell.x(), cell.y(), req.met() ? UiTheme.GOOD_INK : UiTheme.BAD_INK, markWidth);
        ui.text(Component.translatable("villagelife.book.growth.req." + req.key()), cell.x() + markWidth, cell.y(),
                UiTheme.INK, Math.max(0, cell.w() - markWidth - valueWidth - 4));
        if (valueWidth > 0) {
            ui.text(Component.literal(req.value()), cell.right() - valueWidth, cell.y(),
                    req.met() ? UiTheme.GOOD_INK : UiTheme.INK, valueWidth);
        }
    }

    /** The same requirements the book shows (GrowthPages), read from the "growth" section. */
    private static List<Requirement> requirements(CompoundTag g) {
        CompoundTag next = g.getCompoundOrEmpty("next");
        List<Requirement> list = new ArrayList<>();
        list.add(count(g, next, "residents"));
        int mood = g.getIntOr("satisfaction", -1);
        list.add(new Requirement("satisfaction", mood >= next.getIntOr("satisfaction", 0),
                (mood < 0 ? "?" : String.valueOf(mood)) + "/" + next.getIntOr("satisfaction", 0) + "%"));
        list.add(count(g, next, "professions"));
        if (next.getIntOr("guards", 0) > 0) {
            list.add(count(g, next, "guards"));
        }
        for (String key : List.of("storage", "market")) {
            if (next.getBooleanOr(key, false)) {
                list.add(new Requirement(key, g.getBooleanOr(key, false), ""));
            }
        }
        return list;
    }

    private static Requirement count(CompoundTag g, CompoundTag next, String key) {
        int have = g.getIntOr(key, 0);
        int need = next.getIntOr(key, 0);
        return new Requirement(key, have >= need, have + "/" + need);
    }

    // ---- values --------------------------------------------------------------------------------

    private static void renderStats(Ui ui, UiRect r, CompoundTag data) {
        List<Stat> stats = stats(data);
        int half = r.w() / 2;
        int rows = Math.max(1, r.h() / (LINE + 1));
        for (int i = 0; i < stats.size() && i / 2 < rows; i++) {
            Stat stat = stats.get(i);
            UiRect cell = new UiRect(r.x() + (i % 2) * half, r.y() + (i / 2) * (LINE + 1), half - 6, LINE);
            if (ui.hovered(cell)) {
                ui.graphics().fill(cell.x() - 2, cell.y() - 1, cell.right() + 2, cell.bottom(), 0x22705A3A);
                ui.tooltip(cell, Component.translatable("villagelife.ui.overview.goto",
                        Component.translatable("villagelife.ui.page." + page(stat.target()))));
            }
            Component value = Component.literal(stat.value());
            int valueWidth = ui.font().width(value);
            ui.text(Component.translatable("villagelife.ui.overview.stat." + stat.key()), cell.x(), cell.y(),
                    UiTheme.INK_DIM, Math.max(0, cell.w() - valueWidth - 4));
            ui.text(value, cell.right() - valueWidth, cell.y(), stat.color(), valueWidth);
            ui.clickable(cell, () -> ui.navigate(stat.target()));
        }
    }

    /** "citizens:hungry" → "citizens". */
    static String page(String target) {
        int colon = target.indexOf(':');
        return colon < 0 ? target : target.substring(0, colon);
    }

    /** Most important first: when space is short, the last ones are left out. */
    private static List<Stat> stats(CompoundTag data) {
        CompoundTag o = data.getCompoundOrEmpty("overview");
        CompoundTag g = data.getCompoundOrEmpty("growth");
        int homeless = o.getIntOr("homeless", 0);
        int unemployed = o.getIntOr("unemployed", 0);
        int mood = o.getIntOr("satisfaction", -1);
        int hungry = o.getIntOr("hungry", 0);
        int invalid = o.getIntOr("invalid_buildings", 0);
        int emeralds = data.getCompoundOrEmpty("market").getIntOr("village_emeralds", -1);
        List<Stat> list = new ArrayList<>();
        list.add(new Stat("residents", o.getIntOr("residents", 0) + "/" + g.getIntOr("max_residents", 0), UiTheme.INK, "citizens"));
        list.add(new Stat("mood", mood < 0 ? "–" : mood + " %",
                mood < 0 ? UiTheme.INK_DIM : mood >= 60 ? UiTheme.GOOD_INK : mood >= 40 ? WARN_INK : UiTheme.BAD_INK, "citizens:unhappy"));
        list.add(new Stat("beds", o.getIntOr("beds_free", 0) + "/" + o.getIntOr("beds_total", 0), UiTheme.INK, "buildings:free_beds"));
        list.add(new Stat("homeless", String.valueOf(homeless), homeless > 0 ? UiTheme.BAD_INK : UiTheme.GOOD_INK, "citizens:homeless"));
        list.add(new Stat("work", o.getIntOr("workplaces_taken", 0) + "/" + o.getIntOr("workplaces", 0), UiTheme.INK, "jobs:free"));
        list.add(new Stat("unemployed", String.valueOf(unemployed), unemployed > 0 ? WARN_INK : UiTheme.GOOD_INK, "jobs"));
        list.add(new Stat("hungry", String.valueOf(hungry), hungry > 0 ? WARN_INK : UiTheme.GOOD_INK, "citizens:hungry"));
        list.add(new Stat("emeralds", emeralds < 0 ? "–" : String.valueOf(emeralds), UiTheme.GOOD_INK, "economy"));
        list.add(new Stat("houses", String.valueOf(o.getIntOr("houses", 0)), UiTheme.INK, "buildings:houses"));
        list.add(new Stat("workshops", String.valueOf(o.getIntOr("workshops", 0)), UiTheme.INK, "buildings:workplaces"));
        list.add(new Stat("storages", String.valueOf(o.getIntOr("storages", 0)), UiTheme.INK, "buildings:storage"));
        list.add(new Stat("invalid", String.valueOf(invalid), invalid > 0 ? UiTheme.BAD_INK : UiTheme.GOOD_INK, "buildings:invalid"));
        return list;
    }

    // ---- alarm ---------------------------------------------------------------------------------

    private static void renderAlarm(Ui ui, UiRect r, CompoundTag data) {
        CompoundTag o = data.getCompoundOrEmpty("overview");
        boolean attack = o.getBooleanOr("under_attack", false);
        boolean manual = o.getBooleanOr("manual_alarm", false);
        boolean mayManage = o.getBooleanOr("can_manage", false);
        Component label = Component.translatable(attack ? "villagelife.ui.alarm.attack"
                : manual ? "villagelife.ui.alarm.stop" : "villagelife.ui.alarm.start");
        String villageId = data.getStringOr("village_id", "");
        ui.button(r, label, mayManage && !attack && !villageId.isEmpty(), () ->
                ClientPacketDistributor.sendToServer(new UpdateVillageSettingsPayload(villageId, "alarm", "toggle")));
        if (ui.hovered(r)) {
            ui.tooltip(r, Component.translatable(!mayManage ? "villagelife.ui.alarm.no_right"
                    : attack ? "villagelife.ui.alarm.attack_tip" : "villagelife.ui.alarm.tip"));
        }
        if (attack || manual) {
            ui.graphics().fill(r.x() - 1, r.y() - 1, r.right() + 1, r.y(), UiTheme.BAD);
        }
    }

    // ---- input ---------------------------------------------------------------------------------

    @Override
    public boolean mouseScrolled(double x, double y, double amount) {
        return lists.mouseScrolled(x, y, amount);
    }

    @Override
    public boolean mouseDragged(double x, double y) {
        return lists.mouseDragged(x, y);
    }

    @Override
    public void mouseReleased() {
        lists.mouseReleased();
    }
}
