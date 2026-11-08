package io.github.confidesconch.villagelife.convoy;

import io.github.confidesconch.villagelife.core.util.ModProfiler;
import io.github.confidesconch.villagelife.book.BookSections;
import io.github.confidesconch.villagelife.core.network.ModNetwork;
import io.github.confidesconch.villagelife.market.contract.ShipmentHooks;
import io.github.confidesconch.villagelife.resident.ai.ResidentHooks;

import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Wires up raids on trade shipments and their escorts (M20c). */
public final class ConvoySystem {
    private ConvoySystem() {}

    /** Before work (4) and sleep (3), after fighting (1). */
    private static final int ESCORT_PRIORITY = 2;

    public static void init(IEventBus modEventBus) {
        ShipmentHooks.setDispatchListener(ConvoyService::dispatched);
        ShipmentHooks.setArrivalListener(ConvoyService::rolled);
        ResidentHooks.setEscortCheck(resident -> resident.level() instanceof ServerLevel level
                && ConvoyStore.get(level.getServer()).isAway(resident.getUUID()));
        ResidentHooks.addGoalProvider(resident -> resident.goalSelector.addGoal(ESCORT_PRIORITY, new EscortGoal(resident, 0.7)));
        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class,
                event -> ModProfiler.time(ModProfiler.VILLAGE_LIFE, () -> ConvoyService.onServerTick(event)));
        NeoForge.EVENT_BUS.addListener(ConvoyWagons::onJoin);
        NeoForge.EVENT_BUS.addListener(ConvoyWagons::onIncomingDamage);
        NeoForge.EVENT_BUS.addListener(ServerStoppingEvent.class, event -> ConvoyWagons.stopAll(event.getServer()));
        BookSections.register("convoy", ConvoyService::write);
        modEventBus.addListener(ConvoySystem::onRegisterPayloads);
    }

    private static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar(ModNetwork.PROTOCOL_VERSION)
                .playToServer(ConvoyEscortPayload.TYPE, ConvoyEscortPayload.STREAM_CODEC, ConvoyService::onEscort);
    }
}
