package io.github.confidesconch.villagelife.market;

import io.github.confidesconch.villagelife.core.util.ModProfiler;
import io.github.confidesconch.villagelife.book.BookSections;
import io.github.confidesconch.villagelife.core.network.ModNetwork;
import io.github.confidesconch.villagelife.market.contract.ContractActionPayload;
import io.github.confidesconch.villagelife.market.contract.ContractService;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Wires up the market (M15a): goods from data packs, the book section, the trade packet, the caravans (M15b) and the trade contracts (M15d). */
public final class MarketSystem {
    private MarketSystem() {}

    public static void init(IEventBus modEventBus) {
        NeoForge.EVENT_BUS.addListener(MarketGoods::onAddReloadListeners);
        BookSections.register("market", MarketService::write);
        modEventBus.addListener(MarketSystem::onRegisterPayloads);
        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class,
                event -> ModProfiler.time(ModProfiler.VILLAGE_LIFE, () -> CaravanService.onServerTick(event)));
        BookSections.register("contracts", ContractService::write);
        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class,
                event -> ModProfiler.time(ModProfiler.VILLAGE_LIFE, () -> ContractService.onServerTick(event)));
    }

    private static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(ModNetwork.PROTOCOL_VERSION);
        registrar.playToServer(MarketTradePayload.TYPE, MarketTradePayload.STREAM_CODEC, MarketService::onTrade);
        registrar.playToServer(ContractActionPayload.TYPE, ContractActionPayload.STREAM_CODEC, ContractService::onAction);
    }
}
