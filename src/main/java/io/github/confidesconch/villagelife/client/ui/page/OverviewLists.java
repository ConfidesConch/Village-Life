package io.github.confidesconch.villagelife.client.ui.page;

import java.util.ArrayList;
import java.util.List;

import io.github.confidesconch.villagelife.book.UpdateVillageSettingsPayload;
import io.github.confidesconch.villagelife.client.ui.component.ScrollList;
import io.github.confidesconch.villagelife.client.ui.core.Ui;
import io.github.confidesconch.villagelife.client.ui.core.UiRect;
import io.github.confidesconch.villagelife.client.ui.core.UiTheme;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * The right half of the overview (M21b3): the village's problems (most important first; hovering shows
 * the tip, a click opens the area that solves it) and the event log (newest first).
 */
final class OverviewLists {
    private static final int ROW = 13;

    private final ScrollList problems = new ScrollList();
    private final ScrollList events = new ScrollList();
    /** Decoded event texts, kept until the server sends a new list. */
    private ListTag decodedFrom;
    private List<Component> decoded = List.of();

    /** Until when (system time) "Delete all" waits for its second click. */
    private long confirmClearUntil;
    private boolean manage;
    private String villageId = "";

    void render(Ui ui, UiRect area, CompoundTag data) {
        manage = data.getCompoundOrEmpty("overview").getBooleanOr("can_manage", false);
        villageId = data.getStringOr("village_id", "");
        UiTheme.draw(ui.graphics(), UiTheme.PANEL_DARK, area);
        UiRect inner = area.inset(6);
        int problemHeight = inner.h() * 45 / 100;
        CompoundTag problemData = data.getCompoundOrEmpty("problems");
        renderProblems(ui, inner.top(problemHeight), problemData.getListOrEmpty("list"), problemData.getIntOr("hidden", 0));
        UiRect lower = inner.below(problemHeight + 4);
        ui.graphics().fill(lower.x(), lower.y() - 3, lower.right(), lower.y() - 2, 0x66E7C66B);
        renderEvents(ui, lower, data.getCompoundOrEmpty("events").getListOrEmpty("list"));
    }

    /** A small ✕ at the right end of a row (owner and managers, M21b6b); drawn after the row, so it wins the click. */
    private void cross(Ui ui, UiRect row, Component tooltip, String key, String value) {
        if (!manage) {
            return;
        }
        UiRect r = new UiRect(row.right() - 11, row.y() + 1, 10, row.h() - 2);
        boolean hover = ui.hovered(r);
        ui.text(Component.literal("✕"), r, hover ? UiTheme.BAD : UiTheme.TEXT_DIM, 0);
        ui.tooltip(r, tooltip);
        ui.clickable(r, () -> send(key, value));
    }

    private void send(String key, String value) {
        if (!villageId.isEmpty()) {
            ClientPacketDistributor.sendToServer(new UpdateVillageSettingsPayload(villageId, key, value));
        }
    }

    // ---- problems ------------------------------------------------------------------------------

