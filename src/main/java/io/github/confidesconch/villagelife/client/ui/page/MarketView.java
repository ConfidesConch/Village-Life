package io.github.confidesconch.villagelife.client.ui.page;

import io.github.confidesconch.villagelife.client.ui.component.ScrollList;
import io.github.confidesconch.villagelife.client.ui.core.Ui;
import io.github.confidesconch.villagelife.client.ui.core.UiRect;
import io.github.confidesconch.villagelife.client.ui.core.UiTheme;
import io.github.confidesconch.villagelife.market.MarketTradePayload;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * Tab "Market" (M21b7, the book's market chapter since M15a): whether you can trade right now, the
 * emeralds of village and player, what the village sells and buys (bundle → price, button; the button's
 * tooltip says what is missing) and the trading caravan.
 */
final class MarketView {
    private static final int ROW = 22;

    private final ScrollList sells = new ScrollList();
    private final ScrollList buys = new ScrollList();

    void render(Ui ui, UiRect area, CompoundTag m, String villageId) {
        String status = m.getStringOr("status", "no_market");
        boolean open = status.equals("ok");
        UiRect top = area.top(34);
        UiTheme.draw(ui.graphics(), UiTheme.PANEL, top);
        UiRect inner = top.inset(8, 4, 8, 4);
        ui.text(Component.translatable("villagelife.book.market.status." + status, m.getStringOr("market_pos", "?")),
                inner.x(), inner.y() + 1, open ? UiTheme.GOOD_INK : UiTheme.BAD_INK, inner.w());
        int playerEmeralds = m.getIntOr("player_emeralds", 0);
        ui.item(new ItemStack(Items.EMERALD), inner.x(), inner.y() + 10);
        ui.text(Component.translatable("villagelife.book.market.emeralds", m.getIntOr("village_emeralds", 0), playerEmeralds),
                inner.x() + 20, inner.y() + 15, UiTheme.INK, inner.w() - 20);

        UiRect rest = area.below(38);
        UiRect lists = rest.inset(0, 0, 0, 40);
        int half = (lists.w() - 6) / 2;
        column(ui, lists.left(half), sells, m.getListOrEmpty("offers"), true, open, playerEmeralds, villageId);
        column(ui, lists.rightOf(half + 6), buys, m.getListOrEmpty("wants"), false, open, playerEmeralds, villageId);
        caravan(ui, rest.bottomPart(36), m);
    }

    private static void column(Ui ui, UiRect r, ScrollList list, ListTag entries, boolean villageSells, boolean open,
                               int playerEmeralds, String villageId) {
        UiTheme.draw(ui.graphics(), UiTheme.PANEL_DARK, r);
        UiRect inner = r.inset(5);
        ui.text(Component.translatable(villageSells ? "villagelife.book.market.sells" : "villagelife.book.market.buys"),
                inner.x(), inner.y() + 1, UiTheme.GOLD, inner.w());
        UiRect rows = inner.below(14);
        if (entries.isEmpty()) {
            ui.paragraph(Component.translatable(villageSells ? "villagelife.book.market.nothing_to_sell"
                    : "villagelife.book.market.nothing_wanted"), rows.x(), rows.y() + 2, rows.w(), UiTheme.TEXT_DIM);
            return;
        }
        list.render(ui, rows, entries.size(), ROW, (u, index, row) -> {
            CompoundTag e = entries.getCompoundOrEmpty(index);
            String item = e.getStringOr("item", "");
            int bundle = e.getIntOr("count", 1);
            int price = e.getIntOr("price", 1);
            int has = villageSells ? playerEmeralds : e.getIntOr("player_has", 0);
            int needed = villageSells ? price : bundle;
            boolean possible = open && has >= needed;
            int y = row.y() + 2;
            u.slot(EconomyPage.stack(item, bundle), row.x(), y);
            u.graphics().text(u.font(), "→", row.x() + 21, y + 5, UiTheme.TEXT_DIM, false);
            u.slot(new ItemStack(Items.EMERALD, Math.max(1, Math.min(99, price))), row.x() + 30, y);
            UiRect button = new UiRect(row.x() + 54, y + 1, Math.max(0, row.w() - 56), 16);
            Component label = Component.translatable(villageSells ? "villagelife.book.market.buy" : "villagelife.book.market.sell");
            u.button(button, label, possible, () -> ClientPacketDistributor.sendToServer(new MarketTradePayload(villageId, item, villageSells)));
            if (!possible && open) {
                u.tooltip(button, Component.translatable(villageSells ? "villagelife.book.market.need_emeralds"
                        : "villagelife.book.market.need_items", needed, has));
            } else if (!open) {
                u.tooltip(button, Component.translatable("villagelife.ui.economy.closed"));
            }
        });
    }

    /** The trading caravan (M15b): next visit and what the last one traded. */
    private static void caravan(Ui ui, UiRect r, CompoundTag m) {
        UiTheme.draw(ui.graphics(), UiTheme.PANEL, r);
        UiRect inner = r.inset(8, 4, 8, 4);
        ui.text(Component.translatable("villagelife.book.market.caravan"), inner.x(), inner.y() + 1, UiTheme.INK, inner.w());
        if (!m.contains("caravan_in")) {
            ui.text(Component.translatable("villagelife.ui.economy.no_caravan"), inner.x(), inner.y() + 13, UiTheme.INK_DIM, inner.w());
            return;
        }
        long in = m.getLongOr("caravan_in", 0);
        int days = (int) ((in + 23999) / 24000);
        Component next = m.getBooleanOr("caravan_here", false) ? Component.translatable("villagelife.book.market.caravan_here")
                : days == 0 ? Component.translatable("villagelife.book.market.caravan_morning")
                : Component.translatable(days == 1 ? "villagelife.book.market.caravan_day" : "villagelife.book.market.caravan_days", days);
        CompoundTag last = m.getCompoundOrEmpty("caravan_last");
        Component line = last.isEmpty() ? next : next.copy().append(" ").append(Component.translatable("villagelife.book.market.caravan_last",
                last.getIntOr("sold", 0), last.getIntOr("earned", 0), last.getIntOr("bought", 0), last.getIntOr("spent", 0)));
        ui.text(line, inner.x(), inner.y() + 13, UiTheme.INK_DIM, inner.w());
    }

    boolean mouseScrolled(double x, double y, double amount) {
        return sells.mouseScrolled(x, y, amount) || buys.mouseScrolled(x, y, amount);
    }

    boolean mouseDragged(double x, double y) {
        return sells.mouseDragged(x, y) || buys.mouseDragged(x, y);
    }

    void mouseReleased() {
        sells.mouseReleased();
        buys.mouseReleased();
    }
}
