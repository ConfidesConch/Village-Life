package io.github.confidesconch.villagelife.convoy;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import io.github.confidesconch.villagelife.book.VillageBookService;
import io.github.confidesconch.villagelife.convoy.ConvoyStore.RaidRecord;
import io.github.confidesconch.villagelife.defense.RaidService;
import io.github.confidesconch.villagelife.market.contract.ContractService;
import io.github.confidesconch.villagelife.market.contract.ContractService.Found;
import io.github.confidesconch.villagelife.market.contract.ContractStore;
import io.github.confidesconch.villagelife.market.contract.Shipment;
import io.github.confidesconch.villagelife.market.contract.TradeContract;
import io.github.confidesconch.villagelife.village.NotificationTopic;
import io.github.confidesconch.villagelife.village.Village;
import io.github.confidesconch.villagelife.village.VillageService;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Raids on trade shipments (M20c, owner's choice "1b 2b 3a 4a"): when a shipment leaves, the escorts
 * set in the book go with it ({@link EscortTrips}) and it is decided whether bandits will attack it on
 * its way ({@link ConvoyRules}). Is a player near the road at that moment, the attack really happens
 * ({@link ConvoyWagons}); otherwise it is rolled. Bandits take 25–75 % of the goods; the contract goes on.
 */
public final class ConvoyService {
    private ConvoyService() {}

    private static final int UPDATE_TICKS = 20;
    private static final int CLEANUP_TICKS = 1200;
    /** Distance assumed for roads between dimensions. */
    private static final double OTHER_DIMENSION = 1000;

    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        long now = server.overworld().getGameTime();
        if (now % UPDATE_TICKS != 0) {
            return;
        }
        ConvoyStore convoys = ConvoyStore.get(server);
        ContractStore contracts = ContractStore.get(server);
        for (Shipment shipment : contracts.shipments()) {
            if (shipment.raidPending() && now >= shipment.raidAt() && !ConvoyWagons.ambush(server, shipment)) {
                contracts.replaceShipment(shipment, rolled(server, shipment)); // nobody near: rolled
            }
        }
        EscortTrips.tick(server, convoys, now);
        ConvoyWagons.tick(server, contracts.shipments(), now);
        if (now % CLEANUP_TICKS == 0) {
            Set<Integer> ids = new HashSet<>();
            contracts.all().forEach(c -> ids.add(c.id()));
            convoys.keepEscortsOf(ids);
        }
    }

    // ---- leaving -------------------------------------------------------------------------------

    /** A shipment leaves: escorts go with it and maybe a raid lies ahead. */
    public static Shipment dispatched(MinecraftServer server, Shipment shipment) {
        Optional<Found> from = ContractService.find(server, shipment.from());
        Optional<Found> to = ContractService.find(server, shipment.to());
        if (from.isEmpty() || to.isEmpty() || shipment.contractId() < 0) {
            return shipment;
        }
        ConvoyStore store = ConvoyStore.get(server);
        int wanted = store.escorts(shipment.contractId(), shipment.from());
        int escorts = EscortTrips.send(store, from.get(), to.get(), shipment, wanted).size();
        RandomSource random = from.get().level().getRandom();
        long raidAt = 0;
        if (random.nextDouble() < raidChance(from.get(), to.get(), escorts)) {
            long journey = Math.max(1, shipment.arrivesAt() - shipment.departedAt());
            raidAt = shipment.departedAt() + Math.max(1, Math.round(journey * ConvoyRules.raidPoint(random.nextDouble())));
        }
        return shipment.withConvoy(escorts, raidAt);
    }

    /** Chance of a raid on the road between the two villages with this many escorts. */
    static double raidChance(Found a, Found b, int escorts) {
        double power = RaidService.defenderPower(a.village()) + RaidService.defenderPower(b.village());
        return ConvoyRules.raidChance(distance(a, b), power, escorts);
    }

    static double distance(Found a, Found b) {
        if (a.level() != b.level()) {
            return OTHER_DIMENSION;
        }
        double dx = a.village().center().getX() - b.village().center().getX();
        double dz = a.village().center().getZ() - b.village().center().getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    // ---- raids ---------------------------------------------------------------------------------

    /** A raid nobody sees: the escorts fight it off by chance, otherwise goods are lost. Also called just before arrival. */
    public static Shipment rolled(MinecraftServer server, Shipment shipment) {
        if (!shipment.raidPending()) {
            return shipment;
        }
        boolean defended = server.overworld().getRandom().nextDouble() < ConvoyRules.defendChance(shipment.escorts());
        return raided(server, shipment, defended);
    }

    /** The raid is over: goods lost (or not), both villages are told and remember it. */
    static Shipment raided(MinecraftServer server, Shipment shipment, boolean defended) {
        double power = ContractService.find(server, shipment.from()).map(f -> RaidService.defenderPower(f.village())).orElse(0.0);
        int lost = defended ? 0 : ConvoyRules.lost(shipment.count(), ConvoyRules.lossShare(shipment.escorts(), power));
        Shipment after = shipment.afterRaid(lost);
        ConvoyStore store = ConvoyStore.get(server);
        long now = server.overworld().getGameTime();
        store.addRaid(shipment.from(), new RaidRecord(now, shipment.to(), shipment.item(), lost, shipment.count(), defended));
        store.addRaid(shipment.to(), new RaidRecord(now, shipment.from(), shipment.item(), lost, shipment.count(), defended));
        Optional<Found> from = ContractService.find(server, shipment.from());
        Optional<Found> to = ContractService.find(server, shipment.to());
        Component fromName = from.map(f -> f.village().displayName()).orElse(Component.literal("?"));
        Component toName = to.map(f -> f.village().displayName()).orElse(Component.literal("?"));
        Component message = defended
                ? Component.translatable("villagelife.convoy.defended", fromName, toName).withStyle(ChatFormatting.GREEN)
                : Component.translatable("villagelife.convoy.raided", fromName, toName, lost, shipment.count(), itemName(shipment.item()))
                        .withStyle(ChatFormatting.RED);
        from.ifPresent(f -> VillageService.tellManagers(server, f.village(), NotificationTopic.TRADE, message));
        to.ifPresent(f -> VillageService.tellManagers(server, f.village(), NotificationTopic.TRADE, message));
        return after;
    }

    static Component itemName(String id) {
        Identifier parsed = Identifier.tryParse(id);
        var item = parsed == null ? Items.AIR : BuiltInRegistries.ITEM.getValue(parsed);
        return item == Items.AIR ? Component.literal(id) : new ItemStack(item).getHoverName();
    }

    // ---- book ----------------------------------------------------------------------------------

    /** Book section "convoy": road safety and escorts per contract, the last raids. */
    public static void write(ServerPlayer player, Village village, CompoundTag out) {
        MinecraftServer server = player.level().getServer();
        ConvoyStore store = ConvoyStore.get(server);
        Optional<Found> self = ContractService.find(server, village.id());
        CompoundTag perContract = new CompoundTag();
        for (TradeContract contract : ContractStore.get(server).of(village.id())) {
            int escorts = store.escorts(contract.id(), village.id());
            Optional<Found> partner = ContractService.find(server, contract.partnerOf(village.id()));
            double chance = self.isPresent() && partner.isPresent() ? raidChance(self.get(), partner.get(), escorts) : 0.5;
            CompoundTag c = new CompoundTag();
            c.putInt("escorts", escorts);
            c.putInt("safety", ConvoyRules.safetyPercent(chance));
            c.putString("level", ConvoyRules.safetyLevel(ConvoyRules.safetyPercent(chance)));
            perContract.put(String.valueOf(contract.id()), c);
        }
        out.put("contracts", perContract);
        out.putInt("max_escorts", ConvoyRules.MAX_ESCORTS);
        out.putInt("away", (int) store.trips().stream().filter(t -> t.village().equals(village.id()))
                .mapToLong(t -> t.members().size()).sum());
        long now = server.overworld().getGameTime();
        ListTag history = new ListTag();
        for (RaidRecord raid : store.history(village.id())) {
            CompoundTag h = new CompoundTag();
            Optional<Found> partner = ContractService.find(server, raid.partner());
            h.putString("name", partner.flatMap(f -> f.village().customName()).orElse(""));
            h.putString("owner_name", partner.map(f -> f.village().ownerName()).orElse("?"));
            h.putString("item", raid.item());
            h.putInt("lost", raid.lost());
            h.putInt("total", raid.total());
            h.putBoolean("defended", raid.defended());
            h.putLong("ago", Math.max(0, now - raid.at()));
            history.add(h);
        }
        out.put("history", history);
    }

    /** From the book: how many escorts the village sends with a contract (owner and managers). */
    public static void onEscort(ConvoyEscortPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        MinecraftServer server = player.level().getServer();
        UUID villageId;
        try {
            villageId = UUID.fromString(payload.villageId());
        } catch (IllegalArgumentException invalid) {
            return;
        }
        Optional<Found> own = ContractService.find(server, villageId);
        if (own.isEmpty()) {
            return;
        }
        Village village = own.get().village();
        boolean known = ContractStore.get(server).byId(payload.contractId()).map(c -> c.involves(villageId)).orElse(false);
        if (village.mayManage(player.getUUID()) && known) {
            ConvoyStore.get(server).setEscorts(payload.contractId(), villageId, payload.escorts());
        }
        VillageBookService.open(player, village, null, "contracts");
    }
}
