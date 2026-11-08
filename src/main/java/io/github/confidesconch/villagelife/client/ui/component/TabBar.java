package io.github.confidesconch.villagelife.client.ui.component;

import java.util.List;
import java.util.function.IntConsumer;

import io.github.confidesconch.villagelife.client.ui.core.Ui;
import io.github.confidesconch.villagelife.client.ui.core.UiRect;
import io.github.confidesconch.villagelife.client.ui.core.UiTheme;

import net.minecraft.network.chat.Component;

/** Sub-tabs at the top of an area (M21b1): the chosen one gold and underlined. */
public final class TabBar {
    private TabBar() {}

    private static final int GAP = 12;

    /** Draws the tabs in a row; returns the rectangle below the bar. */
    public static UiRect render(Ui ui, UiRect area, List<Component> tabs, int selected, IntConsumer onSelect) {
        UiRect bar = area.top(16);
        int x = bar.x();
        int total = GAP * (tabs.size() - 1);
        for (Component tab : tabs) {
            total += ui.font().width(tab) + 4;
        }
        boolean squeeze = total > bar.w(); // M21b8: too narrow – equal tabs, long names cut with "…"
        int gap = squeeze ? 4 : GAP;
        int even = squeeze ? (bar.w() - gap * (tabs.size() - 1)) / Math.max(1, tabs.size()) : 0;
        for (int i = 0; i < tabs.size(); i++) {
            int width = squeeze ? even : ui.font().width(tabs.get(i)) + 4;
            UiRect tab = new UiRect(x, bar.y(), width, 14);
            boolean chosen = i == selected;
            ui.text(tabs.get(i), tab, chosen ? UiTheme.GOLD : ui.hovered(tab) ? UiTheme.TEXT : UiTheme.TEXT_DIM, 0);
            if (chosen) {
                ui.graphics().fill(tab.x(), tab.bottom(), tab.right(), tab.bottom() + 1, UiTheme.GOLD);
            } else {
                int index = i;
                ui.clickable(tab, () -> onSelect.accept(index));
            }
            x += width + gap;
        }
        return area.below(20);
    }
}
