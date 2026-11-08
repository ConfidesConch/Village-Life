package io.github.confidesconch.villagelife.client.book;

import io.github.confidesconch.villagelife.VillageLife;
import io.github.confidesconch.villagelife.book.BookSystem;
import io.github.confidesconch.villagelife.book.OpenVillageBookPayload;
import io.github.confidesconch.villagelife.book.VillageBookMenu;
import io.github.confidesconch.villagelife.book.VillageBookPayload;
import io.github.confidesconch.villagelife.book.VillageSectionDataPayload;
import io.github.confidesconch.villagelife.claim.ClaimSyncPayload;
import io.github.confidesconch.villagelife.client.map.ClientClaims;
import io.github.confidesconch.villagelife.client.map.ClientFriends;
import io.github.confidesconch.villagelife.friend.FriendPositionsPayload;
import io.github.confidesconch.villagelife.friend.FriendSyncPayload;
import io.github.confidesconch.villagelife.client.map.ClientWaypoints;
import io.github.confidesconch.villagelife.client.map.MinimapLayer;
import io.github.confidesconch.villagelife.core.config.ClientConfig;
import io.github.confidesconch.villagelife.waypoint.WaypointSyncPayload;

import io.github.confidesconch.villagelife.client.ui.VillageUiClient;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client wiring of the village book: key, payload handler and the built-in chapters. */
public final class VillageBookClient {
    private VillageBookClient() {}

    public static void init(IEventBus modEventBus) {
        modEventBus.addListener(VillageBookClient::onRegisterKeys);
        modEventBus.addListener(VillageBookClient::onRegisterPayloadHandlers);
        modEventBus.addListener(VillageBookClient::onRegisterGuiLayers);
        modEventBus.addListener(VillageBookClient::onRegisterScreens);
        NeoForge.EVENT_BUS.addListener(VillageBookClient::onClientTick);

        VillageUiClient.init(); // M21b1: the new village window
    }

    private static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(Identifier.fromNamespaceAndPath(VillageLife.MOD_ID, "minimap"), new MinimapLayer());
    }

    private static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        event.registerCategory(VillageBookKeys.CATEGORY);
        event.register(VillageBookKeys.OPEN_BOOK);
        event.register(VillageBookKeys.TOGGLE_MINIMAP);
    }

    private static void onRegisterPayloadHandlers(RegisterClientPayloadHandlersEvent event) {
        event.register(VillageBookPayload.TYPE, VillageBookClient::onBookData);
        event.register(VillageSectionDataPayload.TYPE, VillageBookClient::onSectionData); // M21b2
        event.register(WaypointSyncPayload.TYPE, ClientWaypoints::onSync);
        event.register(ClaimSyncPayload.TYPE, ClientClaims::onSync);
        event.register(FriendSyncPayload.TYPE, ClientFriends::onSync);
        event.register(FriendPositionsPayload.TYPE, ClientFriends::onPositions);
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientWaypoints.tick(minecraft);
        ClientClaims.tick(minecraft);
        ClientFriends.tick(minecraft);
        while (VillageBookKeys.TOGGLE_MINIMAP.consumeClick()) {
            ClientConfig.SHOW_MINIMAP.set(!ClientConfig.SHOW_MINIMAP.get());
            ClientConfig.SHOW_MINIMAP.save();
        }
        while (VillageBookKeys.OPEN_BOOK.consumeClick()) {
            if (minecraft.player != null && minecraft.gui.screen() == null) {
                ClientPacketDistributor.sendToServer(OpenVillageBookPayload.WINDOW); // M21b1: the new village window
            }
        }
    }

    /** The server refreshed the open book (it was opened as a container, M11h); the screen picks it up. */
    private static void onBookData(VillageBookPayload payload, IPayloadContext context) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null && minecraft.player.containerMenu instanceof VillageBookMenu menu) {
            menu.setData(payload.data());
        }
    }

    /** M21b2: fresh data of some areas of the open village window; the rest of the snapshot stays. */
    private static void onSectionData(VillageSectionDataPayload payload, IPayloadContext context) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null && minecraft.player.containerMenu instanceof VillageBookMenu menu) {
            CompoundTag merged = menu.data().copy();
            if (!merged.getStringOr("village_id", "").equals(payload.data().getStringOr("village_id", ""))) {
                merged = new CompoundTag(); // another village: start fresh
            }
            for (String key : payload.data().keySet()) {
                merged.put(key, payload.data().get(key).copy());
            }
            merged.remove("none");
            menu.setData(merged);
        }
    }

    private static void onRegisterScreens(RegisterMenuScreensEvent event) {
        event.register(BookSystem.VILLAGE_BOOK_MENU.get(), VillageUiClient::screenFor); // M21b1: window or book
    }
}
