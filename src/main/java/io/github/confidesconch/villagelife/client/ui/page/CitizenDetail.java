package io.github.confidesconch.villagelife.client.ui.page;

import java.util.ArrayList;
import java.util.List;

import io.github.confidesconch.villagelife.client.ui.component.ScrollPanel;
import io.github.confidesconch.villagelife.client.ui.core.Ui;
import io.github.confidesconch.villagelife.client.ui.core.UiRect;
import io.github.confidesconch.villagelife.client.ui.core.UiTheme;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.ItemStack;

/**
 * The chosen resident in "Residents" (M21b4), on parchment and scrollable: who he is (job, bed,
 * family, education), what he is doing, his needs as bars, his friends and his inventory – the same
 * values as the resident's page in the book. Live values only exist while the resident is loaded.
 */
final class CitizenDetail {
    private static final int LINE = 11;
    private static final int WARN_INK = 0xFF9A5B12;
    private static final int LOVE = 0xFFC2185B;

    private final ScrollPanel scroll = new ScrollPanel();
    private String shownId;

    void render(Ui ui, UiRect area, CompoundTag r, CompoundTag relations) {
        UiTheme.draw(ui.graphics(), UiTheme.PANEL, area);
        UiRect inner = area.inset(10, 8, 6, 8);
        if (r == null) {
            ui.text(Component.translatable("villagelife.ui.citizens.pick"), inner.x(), inner.y(), UiTheme.INK_DIM, inner.w());
            return;
        }
        String id = r.getStringOr("id", "");
        if (!id.equals(shownId)) {
            shownId = id;
            scroll.reset();
        }
        int w = ScrollPanel.contentWidth(inner);
        int top = scroll.begin(ui, inner);
        int y = top;
        ui.text(Component.literal(r.getStringOr("name", "?")), inner.x(), y, UiTheme.INK, w);
        y += LINE;
        ui.text(CitizensPage.jobName(r), inner.x(), y, r.contains("job") ? UiTheme.GOOD_INK : UiTheme.INK_DIM, w);
        y += LINE + 6;
        y = facts(ui, inner.x(), y, w, r);
        boolean loaded = r.getBooleanOr("loaded", false);
        if (loaded) {
            y = needs(ui, inner.x(), y + 6, w, r);
        } else {
            y += 6 + ui.paragraph(Component.translatable("villagelife.book.resident.not_loaded"), inner.x(), y + 6, w, UiTheme.INK_DIM);
        }
        y = friends(ui, inner.x(), y + 6, w, relations.getListOrEmpty(id));
        if (loaded) {
            y = inventory(ui, inner.x(), y + 6, w, r);
        }
        scroll.end(ui, y - top);
    }

    private static int facts(Ui ui, int x, int y, int w, CompoundTag r) {
        if (r.contains("grows_up_in")) {
            y = row(ui, x, y, w, "job", Component.translatable("villagelife.book.resident.child", r.getIntOr("grows_up_in", 1)), LOVE);
        }
        y = row(ui, x, y, w, "home", r.contains("bed") ? Component.literal(r.getStringOr("bed", ""))
                : Component.translatable("villagelife.book.resident.homeless"), r.contains("bed") ? UiTheme.INK : UiTheme.BAD_INK);
        y = row(ui, x, y, w, "partner", r.contains("partner") ? Component.literal(r.getStringOr("partner", "")) : Component.literal("–"),
                r.contains("partner") ? LOVE : UiTheme.INK_DIM);
        y = row(ui, x, y, w, "parents", dash(r.getStringOr("parents", "")), UiTheme.INK);
        y = row(ui, x, y, w, "children", dash(r.getStringOr("children", "")), UiTheme.INK);
        int education = r.getIntOr("education", 0);
        y = row(ui, x, y, w, "education", Component.literal(education + " %"), education > 0 ? UiTheme.INK : UiTheme.INK_DIM);
        y = row(ui, x, y, w, "since", Component.translatable("villagelife.book.overview.day", r.getLongOr("joined_day", 1)), UiTheme.INK);
        if (r.getBooleanOr("loaded", false)) {
            y = row(ui, x, y, w, "activity", Component.translatable(r.getStringOr("activity", "")), UiTheme.INK);
            boolean rested = r.getBooleanOr("rested", false);
            y = row(ui, x, y, w, "sleep", Component.translatable(rested ? "villagelife.resident.rested" : "villagelife.resident.tired"),
                    rested ? UiTheme.GOOD_INK : UiTheme.BAD_INK);
        }
        return y;
    }

