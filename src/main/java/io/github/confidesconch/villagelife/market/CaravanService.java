package io.github.confidesconch.villagelife.market;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import io.github.confidesconch.villagelife.building.Building;
import io.github.confidesconch.villagelife.market.CaravanPlanner.Deal;
import io.github.confidesconch.villagelife.market.CaravanPlanner.Good;
import io.github.confidesconch.villagelife.market.CaravanStore.State;
import io.github.confidesconch.villagelife.market.CaravanStore.Visit;
import io.github.confidesconch.villagelife.market.treasury.Treasury;
import io.github.confidesconch.villagelife.village.Village;
import io.github.confidesconch.villagelife.village.NotificationTopic;
import io.github.confidesconch.villagelife.village.VillageService;
import io.github.confidesconch.villagelife.village.VillageManager;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.animal.equine.TraderLlama;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Trading caravans (M15b, owner's choice "1a 2a 3a"): every few days (village setting) a wandering
 * trader with two llamas walks to the market in the morning, trades with the village storage on its own
 * ({@link CaravanPlanner}) and leaves after half a day. The owner and managers who are online get a
 * message; the market page shows the next and the last visit.
 */
public final class CaravanService {
    private CaravanService() {}

    static final int UPDATE_INTERVAL_TICKS = 200;
    private static final double ARRIVED_DISTANCE = 10.0;
    private static final int SPAWN_MIN_DISTANCE = 20;
    private static final int SPAWN_MAX_DISTANCE = 28;
    /** If no place to appear is found, try again in a minute. */
    private static final int RETRY_TICKS = 1200;

    public static void onServerTick(ServerTickEvent.Post event) {
        for (ServerLevel level : event.getServer().getAllLevels()) {
            VillageManager manager = VillageManager.get(level);
            if (manager.all().isEmpty()) {
                continue;
            }
            long now = level.getGameTime();
            CaravanStore store = CaravanStore.get(level);
            Set<UUID> ids = new HashSet<>();
            for (Village village : List.copyOf(manager.all())) {
                ids.add(village.id());
                if (Math.floorMod(now + village.id().hashCode(), UPDATE_INTERVAL_TICKS) == 0) {
                    update(level, village, store, now);
                    if ((now / UPDATE_INTERVAL_TICKS) % 6 == 0) {
                        Treasury.snapshot(level, village); // M15c: emeralds per day, about once a minute
                    }
                }
            }
            if (now % CaravanPlanner.TICKS_PER_DAY == 0) {
                store.retain(ids);
            }
        }
    }

    private static void update(ServerLevel level, Village village, CaravanStore store, long now) {
        int days = village.settings().caravanDays();
        Optional<State> known = store.get(village.id());
        if (known.isEmpty()) {
            store.put(village.id(), new State(CaravanPlanner.nextVisit(now, days), Optional.empty(), 0, false, Optional.empty()));
            return;
        }
        State state = known.get();
        Optional<Building> market = MarketService.market(level, village);
        if (state.trader().isPresent()) {
            tend(level, village, store, state, market, now);
            return;
        }
        if (state.nextVisit() > CaravanPlanner.nextVisit(now, days)) { // the owner shortened the interval
            store.put(village.id(), state.postponed(CaravanPlanner.nextVisit(now, days)));
            return;
        }
        if (market.isEmpty() || !level.isLoaded(market.get().markerPos())
                || !CaravanPlanner.mayArrive(now, level.getOverworldClockTime(), state.nextVisit())) {
            return;
        }
        Optional<UUID> trader = spawn(level, village, market.get().markerPos());
        store.put(village.id(), trader.map(id -> state.arrived(id, now, CaravanPlanner.nextVisit(now, days)))
                .orElse(state.postponed(now + RETRY_TICKS)));
    }

    /** A caravan is there: trade once it reaches the market; forget it once it has gone. */
    private static void tend(ServerLevel level, Village village, CaravanStore store, State state, Optional<Building> market, long now) {
        Entity trader = level.getEntity(state.trader().get());
        if (trader == null || !trader.isAlive()) {
            // null can also mean "in an unloaded chunk" – only give up after its stay is surely over
            if (trader != null || now - state.arrivedAt() > CaravanPlanner.STAY_TICKS + RETRY_TICKS) {
                store.put(village.id(), state.left());
            }
            return;
        }
        if (state.traded() || market.isEmpty()) {
            return;
        }
        boolean atMarket = market.get().markerPos().closerToCenterThan(trader.position(), ARRIVED_DISTANCE);
        if (atMarket || now - state.arrivedAt() > CaravanPlanner.TRADE_AT_LATEST_TICKS) {
            Visit visit = trade(level, village, now);
            store.put(village.id(), state.traded(visit));
            tell(level, village, visit);
        }
    }

    /**
     * Debug ({@code /villagelife debug caravan}): a caravan sets out right now, at any time of day.
     * False if there is no market, a caravan is already there or no free spot was found.
     */
    public static boolean callNow(ServerLevel level, Village village) {
        CaravanStore store = CaravanStore.get(level);
        State state = store.get(village.id()).orElse(new State(0, Optional.empty(), 0, false, Optional.empty()));
        Optional<Building> market = MarketService.market(level, village);
        Entity present = state.trader().map(level::getEntity).orElse(null);
        if (market.isEmpty() || (present != null && present.isAlive())) {
            return false;
        }
        long now = level.getGameTime();
        Optional<UUID> trader = spawn(level, village, market.get().markerPos());
        trader.ifPresent(id -> store.put(village.id(),
                state.arrived(id, now, CaravanPlanner.nextVisit(now, village.settings().caravanDays()))));
        return trader.isPresent();
    }

    // ---- appearing -----------------------------------------------------------------------------

    private static Optional<UUID> spawn(ServerLevel level, Village village, BlockPos market) {
        BlockPos at = findPlace(level, market);
        if (at == null) {
            return Optional.empty();
        }
        WanderingTrader trader = EntityTypes.WANDERING_TRADER.spawn(level, at, EntitySpawnReason.EVENT);
        if (trader == null) {
            return Optional.empty();
        }
        trader.setCustomName(Component.translatable("villagelife.caravan.name"));
        trader.setDespawnDelay(CaravanPlanner.STAY_TICKS);
        trader.setWanderTarget(market);
        trader.setHomeTo(market, 16);
        for (int i = 0; i < 2; i++) {
            TraderLlama llama = EntityTypes.TRADER_LLAMA.spawn(level, trader.blockPosition(), EntitySpawnReason.EVENT);
            if (llama != null) {
                llama.setLeashedTo(trader, true);
            }
        }
        return Optional.of(trader.getUUID());
    }

    /** A free surface spot 20–28 blocks from the market, in a loaded chunk. */
    private static BlockPos findPlace(ServerLevel level, BlockPos market) {
        for (int i = 0; i < 16; i++) {
            double angle = level.getRandom().nextDouble() * Math.PI * 2;
            int distance = SPAWN_MIN_DISTANCE + level.getRandom().nextInt(SPAWN_MAX_DISTANCE - SPAWN_MIN_DISTANCE + 1);
            int x = market.getX() + (int) Math.round(Math.cos(angle) * distance);
            int z = market.getZ() + (int) Math.round(Math.sin(angle) * distance);
            if (!level.isLoaded(new BlockPos(x, market.getY(), z))) {
                continue;
            }
            int y = level.getHeight(SpawnPlacements.getHeightmapType(EntityTypes.WANDERING_TRADER), x, z);
            BlockPos pos = new BlockPos(x, y, z);
            if (SpawnPlacements.getPlacementType(EntityTypes.WANDERING_TRADER).isSpawnPositionOk(level, pos, EntityTypes.WANDERING_TRADER)) {
                return pos;
            }
        }
        return null;
    }

    // ---- trading -------------------------------------------------------------------------------

    /** Trades with the village storage at market prices; every deal is checked again against the storage. */
    static Visit trade(ServerLevel level, Village village, long now) {
        List<MarketGood> goods = MarketGoods.all();
        List<Item> items = new ArrayList<>();
        List<Good> view = new ArrayList<>();
        for (MarketGood good : goods) {
            Item item = BuiltInRegistries.ITEM.getValue(good.item());
            int stock = MarketService.count(level, village, stack -> stack.is(item));
            items.add(item);
            view.add(new Good(MarketPricing.bundlesForSale(stock, good.target(), good.count()),
                    MarketPricing.bundlesWanted(stock, good.target(), good.count()),
                    MarketPricing.price(good.emeralds(), stock, good.target())));
        }
        int emeralds = MarketService.count(level, village, stack -> stack.is(Items.EMERALD));
        int sold = 0;
        int bought = 0;
        int earned = 0;
        int spent = 0;
        for (Deal deal : CaravanPlanner.plan(view, emeralds)) {
            MarketGood good = goods.get(deal.index());
            Item item = items.get(deal.index());
            int price = view.get(deal.index()).price();
            if (deal.villageSells()) {
                if (MarketService.count(level, village, stack -> stack.is(item)) >= good.count()
                        && MarketService.room(level, village, new ItemStack(Items.EMERALD)) >= price) {
                    MarketService.take(level, village, stack -> stack.is(item), good.count()); // the caravan takes it along
                    MarketService.insert(level, village, new ItemStack(Items.EMERALD, price));
                    sold++;
                    earned += price;
                    Treasury.log(level, village, Treasury.CARAVAN_SELL, good.item().toString(), good.count(), price);
                }
            } else if (MarketService.count(level, village, stack -> stack.is(Items.EMERALD)) >= price
                    && MarketService.room(level, village, new ItemStack(item)) >= good.count()) {
                MarketService.take(level, village, stack -> stack.is(Items.EMERALD), price);
                MarketService.insert(level, village, new ItemStack(item, good.count()));
                bought++;
                spent += price;
                Treasury.log(level, village, Treasury.CARAVAN_BUY, good.item().toString(), good.count(), -price);
            }
        }
        return new Visit(now, sold, bought, earned, spent);
    }

    /** Tells the owner and the managers who are online what the caravan did. */
    private static void tell(ServerLevel level, Village village, Visit visit) {
        Component message = visit.sold() + visit.bought() == 0
                ? Component.translatable("villagelife.caravan.no_deals", village.displayName())
                : Component.translatable("villagelife.caravan.traded", village.displayName(),
                        visit.sold(), visit.earned(), visit.bought(), visit.spent());
        VillageService.tellManagers(level.getServer(), village, NotificationTopic.CARAVAN, message);
    }
}
