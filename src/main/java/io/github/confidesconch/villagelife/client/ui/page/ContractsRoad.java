package io.github.confidesconch.villagelife.client.ui.page;

import io.github.confidesconch.villagelife.client.ui.core.Ui;
import io.github.confidesconch.villagelife.client.ui.core.UiTheme;
import io.github.confidesconch.villagelife.market.contract.ContractRules;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;

/** Deliveries of a contract in "Contracts" (M21b7): pause, debts, misses, next delivery, goods on the road. */
final class ContractsRoad {
    private ContractsRoad() {}

    static int render(Ui ui, int x, int y, int w, CompoundTag c) {
        boolean offer = "offer".equals(c.getStringOr("status", ""));
        ListTag road = c.getListOrEmpty("shipments");
        if (offer && road.isEmpty()) {
            return y;
        }
        y = ContractsView.heading(ui, x, y, w, Component.translatable("villagelife.ui.economy.deliveries"));
        if (c.getBooleanOr("paused", false) && !offer) {
            y += ui.paragraph(Component.translatable("villagelife.book.contracts.paused"), x, y, w, UiTheme.BAD_INK);
        }
        int debt = c.getIntOr("debt", 0);
        if (debt > 0) {
            boolean weGive = c.getBooleanOr("we_give", true);
            y += ui.paragraph(Component.translatable(weGive ? "villagelife.book.contracts.debt_theirs" : "villagelife.book.contracts.debt_ours",
                    debt, EconomyPage.itemName(c.getStringOr("payment", ""))), x, y, w, UiTheme.BAD_INK);
        }
        int misses = c.getIntOr("misses", 0);
        if (misses > 0) {
            y += ui.paragraph(Component.translatable("villagelife.book.contracts.misses", misses, ContractRules.MISSES_TO_CANCEL),
                    x, y, w, UiTheme.BAD_INK);
        }
        if (c.contains("next_in") && "active".equals(c.getStringOr("status", ""))) {
            y += ui.paragraph(Component.translatable("villagelife.book.contracts.next", when(c.getLongOr("next_in", 0))), x, y, w, UiTheme.INK_DIM);
        }
        for (int i = 0; i < road.size(); i++) {
            CompoundTag s = road.getCompoundOrEmpty(i);
            Component name = EconomyPage.itemName(s.getStringOr("item", ""));
            if (s.getBooleanOr("waiting", false)) {
                y += ui.paragraph(Component.translatable("villagelife.book.contracts.road_waiting", s.getIntOr("count", 0), name),
                        x, y, w, UiTheme.BAD_INK);
                continue;
            }
            y += ui.paragraph(Component.translatable(s.getBooleanOr("to_us", false) ? "villagelife.book.contracts.road_in"
                    : "villagelife.book.contracts.road_out", s.getIntOr("count", 0), name, when(s.getLongOr("arrives_in", 0))),
                    x, y, w, UiTheme.INK);
        }
        return y + 3;
    }

    /** "in 3 h" below a day, else "in 2 days" (rounded up). */
    private static Component when(long ticks) {
        return ticks < 24000 ? Component.translatable("villagelife.book.contracts.in_hours", Math.max(1, (ticks + 999) / 1000))
                : Component.translatable("villagelife.book.contracts.in_days", (ticks + 23999) / 24000);
    }
}
