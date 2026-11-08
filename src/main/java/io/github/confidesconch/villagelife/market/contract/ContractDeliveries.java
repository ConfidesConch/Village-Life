package io.github.confidesconch.villagelife.market.contract;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import io.github.confidesconch.villagelife.market.MarketGood;
import io.github.confidesconch.villagelife.market.MarketGoods;
import io.github.confidesconch.villagelife.market.MarketService;
import io.github.confidesconch.villagelife.market.contract.ContractService.Found;
import io.github.confidesconch.villagelife.market.contract.TradeContract.Status;
import io.github.confidesconch.villagelife.market.treasury.Treasury;
import io.github.confidesconch.villagelife.village.NotificationTopic;
import io.github.confidesconch.villagelife.village.VillageService;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Deliveries of trade contracts (M15d2, owner's choice "3a"): when a delivery is due, the giver's share
 * of its surplus leaves its storage and travels to the receiver ({@link Shipment}); on arrival it goes
 * into the receiver's storage, and the payment – proportional to the delivery – travels back the same
 * way. Storage chunks are loaded for this, so contracts also work while nobody is near the villages.
 *
 * <p>M15d3 ("1a 2a 3a"): what is not paid becomes debt and is paid with the next payment or delivery;
 * three failed deliveries or payments in a row cancel the contract; deliveries pause while a village
 * has no market with a merchant; shipments to a vanished village return to the sender.
 */
final class ContractDeliveries {
    private ContractDeliveries() {}

    /** If the receiving storage is full, the rest is tried again after this long. */
    private static final long RETRY_TICKS = 1000;
    /** Contract id of a shipment that turns back because its target village is gone (no payment for it). */
    static final int RETURNED = -1;

    static void tick(MinecraftServer server, ContractStore store, long now) {
        for (TradeContract contract : store.all()) {
            if (contract.isRunning(now) && contract.nextDelivery() > 0 && now >= contract.nextDelivery()) {
                Optional<Found> giver = ContractService.find(server, contract.giver());
                Optional<Found> receiver = ContractService.find(server, contract.receiver());
                if (giver.isPresent() && receiver.isPresent() && isOpen(giver.get()) && isOpen(receiver.get())) {
                    dispatch(server, store, contract, giver.get(), receiver.get(), now);
                } // else paused: stays due until both have a market with a merchant again
            }
        }
        for (Shipment shipment : store.shipments()) {
            if (now >= shipment.arrivesAt()) {
                arrive(server, store, shipment, now);
            }
        }
    }

    /** Whether deliveries of this village's contracts may happen: market with merchant (M15d3, "3a"). */
    static boolean isOpen(Found village) {
        return MarketService.isOpen(village.level(), village.village());
    }

    /** Debug: deliveries of the village are due now and its shipments arrive now. Returns how many. */
    static int hurry(ContractStore store, UUID village, long now) {
        int count = 0;
        for (TradeContract contract : store.of(village)) {
            if (contract.status() != Status.OFFER) {
                store.put(contract.withNextDelivery(now));
                count++;
            }
        }
        for (Shipment shipment : store.shipments()) {
            if (shipment.from().equals(village) || shipment.to().equals(village)) {
                store.replaceShipment(shipment, shipment.arrivingAt(now));
                count++;
            }
        }
        return count;
    }

    /** Debug: shipments of the village (null: all) on the road are raided in 70 s (escorts have left by then) and arrive 50 s later at the earliest. */
    static int raidSoon(ContractStore store, UUID village, long now) {
        int count = 0;
        for (Shipment s : store.shipments()) {
            if (s.contractId() != RETURNED && (village == null || s.from().equals(village) || s.to().equals(village))) {
                store.replaceShipment(s, new Shipment(s.contractId(), s.from(), s.to(), s.item(), s.count(), s.original(),
                        s.payment(), s.departedAt(), Math.max(s.arrivesAt(), now + 2400), s.escorts(), now + 1400, false, s.waiting()));
                count++;
            }
        }
        return count;
    }

    /** Shipments to villages that no longer exist turn back to their sender (or vanish if it is gone too). */
    static void returnOrphans(MinecraftServer server, ContractStore store, Set<UUID> villages, long now) {
        for (Shipment shipment : store.shipments()) {
            if (villages.contains(shipment.to())) {
                continue;
            }
            if (!villages.contains(shipment.from())) {
                store.replaceShipment(shipment, null);
                continue;
            }
            long journey = Math.max(RETRY_TICKS, shipment.arrivesAt() - shipment.departedAt());
            store.replaceShipment(shipment, new Shipment(RETURNED, shipment.to(), shipment.from(), shipment.item(),
                    shipment.count(), shipment.count(), true, now, now + journey));
            Item item = item(shipment.item());
            ContractService.find(server, shipment.from()).ifPresent(sender -> VillageService.tellManagers(server, sender.village(), NotificationTopic.TRADE,
                    Component.translatable("villagelife.contract.returned", shipment.count(),
                            item == null ? Component.literal(shipment.item()) : new ItemStack(item).getHoverName())));
        }
    }

    // ---- sending -------------------------------------------------------------------------------

    private static void dispatch(MinecraftServer server, ContractStore store, TradeContract contract, Found from, Found to,
                                 long now) {
        ContractTerms terms = contract.terms();
        TradeContract current = contract.withNextDelivery(now + terms.days() * ContractRules.DAY_TICKS);
        store.put(current);
        if (current.debt() > 0) {
            current = pay(server, store, current, 0, to, from, now); // "1a": pay old debt as soon as possible
        }
        Item item = item(terms.good());
        if (item == null) {
            return;
        }
        MarketService.loadStorage(from.level(), from.village());
        int stock = MarketService.count(from.level(), from.village(), stack -> stack.is(item));
        int amount = ContractRules.delivery(stock - target(terms.good()), terms.percent(), terms.max());
        Component goodName = new ItemStack(item).getHoverName();
        int taken = amount <= 0 ? 0 : MarketService.take(from.level(), from.village(), stack -> stack.is(item), amount)
                .stream().mapToInt(ItemStack::getCount).sum();
        if (taken <= 0) {
            int misses = current.deliveryMisses() + 1;
            current = current.withDeliveryMisses(misses);
            Component skipped = Component.translatable("villagelife.contract.skipped", from.village().displayName(),
                    goodName, to.village().displayName(), misses, ContractRules.MISSES_TO_CANCEL);
            VillageService.tellManagers(server, from.village(), NotificationTopic.TRADE, skipped);
            VillageService.tellManagers(server, to.village(), NotificationTopic.TRADE, skipped);
            store.put(cancelIfFailing(server, current, misses, from, to, now));
            return;
        }
        store.put(current.withDeliveryMisses(0));
        store.addShipment(ShipmentHooks.dispatched(server, new Shipment(contract.id(), contract.giver(), contract.receiver(),
                terms.good(), taken, taken, false, now, now + travelTicks(from, to)))); // M20c: convoy
        Treasury.log(from.level(), from.village(), Treasury.CONTRACT_OUT, terms.good(), taken, 0);
        VillageService.tellManagers(server, from.village(), NotificationTopic.TRADE, Component.translatable("villagelife.contract.sent",
                taken, goodName, to.village().displayName()));
    }

    // ---- arriving ------------------------------------------------------------------------------

    private static void arrive(MinecraftServer server, ContractStore store, Shipment onTheRoad, long now) {
        Shipment shipment = onTheRoad;
        if (shipment.raidPending()) { // M20c: a raid that is still due happens before the goods arrive
            shipment = ShipmentHooks.arriving(server, onTheRoad);
            store.replaceShipment(onTheRoad, shipment);
        }
        if (shipment.raided() && shipment.original() <= 0) { // M20c: the bandits took everything
            store.replaceShipment(shipment, null);
            return;
        }
        Optional<Found> target = ContractService.find(server, shipment.to());
        Item item = item(shipment.item());
        if (target.isEmpty() || item == null) {
            return; // the village is gone: returnOrphans sends it back
        }
        Found to = target.get();
        MarketService.loadStorage(to.level(), to.village());
        int left = unload(to, item, shipment.count());
        if (left > 0) {
            store.replaceShipment(shipment, shipment.withCount(left).waitingUntil(now + RETRY_TICKS)); // storage full
            return;
        }
        store.replaceShipment(shipment, null);
        Optional<Found> sender = ContractService.find(server, shipment.from());
        Component fromName = sender.map(f -> f.village().displayName()).orElse(Component.literal("?"));
        Component itemName = new ItemStack(item).getHoverName();
        boolean emeralds = item == Items.EMERALD;
        Treasury.log(to.level(), to.village(), shipment.contractId() == RETURNED ? Treasury.RETURNED
                        : shipment.payment() ? Treasury.PAYMENT_IN : Treasury.CONTRACT_IN,
                shipment.item(), shipment.original(), emeralds ? shipment.original() : 0);
        if (shipment.contractId() == RETURNED) {
            VillageService.tellManagers(server, to.village(), NotificationTopic.TRADE,
                    Component.translatable("villagelife.contract.return_arrived", shipment.original(), itemName));
            return;
        }
        if (shipment.payment()) {
            VillageService.tellManagers(server, to.village(), NotificationTopic.TRADE,
                    Component.translatable("villagelife.contract.paid", shipment.original(), itemName, fromName));
            return;
        }
        VillageService.tellManagers(server, to.village(), NotificationTopic.TRADE,
                Component.translatable("villagelife.contract.arrived", shipment.original(), itemName, fromName));
        Optional<TradeContract> contract = store.byId(shipment.contractId());
        if (contract.isPresent() && sender.isPresent()) {
            ContractTerms terms = contract.get().terms();
            pay(server, store, contract.get(), ContractRules.payment(shipment.original(), terms.max(), terms.paymentCount()),
                    to, sender.get(), now);
        }
    }

    /**
     * The receiver pays {@code share} for a delivery plus its old debt; what it cannot pay stays debt.
     * {@code share == 0} is a debt-only attempt, which does not count as a failed payment.
     */
    private static TradeContract pay(MinecraftServer server, ContractStore store, TradeContract contract, int share,
                                     Found payer, Found payee, long now) {
        ContractTerms terms = contract.terms();
        Item payment = item(terms.payment());
        int due = ContractRules.dueWithDebt(share, contract.debt());
        if (payment == null || due <= 0) {
            return contract;
        }
        MarketService.loadStorage(payer.level(), payer.village());
        int paid = MarketService.take(payer.level(), payer.village(), stack -> stack.is(payment), due).stream()
                .mapToInt(ItemStack::getCount).sum();
        if (paid > 0) {
            store.addShipment(ShipmentHooks.dispatched(server, new Shipment(contract.id(), contract.receiver(), contract.giver(),
                    terms.payment(), paid, paid, true, now, now + travelTicks(payer, payee)))); // M20c: convoy
            Treasury.log(payer.level(), payer.village(), Treasury.PAYMENT_OUT, terms.payment(), paid,
                    payment == Items.EMERALD ? -paid : 0);
        }
        int debt = due - paid;
        int misses = share <= 0 ? contract.paymentMisses() : debt > 0 ? contract.paymentMisses() + 1 : 0;
        TradeContract updated = contract.withPayment(debt, misses);
        if (debt > 0 && share > 0) {
            Component shortfall = Component.translatable("villagelife.contract.underpaid", payer.village().displayName(),
                    paid, due, new ItemStack(payment).getHoverName(), debt, misses, ContractRules.MISSES_TO_CANCEL);
            VillageService.tellManagers(server, payer.village(), NotificationTopic.TRADE, shortfall);
            VillageService.tellManagers(server, payee.village(), NotificationTopic.TRADE, shortfall);
        }
        updated = cancelIfFailing(server, updated, misses, payer, payee, now);
        store.put(updated);
        return updated;
    }

    /** Three failures in a row: the contract is cancelled with the usual notice (M15d3, "2a"). */
    private static TradeContract cancelIfFailing(MinecraftServer server, TradeContract contract, int misses, Found a, Found b,
                                                 long now) {
        if (contract.status() != Status.ACTIVE || !ContractRules.cancelsAfter(misses)) {
            return contract;
        }
        Component message = Component.translatable("villagelife.contract.auto_cancelled", a.village().displayName(),
                b.village().displayName(), ContractRules.MISSES_TO_CANCEL);
        VillageService.tellManagers(server, a.village(), NotificationTopic.TRADE, message);
        VillageService.tellManagers(server, b.village(), NotificationTopic.TRADE, message);
        return contract.ending(now + ContractRules.NOTICE_TICKS);
    }

    /** Puts {@code count} items into the storage in stack-sized parts; returns what did not fit. */
    private static int unload(Found to, Item item, int count) {
        int left = count;
        while (left > 0) {
            ItemStack like = new ItemStack(item);
            int part = Math.min(left, Math.min(like.getMaxStackSize(), MarketService.room(to.level(), to.village(), like)));
            if (part <= 0) {
                break;
            }
            MarketService.insert(to.level(), to.village(), new ItemStack(item, part));
            left -= part;
        }
        return left;
    }

    // ---- helpers -------------------------------------------------------------------------------

    private static long travelTicks(Found a, Found b) {
        if (a.level() != b.level()) {
            return ContractRules.DAY_TICKS; // another dimension: the longest journey
        }
        double dx = a.village().center().getX() - b.village().center().getX();
        double dz = a.village().center().getZ() - b.village().center().getZ();
        return ContractRules.travelTicks(Math.sqrt(dx * dx + dz * dz));
    }

    /** The village keeps its market target stock of a good; only what is above it counts as surplus. */
    private static int target(String good) {
        return MarketGoods.all().stream().filter(g -> g.item().toString().equals(good)).mapToInt(MarketGood::target)
                .findFirst().orElse(0);
    }

    static Item item(String id) {
        Identifier parsed = Identifier.tryParse(id);
        if (parsed == null) {
            return null;
        }
        Item item = BuiltInRegistries.ITEM.getValue(parsed);
        return item == Items.AIR ? null : item;
    }
}
