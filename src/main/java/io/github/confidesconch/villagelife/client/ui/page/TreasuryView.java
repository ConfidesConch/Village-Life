package io.github.confidesconch.villagelife.client.ui.page;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import io.github.confidesconch.villagelife.client.ui.component.ScrollList;
import io.github.confidesconch.villagelife.client.ui.core.Ui;
import io.github.confidesconch.villagelife.client.ui.core.UiRect;
import io.github.confidesconch.villagelife.client.ui.core.UiTheme;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Tab "Treasury" (M21b7, the book's treasury page since M15c; owner and managers only): the emeralds
 * of the last 7 days as bars, the last business (scrollable) and the goods whose price is off its normal.
 */
final class TreasuryView {
    private static final int ROW = 12;
    private static final int MAX_PRICES = 6;

    private final ScrollList entries = new ScrollList();

    void render(Ui ui, UiRect area, CompoundTag t, boolean managers) {
        if (!managers) {
            UiTheme.draw(ui.graphics(), UiTheme.PANEL, area);
            UiRect inner = area.inset(12);
            ui.paragraph(Component.translatable("villagelife.ui.economy.treasury_managers"), inner.x(), inner.y(), inner.w(), UiTheme.INK_DIM);
            return;
        }
        int leftWidth = area.w() * 45 / 100;
        UiRect left = area.left(leftWidth);
        UiTheme.draw(ui.graphics(), UiTheme.PANEL, left);
        UiRect inner = left.inset(10, 8, 10, 8);
        int[] days = t.getIntArray("days").orElse(new int[0]);
        int today = days.length == 0 ? 0 : Math.max(0, days[days.length - 1]);
        ui.item(new ItemStack(Items.EMERALD), inner.x(), inner.y());
        ui.text(Component.translatable("villagelife.book.treasury.now", today), inner.x() + 20, inner.y() + 5, UiTheme.INK, inner.w() - 20);
        int y = inner.y() + 22;
        ui.text(Component.translatable("villagelife.book.treasury.week"), inner.x(), y, UiTheme.INK_DIM, inner.w());
        y += 12;
        int chartHeight = Math.max(40, Math.min(110, inner.bottom() - y - 16));
        chart(ui, new UiRect(inner.x(), y, inner.w(), chartHeight), days);
        y += chartHeight + 4;
        if (days.length > 0 && days[0] >= 0) {
            int change = today - days[0];
            ui.text(Component.translatable("villagelife.book.treasury.change", (change >= 0 ? "+" : "") + change),
                    inner.x(), y, change >= 0 ? UiTheme.GOOD_INK : UiTheme.BAD_INK, inner.w());
        }

        UiRect right = area.rightOf(leftWidth + 6);
        UiTheme.draw(ui.graphics(), UiTheme.PANEL_DARK, right);
        UiRect r = right.inset(6);
        List<CompoundTag> prices = prices(t.getListOrEmpty("prices"));
        int pricesHeight = prices.isEmpty() ? 0 : 16 + prices.size() * ROW;
        UiRect top = r.inset(0, 0, 0, pricesHeight);
        ui.text(Component.translatable("villagelife.book.treasury.entries"), top.x(), top.y() + 1, UiTheme.GOLD, top.w());
        ListTag list = t.getListOrEmpty("entries");
        UiRect rows = top.below(14);
        if (list.isEmpty()) {
            ui.text(Component.translatable("villagelife.book.treasury.no_entries"), rows.x(), rows.y() + 2, UiTheme.TEXT_DIM, rows.w());
        } else {
            entries.render(ui, rows, list.size(), ROW, (u, index, row) -> {
                CompoundTag e = list.getCompoundOrEmpty(index);
                int emeralds = e.getIntOr("emeralds", 0);
                Component amount = Component.literal(emeralds == 0 ? "" : (emeralds > 0 ? "+" : "") + emeralds);
                int amountWidth = u.font().width(amount);
                u.text(Component.translatable("villagelife.book.treasury.kind." + e.getStringOr("kind", ""), e.getIntOr("count", 0),
                        EconomyPage.itemName(e.getStringOr("item", ""))), row.inset(0, 0, amountWidth + 6, 0), UiTheme.TEXT, -1);
                u.text(amount, row, emeralds > 0 ? UiTheme.GOOD : UiTheme.BAD, 1);
            });
        }
        if (!prices.isEmpty()) {
            UiRect bottom = r.bottomPart(pricesHeight);
            ui.graphics().fill(bottom.x(), bottom.y(), bottom.right(), bottom.y() + 1, 0x66E7C66B);
            ui.text(Component.translatable("villagelife.book.treasury.prices"), bottom.x(), bottom.y() + 4, UiTheme.GOLD, bottom.w());
            for (int i = 0; i < prices.size(); i++) {
                CompoundTag p = prices.get(i);
                int price = p.getIntOr("price", 1);
                int base = p.getIntOr("base", 1);
                boolean up = price > base;
                ui.text(Component.translatable(up ? "villagelife.book.treasury.up" : "villagelife.book.treasury.down",
                        EconomyPage.itemName(p.getStringOr("item", "")), price, base), bottom.x(), bottom.y() + 16 + i * ROW,
                        up ? UiTheme.BAD : UiTheme.GOOD, bottom.w());
            }
        }
    }

    /** Seven bars, today on the right (brighter); days without a known value stay empty. */
    private static void chart(Ui ui, UiRect r, int[] days) {
        int bottom = r.bottom() - 10;
        int max = 1;
        for (int value : days) {
            max = Math.max(max, value);
        }
        ui.graphics().fill(r.x(), bottom, r.right(), bottom + 1, 0x55705A3A);
        int slot = r.w() / Math.max(1, days.length);
        for (int i = 0; i < days.length; i++) {
            int barX = r.x() + i * slot + 2;
            int barW = Math.max(2, slot - 4);
            boolean isToday = i == days.length - 1;
            if (days[i] >= 0) {
                int barTop = bottom - Math.round((bottom - r.y() - 2) * (days[i] / (float) max));
                ui.graphics().fill(barX, barTop, barX + barW, bottom, isToday ? 0xFF43A047 : 0xFF2E7D32);
                ui.tooltip(new UiRect(barX, r.y(), barW, bottom - r.y()), Component.translatable("villagelife.book.treasury.bar", days[i]));
            }
            Component label = isToday ? Component.translatable("villagelife.book.treasury.today") : Component.literal(String.valueOf(i - (days.length - 1)));
            ui.text(label, new UiRect(barX - 4, bottom + 1, barW + 8, 9), isToday ? UiTheme.INK : UiTheme.INK_DIM, 0);
        }
    }

    /** The goods furthest from their normal price first. */
    private static List<CompoundTag> prices(ListTag prices) {
        List<CompoundTag> list = new ArrayList<>();
        for (int i = 0; i < prices.size(); i++) {
            list.add(prices.getCompoundOrEmpty(i));
        }
        list.sort(Comparator.comparingDouble(p -> -Math.abs(Math.log(p.getIntOr("price", 1) / (double) Math.max(1, p.getIntOr("base", 1))))));
        return list.subList(0, Math.min(MAX_PRICES, list.size()));
    }

    boolean mouseScrolled(double x, double y, double amount) {
        return entries.mouseScrolled(x, y, amount);
    }

    boolean mouseDragged(double x, double y) {
        return entries.mouseDragged(x, y);
    }

    void mouseReleased() {
        entries.mouseReleased();
    }
}