    private void renderProblems(Ui ui, UiRect r, ListTag list, int hidden) {
        List<CompoundTag> sorted = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            sorted.add(list.getCompoundOrEmpty(i));
        }
        sorted.sort((a, b) -> rank(a) - rank(b));
        int titleWidth = ui.text(Component.translatable("villagelife.ui.overview.problems", sorted.size()), r.x(), r.y() + 1,
                UiTheme.GOLD, r.w());
        if (hidden > 0) {
            ui.text(Component.translatable("villagelife.ui.overview.hidden", hidden), r.x() + titleWidth + 6, r.y() + 1,
                    UiTheme.TEXT_DIM, r.w() - titleWidth - 6);
            ui.tooltip(new UiRect(r.x() + titleWidth + 6, r.y(), r.w() - titleWidth - 6, 11),
                    Component.translatable("villagelife.ui.overview.hidden_tip"));
        }
        UiRect rows = r.below(13);
        if (sorted.isEmpty()) {
            ui.text(Component.translatable("villagelife.book.problems.none"), rows.x(), rows.y() + 2, UiTheme.GOOD, rows.w());
            return;
        }
        problems.render(ui, rows, sorted.size(), ROW, (u, index, row) -> {
            CompoundTag p = sorted.get(index);
            String key = "villagelife.problem." + p.getStringOr("key", "");
            String target = target(p);
            if (u.hovered(row)) {
                u.graphics().fill(row.x(), row.y(), row.right(), row.bottom(), 0x30E7C66B);
                u.tooltip(row, Component.translatable(key, args(p)), Component.translatable(key + ".tip"),
                        Component.translatable("villagelife.ui.overview.goto", Component.translatable("villagelife.ui.page." + OverviewPage.page(target))));
            }
            int color = switch (p.getStringOr("severity", "info")) {
                case "error" -> UiTheme.BAD;
                case "warning" -> UiTheme.WARN;
                default -> UiTheme.TEXT_DIM;
            };
            u.graphics().fill(row.x() + 2, row.y() + 4, row.x() + 6, row.y() + 8, color);
            u.text(Component.translatable(key, args(p)), row.inset(10, 0, manage ? 14 : 2, 0), UiTheme.TEXT, -1);
            u.clickable(row, () -> u.navigate(target));
            cross(u, row, Component.translatable("villagelife.ui.overview.hide_problem"), "hide_problem", p.getStringOr("pid", ""));
        });
    }

    private static int rank(CompoundTag p) {
        return switch (p.getStringOr("severity", "info")) {
            case "error" -> 0;
            case "warning" -> 1;
            default -> 2;
        };
    }

    private static Object[] args(CompoundTag p) {
        ListTag args = p.getListOrEmpty("args");
        Object[] values = new Object[args.size()];
        for (int i = 0; i < args.size(); i++) {
            values[i] = args.getStringOr(i, "");
        }
        return values;
    }

    /** The area where a problem is solved; problems of one building lead straight to it (M21b5). */
    static String target(CompoundTag p) {
        String key = p.getStringOr("key", "");
        ListTag args = p.getListOrEmpty("args");
        if (key.startsWith("work.")) {
            return "buildings:at:" + args.getStringOr(1, ""); // args: resident, workplace position
        }
        return switch (key) {
            case "invalid_building", "workplace_without_chest" -> "buildings:at:" + args.getStringOr(0, "");
            case "homeless", "no_beds" -> "buildings:free_beds";
            case "no_storage", "storage_full" -> "buildings:storage";
            case "unemployed" -> "jobs:free";
            case "no_food", "low_food" -> "economy";
            case "starving", "hungry" -> "citizens:hungry";
            case "unhappy" -> "citizens:unhappy";
            default -> "buildings";
        };
    }

    // ---- events --------------------------------------------------------------------------------

    private void renderEvents(Ui ui, UiRect r, ListTag list) {
        ui.text(Component.translatable("villagelife.ui.overview.events"), r.x(), r.y() + 1, UiTheme.GOLD, r.w());
        if (manage && !list.isEmpty()) { // "Delete all" – the second click within 3 s deletes
            boolean confirming = System.currentTimeMillis() < confirmClearUntil;
            Component label = Component.translatable(confirming ? "villagelife.ui.overview.clear_confirm" : "villagelife.ui.overview.clear");
            int width = ui.font().width(label);
            UiRect link = new UiRect(r.right() - width - 2, r.y(), width + 2, 11);
            ui.text(label, link, confirming || ui.hovered(link) ? UiTheme.BAD : UiTheme.TEXT_DIM, 1);
            ui.clickable(link, () -> {
                if (System.currentTimeMillis() < confirmClearUntil) {
                    confirmClearUntil = 0;
                    send("clear_events", "all");
                } else {
                    confirmClearUntil = System.currentTimeMillis() + 3000;
                }
            });
        }
        UiRect rows = r.below(13);
        if (list.isEmpty()) {
            ui.text(Component.translatable("villagelife.ui.overview.events.none"), rows.x(), rows.y() + 2, UiTheme.TEXT_DIM, rows.w());
            return;
        }
        List<Component> texts = decode(list);
        int dayWidth = ui.font().width(Component.translatable("villagelife.ui.overview.day_short",
                list.getCompoundOrEmpty(0).getLongOr("day", 0))); // the newest day is the widest
        events.render(ui, rows, list.size(), ROW, (u, index, row) -> {
            CompoundTag e = list.getCompoundOrEmpty(index);
            u.graphics().fill(row.x() + 2, row.y() + 4, row.x() + 6, row.y() + 8, topicColor(e.getStringOr("topic", "")));
            u.text(Component.translatable("villagelife.ui.overview.day_short", e.getLongOr("day", 0)),
                    row.inset(10, 0, 0, 0).left(dayWidth), UiTheme.TEXT_DIM, -1);
            u.text(texts.get(index), row.inset(14 + dayWidth, 0, manage ? 14 : 2, 0), UiTheme.TEXT, -1);
            cross(u, row, Component.translatable("villagelife.ui.overview.delete_event"), "delete_event",
                    String.valueOf(e.getLongOr("id", 0)));
        });
    }

    /** Event texts arrive as components; they are decoded once per list the server sends. */
    private List<Component> decode(ListTag list) {
        if (list == decodedFrom) {
            return decoded;
        }
        var level = Minecraft.getInstance().level;
        List<Component> texts = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            Tag text = list.getCompoundOrEmpty(i).get("text");
            texts.add(text == null || level == null ? Component.literal("?")
                    : ComponentSerialization.CODEC.parse(level.registryAccess().createSerializationContext(NbtOps.INSTANCE), text)
                            .result().orElse(Component.literal("?")));
        }
        decodedFrom = list;
        decoded = texts;
        return texts;
    }

    private static int topicColor(String topic) {
        return switch (topic) {
            case "family" -> 0xFFE58FB5;
            case "growth" -> UiTheme.GOLD;
            case "trade", "caravan" -> UiTheme.GOOD;
            case "jobs" -> 0xFF7FB2E5;
            case "defense" -> UiTheme.BAD;
            case "buildings" -> 0xFFC9A26B;
            default -> UiTheme.TEXT; // residents
        };
    }

    boolean mouseScrolled(double x, double y, double amount) {
        return problems.mouseScrolled(x, y, amount) || events.mouseScrolled(x, y, amount);
    }

    boolean mouseDragged(double x, double y) {
        return problems.mouseDragged(x, y) || events.mouseDragged(x, y);
    }

    void mouseReleased() {
        problems.mouseReleased();
        events.mouseReleased();
    }
}
