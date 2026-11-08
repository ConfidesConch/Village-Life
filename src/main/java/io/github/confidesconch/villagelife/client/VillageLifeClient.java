package io.github.confidesconch.villagelife.client;

import io.github.confidesconch.villagelife.VillageLife;
import io.github.confidesconch.villagelife.client.book.VillageBookClient;
import io.github.confidesconch.villagelife.client.map.MapMemory;
import io.github.confidesconch.villagelife.core.config.ClientConfig;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ChunkEvent;
import io.github.confidesconch.villagelife.core.registry.ModEntities;

/**
 * Client-only entry point. NeoForge loads this class only on the physical client, so it is the
 * single place where rendering, screens and key bindings get registered.
 */
@Mod(value = VillageLife.MOD_ID, dist = Dist.CLIENT)
public final class VillageLifeClient {

    public VillageLifeClient(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
        // Adds the "Config" button for Village Life in the in-game mod list
        modContainer.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        modEventBus.addListener(this::onClientSetup);
        modEventBus.addListener(this::onRegisterRenderers);
        // M21c: resident skins are painted from resource-pack layers – paint them again after a reload
        modEventBus.addListener(net.neoforged.neoforge.client.event.AddClientReloadListenersEvent.class, event -> event.addListener(
                net.minecraft.resources.Identifier.fromNamespaceAndPath(VillageLife.MOD_ID, "resident_skins"),
                (net.minecraft.server.packs.resources.ResourceManagerReloadListener) manager -> ResidentSkins.clear()));
        VillageBookClient.init(modEventBus);
        registerMapMemory();
    }

    /** 1.0.1: the map remembers explored areas (client only, the singleplayer server's chunks are ignored). */
    private static void registerMapMemory() {
        IEventBus game = NeoForge.EVENT_BUS;
        game.addListener(ClientPlayerNetworkEvent.LoggingIn.class, event -> MapMemory.onLogin());
        game.addListener(ClientPlayerNetworkEvent.LoggingOut.class, event -> MapMemory.onLogout());
        game.addListener(ChunkEvent.Load.class, event -> {
            if (event.getLevel().isClientSide()) {
                MapMemory.onChunkLoad(event.getChunk());
            }
        });
        game.addListener(ChunkEvent.Unload.class, event -> {
            if (event.getLevel().isClientSide()) {
                MapMemory.onChunkUnload(event.getChunk());
            }
        });
    }

    private void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.RESIDENT.get(), ResidentRenderer::new);
        event.registerEntityRenderer(ModEntities.SEAT.get(), net.minecraft.client.renderer.entity.NoopRenderer::new); // M21e
    }

    private void onClientSetup(FMLClientSetupEvent event) {
        VillageLife.LOGGER.info("Village Life client setup complete");
    }
}
