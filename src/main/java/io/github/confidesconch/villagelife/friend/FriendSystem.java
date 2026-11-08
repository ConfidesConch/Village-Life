package io.github.confidesconch.villagelife.friend;

import io.github.confidesconch.villagelife.core.network.ModNetwork;
import io.github.confidesconch.villagelife.core.util.ModProfiler;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Wires the friends into the game (M11g): payloads and the position updates. */
public final class FriendSystem {
    private FriendSystem() {}

    public static void init(IEventBus modEventBus) {
        modEventBus.addListener(FriendSystem::onRegisterPayloads);
        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class,
                event -> ModProfiler.time(ModProfiler.FRIENDS, () -> FriendService.onServerTick(event)));
    }

    private static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(ModNetwork.PROTOCOL_VERSION);
        registrar.playToServer(FriendActionPayload.TYPE, FriendActionPayload.STREAM_CODEC, FriendService::onAction);
        // the client handlers are registered in client.map (RegisterClientPayloadHandlersEvent)
        registrar.playToClient(FriendSyncPayload.TYPE, FriendSyncPayload.STREAM_CODEC);
        registrar.playToClient(FriendPositionsPayload.TYPE, FriendPositionsPayload.STREAM_CODEC);
    }
}
