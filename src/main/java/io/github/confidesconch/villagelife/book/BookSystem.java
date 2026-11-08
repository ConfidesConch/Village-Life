package io.github.confidesconch.villagelife.book;

import io.github.confidesconch.villagelife.VillageLife;
import io.github.confidesconch.villagelife.core.network.ModNetwork;
import io.github.confidesconch.villagelife.friend.FriendService;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Wires the village book into the game (M11a): the book item, the network payloads, the
 * village-center right-click and the built-in chapter sections. The screen itself is client-only
 * ({@code client.book}).
 */
public final class BookSystem {
    private BookSystem() {}

    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, VillageLife.MOD_ID);

    /** The open book is a container (M11h), so the player's inventory can be used inside it. */
    public static final DeferredHolder<MenuType<?>, MenuType<VillageBookMenu>> VILLAGE_BOOK_MENU =
            MENUS.register("village_book", () -> IMenuTypeExtension.create(VillageBookMenu::fromNetwork));


    /** Call from the mod constructor, before the registries fire. */
    public static void init(IEventBus modEventBus) {
        modEventBus.addListener(BookSystem::onRegisterPayloads);
        MENUS.register(modEventBus);
        NeoForge.EVENT_BUS.addListener(VillageBookService::onRightClickBlock);
        NeoForge.EVENT_BUS.addListener(VillageBookService::onRightClickMarker);
        NeoForge.EVENT_BUS.addListener(VillageBookService::onInteractResident);
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent.class,
                event -> VillageBookService.forget(event.getEntity().getUUID())); // M21b2
        FriendService.onFriendsChanged(SettingsSection::dropFormerFriends); // managers must stay friends (M11g2)
        BookSections.register("overview", OverviewSection::write);
        BookSections.register("residents", ResidentsSection::write);
        BookSections.register("buildings", BuildingsSection::write);
        BookSections.register("problems", ProblemsSection::write);
        BookSections.register("events", EventsSection::write); // M21b3, window only
        BookSections.register("defense", DefenseSection::write); // M21b9, window only
        BookSections.register("professions", ProfessionsSection::write); // M13b
        BookSections.register("settings", SettingsSection::write);
        BookSections.register("management", ManagementSection::write); // M19a
        BookSections.registerGlobal("character", CharacterSection::write);
        BookSections.register("zones", ZonesSection::write);
    }

    private static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(ModNetwork.PROTOCOL_VERSION);
        registrar.playToServer(OpenVillageBookPayload.TYPE, OpenVillageBookPayload.STREAM_CODEC,
                VillageBookService::onOpenRequest);
        registrar.playToServer(UpdateVillageSettingsPayload.TYPE, UpdateVillageSettingsPayload.STREAM_CODEC,
                SettingsSection::onUpdate);
        registrar.playToServer(BuildingActionPayload.TYPE, BuildingActionPayload.STREAM_CODEC, BuildingsSection::onAction);
        registrar.playToServer(EditVillageZonePayload.TYPE, EditVillageZonePayload.STREAM_CODEC, ZonesSection::onEdit);
        // the client handler is registered in client.book (RegisterClientPayloadHandlersEvent)
        registrar.playToClient(VillageBookPayload.TYPE, VillageBookPayload.STREAM_CODEC);
        registrar.playToServer(VillageSectionRequestPayload.TYPE, VillageSectionRequestPayload.STREAM_CODEC,
                VillageBookService::onSectionRequest); // M21b2
        registrar.playToClient(VillageSectionDataPayload.TYPE, VillageSectionDataPayload.STREAM_CODEC);
    }
}
