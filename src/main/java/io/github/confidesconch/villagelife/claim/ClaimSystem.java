package io.github.confidesconch.villagelife.claim;

import io.github.confidesconch.villagelife.core.network.ModNetwork;

import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Wires the claims into the game (M11f): payloads and the block protection (runs before other listeners). */
public final class ClaimSystem {
    private ClaimSystem() {}

    public static void init(IEventBus modEventBus) {
        modEventBus.addListener(ClaimSystem::onRegisterPayloads);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, ClaimService::onBreak);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, ClaimService::onPlace);
    }

    private static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(ModNetwork.PROTOCOL_VERSION);
        registrar.playToServer(ClaimActionPayload.TYPE, ClaimActionPayload.STREAM_CODEC, ClaimService::onAction);
        // the client handler is registered in client.map (RegisterClientPayloadHandlersEvent)
        registrar.playToClient(ClaimSyncPayload.TYPE, ClaimSyncPayload.STREAM_CODEC);
    }
}
