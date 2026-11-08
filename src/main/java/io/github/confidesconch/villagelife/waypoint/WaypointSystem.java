package io.github.confidesconch.villagelife.waypoint;

import io.github.confidesconch.villagelife.core.network.ModNetwork;
import io.github.confidesconch.villagelife.friend.FriendService;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Wires the waypoints into the game (M11e): network payloads. Map drawing lives in {@code client.map}. */
public final class WaypointSystem {
    private WaypointSystem() {}

    public static void init(IEventBus modEventBus) {
        modEventBus.addListener(WaypointSystem::onRegisterPayloads);
        FriendService.onFriendsChanged(WaypointService::sendTo); // shared waypoints appear/disappear
    }

    private static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(ModNetwork.PROTOCOL_VERSION);
        registrar.playToServer(WaypointActionPayload.TYPE, WaypointActionPayload.STREAM_CODEC, WaypointService::onAction);
        // the client handler is registered in client.map (RegisterClientPayloadHandlersEvent)
        registrar.playToClient(WaypointSyncPayload.TYPE, WaypointSyncPayload.STREAM_CODEC);
    }
}
