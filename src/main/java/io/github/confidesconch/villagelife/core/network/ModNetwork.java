package io.github.confidesconch.villagelife.core.network;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Registers all network payloads of Village Life.
 * Rules (see CLAUDE.md): payloads end with {@code Payload}, use a StreamCodec, and every
 * client-to-server payload is validated on the server (permission, distance, value ranges).
 */
public final class ModNetwork {
    private ModNetwork() {}

    /** Increase when payloads change incompatibly; client and server must match. */
    public static final String PROTOCOL_VERSION = "1";

    public static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);
        // Payloads are registered here from M3 on, e.g.:
        // registrar.playToServer(SetBuildingTypePayload.TYPE, SetBuildingTypePayload.STREAM_CODEC, handler);
    }
}
