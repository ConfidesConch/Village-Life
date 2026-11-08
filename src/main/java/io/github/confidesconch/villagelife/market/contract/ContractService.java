package io.github.confidesconch.villagelife.market.contract;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import io.github.confidesconch.villagelife.book.VillageBookService;
import io.github.confidesconch.villagelife.market.MarketGood;
import io.github.confidesconch.villagelife.market.MarketGoods;
import io.github.confidesconch.villagelife.market.MarketService;
import io.github.confidesconch.villagelife.market.contract.TradeContract.Status;
import io.github.confidesconch.villagelife.market.treasury.TreasuryStore;
import io.github.confidesconch.villagelife.village.Village;
import io.github.confidesconch.villagelife.village.NotificationTopic;
import io.github.confidesconch.villagelife.village.VillageService;
import io.github.confidesconch.villagelife.village.VillageManager;
import io.github.confidesconch.villagelife.village.VillageStages;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Trade contracts between villages (M15d1, owner's choice "1a 2a 3a 4a"): owners and managers offer
 * contracts to other villages with a market (also of other players), the other side accepts, declines
 * or counters, and either side can cancel with one day's notice. Deliveries: {@link ContractDeliveries} (M15d2).
 */
public final class ContractService {
    private ContractService() {}

    public static final String EMERALD = "minecraft:emerald";
    private static final int CLEANUP_INTERVAL_TICKS = 200;

    // ---- book section --------------------------------------------------------------------------

    /** Section "contracts": possible partners, tradable goods and the village's contracts. */
    public static void write(ServerPlayer player, Village village, CompoundTag out) {
        MinecraftServer server = player.level().getServer();
        boolean manage = village.mayManage(player.getUUID());
        out.putBoolean("can_manage", manage);
        out.putBoolean("has_market", MarketService.market(player.level(), village).isPresent());
        out.putInt("max_contracts", VillageStages.maxContracts(village.stage())); // M16: grows with the stage
        if (!manage) {
            return;
        }
        ListTag partners = new ListTag();
        for (ServerLevel level : server.getAllLevels()) {
            for (Village other : VillageManager.get(level).all()) {
                if (!other.id().equals(village.id()) && MarketService.market(level, other).isPresent()) {
                    partners.add(names(other));
                }
            }
        }
        out.put("partners", partners);
        ListTag goods = new ListTag();
        for (MarketGood good : MarketGoods.all()) {
            CompoundTag g = new CompoundTag();
            g.putString("item", good.item().toString());
            goods.add(g);
        }
        out.put("goods", goods);
        long now = server.overworld().getGameTime();
        ListTag contracts = new ListTag();
        for (TradeContract contract : ContractStore.get(server).of(village.id())) {
            contracts.add(entry(server, village, contract, now));
        }
        out.put("contracts", contracts);
    }

    private static CompoundTag names(Village village) {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", village.id().toString());
        tag.putString("name", village.customName().orElse(""));
        tag.putString("owner_name", village.ownerName());
        return tag;
    }

    private static CompoundTag entry(MinecraftServer server, Village village, TradeContract contract, long now) {
        UUID partnerId = contract.partnerOf(village.id());
        CompoundTag tag = find(server, partnerId).map(found -> names(found.village())).orElseGet(() -> {
            CompoundTag unknown = new CompoundTag();
            unknown.putString("id", partnerId.toString());
            unknown.putString("name", "?");
            return unknown;
        });
        tag.putInt("contract", contract.id());
        tag.putBoolean("we_give", contract.giver().equals(village.id()));
        ContractTerms t = contract.terms();
        tag.putString("good", t.good());
        tag.putInt("percent", t.percent());
        tag.putInt("max", t.max());
        tag.putInt("days", t.days());
        tag.putString("payment", t.payment());
        tag.putInt("payment_count", t.paymentCount());
        tag.putString("status", contract.status().name().toLowerCase(java.util.Locale.ROOT));
        tag.putBoolean("my_turn", contract.isTurnOf(village.id()));
        tag.putLong("ends_in", Math.max(0, contract.endsAt() - now));
        if (contract.status() != Status.OFFER) {
            tag.putLong("next_in", Math.max(0, contract.nextDelivery() - now));
            boolean open = find(server, contract.giver()).map(ContractDeliveries::isOpen).orElse(false)
                    && find(server, contract.receiver()).map(ContractDeliveries::isOpen).orElse(false);
            tag.putBoolean("paused", !open);
            tag.putInt("debt", contract.debt());
            tag.putInt("misses", Math.max(contract.deliveryMisses(), contract.paymentMisses()));
        }
        ListTag road = new ListTag();
        for (Shipment shipment : ContractStore.get(server).shipments()) {
            if (shipment.contractId() == contract.id()) {
                CompoundTag s = new CompoundTag();
                s.putString("item", shipment.item());
                s.putInt("count", shipment.count());
                s.putBoolean("to_us", shipment.to().equals(village.id()));
                s.putLong("arrives_in", Math.max(0, shipment.arrivesAt() - now));
                s.putBoolean("waiting", shipment.waiting()); // M20c
                road.add(s);
            }
        }
        tag.put("shipments", road);
        return tag;
    }

    // ---- actions -------------------------------------------------------------------------------

    public static void onAction(ContractActionPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        MinecraftServer server = player.level().getServer();
        Optional<Found> own = parse(payload.villageId()).flatMap(id -> find(server, id));
        if (own.isEmpty()) {
            return;
        }
        Village village = own.get().village();
        if (village.mayManage(player.getUUID())) {
            Component problem = switch (payload.action()) {
                case "offer" -> offer(server, village, payload);
                case "accept", "decline", "counter", "cancel" -> answer(server, village, payload);
                default -> null;
            };
            if (problem != null) {
                player.sendSystemMessage(problem);
            }
        }
        VillageBookService.open(player, village, null, "contracts");
    }

    /** A new offer; returns a problem to show, or null. */
    private static Component offer(MinecraftServer server, Village village, ContractActionPayload payload) {
        ContractStore store = ContractStore.get(server);
        Optional<Found> partner = parse(payload.partner()).flatMap(id -> find(server, id));
        if (partner.isEmpty() || partner.get().village().id().equals(village.id())
                || MarketService.market(partner.get().level(), partner.get().village()).isEmpty()) {
            return Component.translatable("villagelife.contract.problem.partner");
        }
        if (!isTradable(payload.terms())) {
            return Component.translatable("villagelife.contract.problem.terms");
        }
        Village other = partner.get().village();
        if (store.of(village.id()).size() >= VillageStages.maxContracts(village.stage())
                || store.of(other.id()).size() >= VillageStages.maxContracts(other.stage())) {
            return Component.translatable("villagelife.contract.problem.too_many");
        }
        UUID giver = payload.weGive() ? village.id() : other.id();
        UUID receiver = payload.weGive() ? other.id() : village.id();
        store.put(TradeContract.offer(store.newId(), giver, receiver, payload.terms(), other.id()));
        VillageService.tellManagers(server, other, NotificationTopic.TRADE, Component.translatable("villagelife.contract.offered", village.displayName()));
        return null;
    }

    /** Accept, decline, counter or cancel an existing contract; returns a problem to show, or null. */
    private static Component answer(MinecraftServer server, Village village, ContractActionPayload payload) {
        ContractStore store = ContractStore.get(server);
        Optional<TradeContract> found = store.byId(payload.contractId()).filter(c -> c.involves(village.id()));
        if (found.isEmpty()) {
            return Component.translatable("villagelife.contract.problem.gone");
        }
        TradeContract contract = found.get();
        UUID partnerId = contract.partnerOf(village.id());
        long now = server.overworld().getGameTime();
        String message;
        switch (payload.action()) {
            case "accept" -> {
                if (!contract.isTurnOf(village.id())) {
                    return Component.translatable("villagelife.contract.problem.not_your_turn");
                }
                store.put(contract.accepted(now + (long) contract.terms().days() * ContractRules.DAY_TICKS));
                message = "villagelife.contract.accepted";
            }
            case "decline" -> {
                if (contract.status() != Status.OFFER) {
                    return Component.translatable("villagelife.contract.problem.not_your_turn");
                }
                store.remove(contract.id());
                message = "villagelife.contract.declined";
            }
            case "counter" -> {
                if (!contract.isTurnOf(village.id())) {
                    return Component.translatable("villagelife.contract.problem.not_your_turn");
                }
                if (!isTradable(payload.terms())) {
                    return Component.translatable("villagelife.contract.problem.terms");
                }
                UUID giver = payload.weGive() ? village.id() : partnerId;
                UUID receiver = payload.weGive() ? partnerId : village.id();
                store.put(contract.countered(giver, receiver, payload.terms(), partnerId));
                message = "villagelife.contract.countered";
            }
            default -> { // cancel
                if (contract.status() != Status.ACTIVE) {
                    return Component.translatable("villagelife.contract.problem.not_active");
                }
                store.put(contract.ending(now + ContractRules.NOTICE_TICKS));
                message = "villagelife.contract.cancelled";
            }
        }
        find(server, partnerId).ifPresent(partner ->
                VillageService.tellManagers(server, partner.village(), NotificationTopic.TRADE, Component.translatable(message, village.displayName())));
        return null;
    }

    /** Valid terms whose good is a market good and whose payment is a market good or emeralds. */
    private static boolean isTradable(ContractTerms terms) {
        Set<String> goods = new HashSet<>();
        MarketGoods.all().forEach(good -> goods.add(good.item().toString()));
        return ContractRules.isValid(terms) && goods.contains(terms.good())
                && (goods.contains(terms.payment()) || EMERALD.equals(terms.payment()));
    }

    // ---- housekeeping --------------------------------------------------------------------------

    /**
     * Every 10 s: deliveries and arrivals (M15d2); ends cancelled contracts after the notice period once
     * nothing of them is on the road any more; forgets contracts and shipments of vanished villages.
     */
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        long now = server.overworld().getGameTime();
        if (now % CLEANUP_INTERVAL_TICKS != 0) {
            return;
        }
        Set<UUID> villages = new HashSet<>();
        for (ServerLevel level : server.getAllLevels()) {
            VillageManager.get(level).all().forEach(v -> villages.add(v.id()));
        }
        if (now % ContractRules.DAY_TICKS == 0) {
            TreasuryStore.get(server).retain(villages); // M15c: forget the books of vanished villages
        }
        ContractStore store = ContractStore.get(server);
        ContractDeliveries.returnOrphans(server, store, villages, now);
        ContractDeliveries.tick(server, store, now);
        for (TradeContract c : store.all()) {
            boolean giverGone = !villages.contains(c.giver());
            if (giverGone != !villages.contains(c.receiver())) { // exactly one side is gone: tell the other
                find(server, giverGone ? c.receiver() : c.giver()).ifPresent(left -> VillageService.tellManagers(server,
                        left.village(), NotificationTopic.TRADE, Component.translatable("villagelife.contract.partner_gone")));
            }
        }
        Set<Integer> onTheRoad = new HashSet<>();
        store.shipments().forEach(s -> onTheRoad.add(s.contractId()));
        store.removeIf(c -> (c.status() == Status.ENDING && now >= c.endsAt() && !onTheRoad.contains(c.id()))
                || !villages.contains(c.giver()) || !villages.contains(c.receiver()));
    }

    /** Debug ({@code /villagelife debug contracts}): the village's deliveries are due and its shipments arrive now. */
    public static int hurry(MinecraftServer server, UUID village) {
        return ContractDeliveries.hurry(ContractStore.get(server), village, server.overworld().getGameTime());
    }

    /** Debug ({@code /villagelife debug convoy}, M20c): the village's shipments on the road (null: all) are raided in about a minute. */
    public static int raidSoon(MinecraftServer server, UUID village) {
        return ContractDeliveries.raidSoon(ContractStore.get(server), village, server.overworld().getGameTime());
    }

    // ---- finding villages across dimensions ----------------------------------------------------

    public record Found(ServerLevel level, Village village) {}

    public static Optional<Found> find(MinecraftServer server, UUID id) {
        for (ServerLevel level : server.getAllLevels()) {
            Optional<Village> village = VillageManager.get(level).byId(id);
            if (village.isPresent()) {
                return Optional.of(new Found(level, village.get()));
            }
        }
        return Optional.empty();
    }

    private static Optional<UUID> parse(String id) {
        try {
            return Optional.of(UUID.fromString(id));
        } catch (IllegalArgumentException invalid) {
            return Optional.empty();
        }
    }
}
