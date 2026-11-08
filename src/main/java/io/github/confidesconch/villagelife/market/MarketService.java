package io.github.confidesconch.villagelife.market;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

import io.github.confidesconch.villagelife.book.VillageBookService;
import io.github.confidesconch.villagelife.building.Building;
import io.github.confidesconch.villagelife.building.BuildingType;
import io.github.confidesconch.villagelife.inventory.StorageAccess;
import io.github.confidesconch.villagelife.market.treasury.Treasury;
import io.github.confidesconch.villagelife.profession.JobAllocator.Workplace;
import io.github.confidesconch.villagelife.profession.JobService;
import io.github.confidesconch.villagelife.resident.ResidentEntity;
import io.github.confidesconch.villagelife.resident.ResidentService;
import io.github.confidesconch.villagelife.village.Village;
import io.github.confidesconch.villagelife.village.VillageManager;
import io.github.confidesconch.villagelife.village.VillageService;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * The village market (M15a, owner's choice "1a 2b 3a 4a"): players trade on the market page of the
 * village book while standing at the market (a workplace with a cartography table) and while its
 * merchant is there. Payment in emeralds; goods and emeralds of the village lie in its storage
 * buildings. Prices follow supply and demand ({@link MarketPricing}). Everything is checked again on
 * the server for every trade.
 */
public final class MarketService {
    private MarketService() {}

    public static final String MERCHANT = "villagelife:merchant";
    /** The player must be this close to the market's marker. */
    private static final double TRADE_DISTANCE = 10.0;
    /** The merchant must be this close to the market's marker (at work, not at home). */
    private static final double MERCHANT_DISTANCE = 8.0;

    // ---- book section --------------------------------------------------------------------------

    /** Section "market": whether the player can trade here and now, the offers and the wishes. */
    public static void write(ServerPlayer player, Village village, CompoundTag out) {
        ServerLevel level = player.level();
        Optional<Building> market = market(level, village);
        market.ifPresent(b -> out.putString("market_pos", b.markerPos().toShortString()));
        out.putString("status", status(player, village, market));
        out.putInt("village_emeralds", count(level, village, stack -> stack.is(Items.EMERALD)));
        out.putInt("player_emeralds", ContainerHelper.clearOrCountMatchingItems(player.getInventory(),
                stack -> stack.is(Items.EMERALD), Integer.MAX_VALUE, true));
        ListTag offers = new ListTag();
        ListTag wants = new ListTag();
        ListTag prices = new ListTag();
        for (MarketGood good : MarketGoods.all()) {
            Item item = BuiltInRegistries.ITEM.getValue(good.item());
            int stock = count(level, village, stack -> stack.is(item));
            int price = MarketPricing.price(good.emeralds(), stock, good.target());
            int forSale = MarketPricing.bundlesForSale(stock, good.target(), good.count());
            int wanted = MarketPricing.bundlesWanted(stock, good.target(), good.count());
            if (price != good.emeralds()) {
                CompoundTag p = new CompoundTag();
                p.putString("item", good.item().toString());
                p.putInt("price", price);
                p.putInt("base", good.emeralds());
                prices.add(p);
            }
            if (forSale > 0) {
                offers.add(entry(good, price, forSale, 0));
            }
            if (wanted > 0) {
                int has = ContainerHelper.clearOrCountMatchingItems(player.getInventory(), sellable(item), Integer.MAX_VALUE, true);
                wants.add(entry(good, price, wanted, has));
            }
        }
        out.put("offers", offers);
        out.put("wants", wants);
        writeCaravan(level, village, out);
        if (village.mayManage(player.getUUID())) { // M15c: the treasury is for owner and managers only
            CompoundTag treasury = Treasury.section(level, village);
            treasury.put("prices", prices);
            out.put("treasury", treasury);
        }
    }

    /** When the next caravan comes (M15b), whether one is here, and what the last one traded. */
    private static void writeCaravan(ServerLevel level, Village village, CompoundTag out) {
        CaravanStore.get(level).get(village.id()).ifPresent(state -> {
            out.putBoolean("caravan_here", state.trader().isPresent());
            out.putLong("caravan_in", Math.max(0, state.nextVisit() - level.getGameTime()));
            state.last().ifPresent(visit -> {
                CompoundTag last = new CompoundTag();
                last.putInt("sold", visit.sold());
                last.putInt("earned", visit.earned());
                last.putInt("bought", visit.bought());
                last.putInt("spent", visit.spent());
                out.put("caravan_last", last);
            });
        });
    }

    private static CompoundTag entry(MarketGood good, int price, int bundles, int playerHas) {
        CompoundTag e = new CompoundTag();
        e.putString("item", good.item().toString());
        e.putInt("count", good.count());
        e.putInt("price", price);
        e.putInt("bundles", bundles);
        e.putInt("player_has", playerHas);
        return e;
    }

    /** "ok", "no_market" (none built), "no_merchant" (none at work there) or "too_far". */
    private static String status(ServerPlayer player, Village village, Optional<Building> market) {
        if (market.isEmpty()) {
            return "no_market";
        }
        BlockPos pos = market.get().markerPos();
        boolean merchantThere = ResidentService.loadedResidents(player.level(), village).stream()
                .anyMatch(r -> isMerchantOf(r, market.get()) && pos.closerToCenterThan(r.position(), MERCHANT_DISTANCE));
        if (!merchantThere) {
            return "no_merchant";
        }
        return pos.closerToCenterThan(player.position(), TRADE_DISTANCE) ? "ok" : "too_far";
    }

    private static boolean isMerchantOf(ResidentEntity resident, Building market) {
        return resident.brain().job() != null && resident.brain().job().workplace().equals(market.id())
                && resident.brain().job().profession().equals(MERCHANT);
    }

    /** The village's market: the first workplace of the merchant profession. */
    public static Optional<Building> market(ServerLevel level, Village village) {
        for (Workplace workplace : JobService.workplaces(level, village)) {
            if (workplace.profession().equals(MERCHANT)) {
                return village.buildings().stream().filter(b -> b.id().equals(workplace.buildingId())).findFirst();
            }
        }
        return Optional.empty();
    }

    /** A market with a merchant who has the job there (whether at work right now or not) – M15d3. */
    public static boolean isOpen(ServerLevel level, Village village) {
        return market(level, village).map(m -> village.residents().stream().anyMatch(r -> r.job()
                .map(job -> job.workplace().equals(m.id()) && job.profession().equals(MERCHANT)).orElse(false)))
                .orElse(false);
    }

    // ---- trading -------------------------------------------------------------------------------

    /** A click on "buy" or "sell" on the market page. */
    public static void onTrade(MarketTradePayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        UUID id;
        try {
            id = UUID.fromString(payload.villageId());
        } catch (IllegalArgumentException invalid) {
            return;
        }
        VillageManager.get(player.level()).byId(id).ifPresent(village -> {
            ServerLevel level = player.level();
            if (status(player, village, market(level, village)).equals("ok")) {
                MarketGoods.all().stream().filter(good -> good.item().toString().equals(payload.item())).findFirst()
                        .ifPresent(good -> {
                            if (payload.villageSells()) {
                                sellToPlayer(level, player, village, good);
                            } else {
                                buyFromPlayer(level, player, village, good);
                            }
                        });
            }
            VillageBookService.open(player, village, null, "market");
        });
    }

    /** The player buys one bundle: emeralds into storage, goods to the player. */
    private static void sellToPlayer(ServerLevel level, ServerPlayer player, Village village, MarketGood good) {
        Item item = BuiltInRegistries.ITEM.getValue(good.item());
        int stock = count(level, village, stack -> stack.is(item));
        int price = MarketPricing.price(good.emeralds(), stock, good.target());
        if (MarketPricing.bundlesForSale(stock, good.target(), good.count()) <= 0
                || countPlayer(player, stack -> stack.is(Items.EMERALD)) < price
                || room(level, village, new ItemStack(Items.EMERALD)) < price) {
            return;
        }
        ContainerHelper.clearOrCountMatchingItems(player.getInventory(), stack -> stack.is(Items.EMERALD), price, false);
        insert(level, village, new ItemStack(Items.EMERALD, price));
        take(level, village, stack -> stack.is(item), good.count()).forEach(stack -> give(level, player, stack));
        Treasury.log(level, village, Treasury.PLAYER_BUY, good.item().toString(), good.count(), price);
    }

    /** The player sells one bundle: goods into storage, emeralds to the player. */
    private static void buyFromPlayer(ServerLevel level, ServerPlayer player, Village village, MarketGood good) {
        Item item = BuiltInRegistries.ITEM.getValue(good.item());
        int stock = count(level, village, stack -> stack.is(item));
        int price = MarketPricing.price(good.emeralds(), stock, good.target());
        if (MarketPricing.bundlesWanted(stock, good.target(), good.count()) <= 0
                || count(level, village, stack -> stack.is(Items.EMERALD)) < price
                || countPlayer(player, sellable(item)) < good.count()
                || room(level, village, new ItemStack(item)) < good.count()) {
            return;
        }
        ContainerHelper.clearOrCountMatchingItems(player.getInventory(), sellable(item), good.count(), false);
        insert(level, village, new ItemStack(item, good.count()));
        take(level, village, stack -> stack.is(Items.EMERALD), price).forEach(stack -> give(level, player, stack));
        Treasury.log(level, village, Treasury.PLAYER_SELL, good.item().toString(), good.count(), -price);
    }

    /** Undamaged items of that kind (a worn tool is not bought at full price). */
    private static Predicate<ItemStack> sellable(Item item) {
        return stack -> stack.is(item) && !stack.isDamaged();
    }

    private static int countPlayer(ServerPlayer player, Predicate<ItemStack> wanted) {
        return ContainerHelper.clearOrCountMatchingItems(player.getInventory(), wanted, Integer.MAX_VALUE, true);
    }

    private static void give(ServerLevel level, ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack) && !stack.isEmpty()) {
            player.spawnAtLocation(level, stack);
        }
    }

    // ---- the village's storage -----------------------------------------------------------------

    /**
     * Loads the chunks of the village's storage containers (M15d2): contract deliveries also happen while
     * nobody is near the village. Only called a few times per Minecraft day.
     */
    public static void loadStorage(ServerLevel level, Village village) {
        for (Building building : village.buildings()) {
            if (building.type() == BuildingType.STORAGE && building.isValid()) {
                for (BlockPos pos : building.containers()) {
                    level.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
                }
            }
        }
    }

    private static List<Container> storage(ServerLevel level, Village village) {
        List<Container> containers = new ArrayList<>();
        for (Building building : village.buildings()) {
            if (building.type() == BuildingType.STORAGE && building.isValid()) {
                for (BlockPos pos : building.containers()) {
                    Container container = StorageAccess.containerAt(level, pos);
                    if (container != null) {
                        containers.add(container);
                    }
                }
            }
        }
        return containers;
    }

    /** Whether all storage containers of the village are loaded right now (M15c). */
    public static boolean isStorageLoaded(ServerLevel level, Village village) {
        for (Building building : village.buildings()) {
            if (building.type() == BuildingType.STORAGE && building.isValid()) {
                for (BlockPos pos : building.containers()) {
                    if (!level.isLoaded(pos)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    public static int count(ServerLevel level, Village village, Predicate<ItemStack> wanted) {
        int count = 0;
        for (Container container : storage(level, village)) {
            for (int slot = 0; slot < container.getContainerSize(); slot++) {
                ItemStack stack = container.getItem(slot);
                count += wanted.test(stack) ? stack.getCount() : 0;
            }
        }
        return count;
    }

    /** How many items like {@code like} still fit into the storage. */
    public static int room(ServerLevel level, Village village, ItemStack like) {
        int room = 0;
        for (Container container : storage(level, village)) {
            for (int slot = 0; slot < container.getContainerSize(); slot++) {
                ItemStack stack = container.getItem(slot);
                if (stack.isEmpty()) {
                    room += like.getMaxStackSize();
                } else if (ItemStack.isSameItemSameComponents(stack, like)) {
                    room += stack.getMaxStackSize() - stack.getCount();
                }
            }
        }
        return room;
    }

    public static void insert(ServerLevel level, Village village, ItemStack stack) {
        ItemStack rest = stack;
        for (Container container : storage(level, village)) {
            rest = StorageAccess.insert(container, rest);
            if (rest.isEmpty()) {
                return;
            }
        }
    }

    public static List<ItemStack> take(ServerLevel level, Village village, Predicate<ItemStack> wanted, int amount) {
        List<ItemStack> taken = new ArrayList<>();
        int left = amount;
        for (Container container : storage(level, village)) {
            for (ItemStack stack : StorageAccess.take(container, wanted, left)) {
                left -= stack.getCount();
                taken.add(stack);
            }
            if (left <= 0) {
                break;
            }
        }
        return taken;
    }
}