    private static Component dash(String text) {
        return Component.literal(text.isEmpty() ? "–" : text);
    }

    /** "Label ......... value" with the book's label key {@code villagelife.book.resident.<key>}. */
    private static int row(Ui ui, int x, int y, int w, String key, Component value, int color) {
        int labelWidth = Math.min(w / 2, 110);
        ui.text(Component.translatable("villagelife.book.resident." + key), x, y, UiTheme.INK_DIM, labelWidth - 4);
        ui.text(value, x + labelWidth, y, color, w - labelWidth);
        return y + LINE;
    }

    private static int heading(Ui ui, int x, int y, int w, Component title) {
        ui.text(title, x, y, UiTheme.INK, w);
        ui.graphics().fill(x, y + 10, x + w, y + 11, 0x33705A3A);
        return y + LINE + 3;
    }

    private static int needs(Ui ui, int x, int y, int w, CompoundTag r) {
        y = heading(ui, x, y, w, Component.translatable("villagelife.book.resident.needs"));
        double food = r.getDoubleOr("food", 20);
        y = bar(ui, x, y, w, Component.translatable("villagelife.book.resident.food", Math.round(food)), (float) (food / 20.0),
                food <= 3 ? UiTheme.BAD : food <= 12 ? UiTheme.WARN : UiTheme.GOOD);
        int satisfaction = r.getIntOr("satisfaction", 0);
        String mood = r.getStringOr("mood", "content");
        y = bar(ui, x, y, w, Component.translatable("villagelife.book.resident.satisfaction", satisfaction,
                Component.translatable("villagelife.mood." + mood)), satisfaction / 100F, CitizensPage.moodColor(mood));
        for (String key : List.of("social", "laws")) {
            int value = r.getIntOr(key, 0);
            if (value != 0) {
                y = row(ui, x, y, w, key, Component.literal((value > 0 ? "+" : "") + value), value > 0 ? UiTheme.GOOD_INK : UiTheme.BAD_INK);
            }
        }
        return y;
    }

    private static int bar(Ui ui, int x, int y, int w, Component label, float fraction, int color) {
        ui.text(label, x, y, UiTheme.INK, w);
        UiTheme.bar(ui.graphics(), new UiRect(x, y + LINE, w, 6), fraction, color);
        return y + LINE + 10;
    }

    private static int friends(Ui ui, int x, int y, int w, ListTag relations) {
        y = heading(ui, x, y, w, Component.translatable("villagelife.book.resident.friends"));
        if (relations.isEmpty()) {
            return y + ui.paragraph(Component.translatable("villagelife.book.resident.no_friends"), x, y, w, UiTheme.INK_DIM);
        }
        for (int i = 0; i < relations.size(); i++) {
            CompoundTag f = relations.getCompoundOrEmpty(i);
            String stage = f.getStringOr("stage", "stranger");
            String symbol = switch (stage) {
                case "best_friend" -> "♥♥♥";
                case "friend" -> "♥♥";
                case "acquaintance" -> "♥";
                case "dislike" -> "✖";
                default -> "·";
            };
            int symbolWidth = ui.font().width(symbol);
            ui.text(Component.literal(f.getStringOr("name", "?")), x, y, UiTheme.INK, w - symbolWidth - 6);
            ui.text(Component.literal(symbol), x + w - symbolWidth, y,
                    "dislike".equals(stage) ? UiTheme.BAD_INK : stage.contains("friend") ? LOVE : UiTheme.INK_DIM, symbolWidth);
            y += LINE;
        }
        return y;
    }

    private static int inventory(Ui ui, int x, int y, int w, CompoundTag r) {
        y = heading(ui, x, y, w, Component.translatable("villagelife.book.resident.inventory"));
        List<ItemStack> stacks = stacks(r.getListOrEmpty("inventory"));
        if (stacks.isEmpty()) {
            return y + LINE;
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
        return y + ((stacks.size() + perRow - 1) / perRow) * 18 + 2;
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
