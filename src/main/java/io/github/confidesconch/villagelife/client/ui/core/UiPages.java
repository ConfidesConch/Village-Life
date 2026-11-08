package io.github.confidesconch.villagelife.client.ui.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** All areas of the village window, sorted by {@link UiPage#order()} (M21b1). Client only. */
public final class UiPages {
    private UiPages() {}

    private static final List<UiPage> PAGES = new ArrayList<>();

    public static synchronized void register(UiPage page) {
        if (PAGES.stream().anyMatch(p -> p.id().equals(page.id()))) {
            throw new IllegalStateException("Village window page registered twice: " + page.id());
        }
        PAGES.add(page);
        PAGES.sort(Comparator.comparingInt(UiPage::order));
    }

    public static List<UiPage> all() {
        return List.copyOf(PAGES);
    }
}
