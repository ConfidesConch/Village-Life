package io.github.confidesconch.villagelife.market;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.github.confidesconch.villagelife.village.NotificationTopic;
import io.github.confidesconch.villagelife.village.Village;
import io.github.confidesconch.villagelife.village.VillageManager;
import io.github.confidesconch.villagelife.village.VillageService;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * The daily market trader (M28c, owner's choice "a stranger every day, at the village center, rare
 * goods"): every morning a trader appears next to the village center, sells players a few rare things
 * for emeralds ({@link StallOffers}, picked by {@link StallPlanner}) and leaves at dusk. He is a vanilla
 * wandering trader with his own offers (vanilla trading window); no blocks are placed, so nothing the
 * players built is touched. The caravan (M15b) is not affected.
 */
public final class StallService {
    private StallService() {}

    private static final int UPDATE_INTERVAL_TICKS = 200;
    /** Marks the trader with the village id (NeoForge persistent data), so he is found again after a restart. */
    private static final String TAG = "villagelife_stall";
    private static final int SEARCH_RADIUS = 64;
    private static final int SPAWN_MIN_DISTANCE = 3;
    private static final int SPAWN_MAX_DISTANCE = 6;
    private static final int STAY_NEAR = 5;
    /** Each offer can be bought this often per day. */
    private static final int MAX_USES = 4;

    /** Day of the last visit per village (in memory: after a restart he may come once more that morning). */
    private static final Map<UUID, Long> lastDay = new HashMap<>();

    public static void onServerTick(ServerTickEvent.Post event) {
        for (ServerLevel level : event.getServer().getAllLevels()) {
            VillageManager manager = VillageManager.get(level);
            long now = level.getGameTime();
            for (Village village : List.copyOf(manager.all())) {
                if (Math.floorMod(now + village.id().hashCode() + 97, UPDATE_INTERVAL_TICKS) == 0) {
                    update(level, village);
                }
            }
        }
    }

    private static void update(ServerLevel level, Village village) {
        if (StallOffers.all().isEmpty() || !level.isLoaded(village.center())) {
            return;
        }
        long dayTime = level.getOverworldClockTime();
        long today = StallPlanner.day(dayTime);
        if (present(level, village) != null) {
            lastDay.put(village.id(), today);
            return;
        }
        if (StallPlanner.mayArrive(dayTime, lastDay.getOrDefault(village.id(), Long.MIN_VALUE)) && spawn(level, village, dayTime)) {
            lastDay.put(village.id(), today);
            VillageService.tellManagers(level.getServer(), village, NotificationTopic.CARAVAN,
                    Component.translatable("villagelife.stall.arrived", village.displayName()));
        }
    }

    /**
     * Debug ({@code /villagelife debug stall}): the trader comes right now (a new one if he is already
     * there), at any time of day; he still leaves at dusk (at once after dusk: half a day later).
     */
    public static boolean callNow(ServerLevel level, Village village) {
        WanderingTrader old = present(level, village);
        if (old != null) {
            old.discard();
        }
        return !StallOffers.all().isEmpty() && spawn(level, village, level.getOverworldClockTime());
    }

    private static WanderingTrader present(ServerLevel level, Village village) {
        String id = village.id().toString();
        List<WanderingTrader> found = level.getEntitiesOfClass(WanderingTrader.class, new AABB(village.center()).inflate(SEARCH_RADIUS),
                trader -> trader.isAlive() && id.equals(trader.getPersistentData().getStringOr(TAG, "")));
        return found.isEmpty() ? null : found.getFirst();
    }

    private static boolean spawn(ServerLevel level, Village village, long dayTime) {
        BlockPos at = findPlace(level, village.center());
        if (at == null) {
            return false;
        }
        WanderingTrader trader = EntityTypes.WANDERING_TRADER.spawn(level, at, EntitySpawnReason.EVENT);
        if (trader == null) {
            return false;
        }
        trader.getPersistentData().putString(TAG, village.id().toString());
        trader.setCustomName(Component.translatable("villagelife.stall.name"));
        int stay = StallPlanner.stayTicks(dayTime);
        trader.setDespawnDelay(stay > 1 ? stay : StallPlanner.TICKS_PER_DAY / 2);
        trader.setWanderTarget(village.center());
        trader.setHomeTo(village.center(), STAY_NEAR);
        MerchantOffers offers = trader.getOffers(); // fills the vanilla offers first ...
        offers.clear();                             // ... which the market trader does not have
        offers.addAll(offersFor(village, StallPlanner.day(dayTime)));
        return true;
    }

    /** Today's offers of this village (the same all day). */
    private static List<MerchantOffer> offersFor(Village village, long day) {
        List<StallOffer> all = StallOffers.all();
        List<Integer> weights = all.stream().map(StallOffer::weight).toList();
        List<MerchantOffer> offers = new ArrayList<>();
        for (int index : StallPlanner.pick(weights, StallPlanner.OFFERS_PER_DAY, StallPlanner.seed(village.id().hashCode(), day))) {
            StallOffer offer = all.get(index);
            if (!BuiltInRegistries.ITEM.containsKey(offer.item())) {
                continue; // an item of a mod that is not installed
            }
            ItemStack result = new ItemStack(BuiltInRegistries.ITEM.getValue(offer.item()), offer.count());
            offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, offer.emeralds()), result, MAX_USES, 1, 0.05F));
        }
        return offers;
    }

    /** A free surface spot a few blocks from the village center, in a loaded chunk. */
    private static BlockPos findPlace(ServerLevel level, BlockPos center) {
        for (int i = 0; i < 16; i++) {
            double angle = level.getRandom().nextDouble() * Math.PI * 2;
            int distance = SPAWN_MIN_DISTANCE + level.getRandom().nextInt(SPAWN_MAX_DISTANCE - SPAWN_MIN_DISTANCE + 1);
            int x = center.getX() + (int) Math.round(Math.cos(angle) * distance);
            int z = center.getZ() + (int) Math.round(Math.sin(angle) * distance);
            if (!level.isLoaded(new BlockPos(x, center.getY(), z))) {
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
}
