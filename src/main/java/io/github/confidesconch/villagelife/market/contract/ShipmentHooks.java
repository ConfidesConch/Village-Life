package io.github.confidesconch.villagelife.market.contract;

import java.util.function.BiFunction;

import net.minecraft.server.MinecraftServer;

/**
 * Lets the convoy system (M20c, a higher package) decide about a shipment when it leaves: escorts and
 * whether it will be raided on its way; and just before it arrives (a raid that is still due happens
 * first). Without it shipments travel as before.
 */
public final class ShipmentHooks {
    private ShipmentHooks() {}

    private static volatile BiFunction<MinecraftServer, Shipment, Shipment> onDispatch = (server, shipment) -> shipment;
    private static volatile BiFunction<MinecraftServer, Shipment, Shipment> onArrival = (server, shipment) -> shipment;

    /** Called before a shipment is unloaded; returns it as it arrives (e.g. after a raid). */
    public static void setArrivalListener(BiFunction<MinecraftServer, Shipment, Shipment> listener) {
        onArrival = listener;
    }

    static Shipment arriving(MinecraftServer server, Shipment shipment) {
        return onArrival.apply(server, shipment);
    }

    public static void setDispatchListener(BiFunction<MinecraftServer, Shipment, Shipment> listener) {
        onDispatch = listener;
    }

    static Shipment dispatched(MinecraftServer server, Shipment shipment) {
        return onDispatch.apply(server, shipment);
    }
}
