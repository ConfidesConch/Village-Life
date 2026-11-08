package io.github.confidesconch.villagelife.client.ui;

import io.github.confidesconch.villagelife.book.VillageBookMenu;
import io.github.confidesconch.villagelife.client.ui.core.UiPages;
import io.github.confidesconch.villagelife.client.ui.core.VillageScreen;
import io.github.confidesconch.villagelife.client.ui.page.AdminPage;
import io.github.confidesconch.villagelife.client.ui.page.BuildingsPage;
import io.github.confidesconch.villagelife.client.ui.page.CitizensPage;
import io.github.confidesconch.villagelife.client.ui.page.DefensePage;
import io.github.confidesconch.villagelife.client.ui.page.EconomyPage;
import io.github.confidesconch.villagelife.client.ui.page.JobsPage;
import io.github.confidesconch.villagelife.client.ui.page.MapPage;
import io.github.confidesconch.villagelife.client.ui.page.PlayerPage;
import io.github.confidesconch.villagelife.client.ui.page.OverviewPage;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Client wiring of the village window (M21b1): its nine areas and the choice between window and book
 * when the server opens the village menu. Until M21b10 the book stays reachable (book item, right
 * clicks in the world); the book key opens the window.
 */
public final class VillageUiClient {
    private VillageUiClient() {}

    public static void init() {
        UiPages.register(new OverviewPage()); // M21b3
        UiPages.register(new CitizensPage()); // M21b4
        UiPages.register(new JobsPage()); // M21b6
        UiPages.register(new BuildingsPage()); // M21b5
        UiPages.register(new EconomyPage()); // M21b7
        UiPages.register(new DefensePage()); // M21b9
        UiPages.register(new MapPage()); // M21b8
        UiPages.register(new AdminPage()); // M21b10a
        UiPages.register(new PlayerPage()); // M21b10a
    }

    /** The server opened the village menu: window (book key) or book (everything else, until M21b10). */
    public static AbstractContainerScreen<VillageBookMenu> screenFor(VillageBookMenu menu, Inventory inventory, Component title) {
        return new VillageScreen(menu, inventory, title); // M21b10b: the book is gone
    }
}
