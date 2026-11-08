package io.github.confidesconch.villagelife.growth;

import java.util.List;
import java.util.Objects;

import io.github.confidesconch.villagelife.building.BuildingType;
import io.github.confidesconch.villagelife.core.config.SyncedConfig;
import io.github.confidesconch.villagelife.market.MarketService;
import io.github.confidesconch.villagelife.profession.Profession;
import io.github.confidesconch.villagelife.profession.Professions;
import io.github.confidesconch.villagelife.resident.ResidentRecord;
import io.github.confidesconch.villagelife.resident.ResidentService;
import io.github.confidesconch.villagelife.village.Village;
import io.github.confidesconch.villagelife.village.NotificationTopic;
import io.github.confidesconch.villagelife.village.VillageService;
import io.github.confidesconch.villagelife.village.VillageArea;
import io.github.confidesconch.villagelife.village.VillageManager;
import io.github.confidesconch.villagelife.village.VillageStages;
import io.github.confidesconch.villagelife.village.VillageStages.Requirements;
import io.github.confidesconch.villagelife.village.VillageStages.Status;

import it.unimi.dsi.fastutil.ints.IntList;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.component.Fireworks;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Village growth (M16, owner's choice "1a 2a 3a 4a"): every 10 s each village is checked against the
 * next stage's requirements ({@link VillageStages}); when it meets them it rises automatically – the
 * radius grows (if no other village is in the way), fireworks go up over the center, the owner and
 * managers get a message and a supply parcel arrives in the storage ({@link StageRewards}).
 */
public final class GrowthService {
    private GrowthService() {}

    static final int UPDATE_INTERVAL_TICKS = 200;
    private static final int OFFSET = 50;
    private static final int[] FIREWORK_COLORS = {0xFFD700, 0x43A047, 0x1E88E5, 0xE53935, 0xFFFFFF};

    public static void onServerTick(ServerTickEvent.Post event) {
        for (ServerLevel level : event.getServer().getAllLevels()) {
            long now = level.getGameTime();
            VillageManager manager = VillageManager.get(level);
            for (Village village : List.copyOf(manager.all())) {
                if (Math.floorMod(now + OFFSET + village.id().hashCode(), UPDATE_INTERVAL_TICKS) == 0) {
                    check(level, manager, village);
                }
            }
        }
    }

    /** The village's current values for the stage requirements. */
    public static Status status(ServerLevel level, Village village) {
        int professions = (int) village.residents().stream().map(ResidentRecord::job)
                .flatMap(java.util.Optional::stream).map(job -> job.profession()).filter(Objects::nonNull).distinct().count();
        boolean storage = village.buildings().stream().anyMatch(b -> b.type() == BuildingType.STORAGE && b.isValid());
        int guards = (int) village.residents().stream().map(ResidentRecord::job).flatMap(java.util.Optional::stream)
                .filter(job -> Professions.get(job.profession()).map(Profession::defender).orElse(false)).count(); // M20a
        return new Status(village.residents().size(), ResidentService.averageSatisfaction(level, village).orElse(-1),
                professions, storage, MarketService.market(level, village).isPresent(), guards);
    }

    /** Residents this village may have: the stage limit, at most the server's limit. */
    public static int maxResidents(Village village) {
        return Math.min(SyncedConfig.MAX_RESIDENTS_PER_VILLAGE.getAsInt(), VillageStages.maxResidents(village.stage()));
    }

    private static void check(ServerLevel level, VillageManager manager, Village village) {
        int next = VillageStages.next(village.stage(), status(level, village));
        if (next > village.stage()) {
            rise(level, manager, village, next);
        }
    }

    /** Debug ({@code /villagelife debug stage}): the village rises one stage now, requirements or not. */
    public static boolean forceRise(ServerLevel level, Village village) {
        if (village.stage() >= VillageStages.MAX_STAGE) {
            return false;
        }
        rise(level, VillageManager.get(level), village, village.stage() + 1);
        return true;
    }

    private static void rise(ServerLevel level, VillageManager manager, Village village, int next) {
        int radius = village.radius() + VillageStages.RADIUS_GROWTH;
        boolean grew = !overlapsOther(manager, village, radius);
        Village risen = village.withStage(next, grew ? radius : village.radius());
        manager.update(risen);
        io.github.confidesconch.villagelife.api.ApiEvents.stageChanged(level, risen.id(), village.stage(), next); // M22a
        celebrate(level, risen);
        boolean allStored = deliverParcel(level, risen, next);
        Component name = Component.translatable("villagelife.stage." + next);
        VillageService.tellManagers(level.getServer(), risen, NotificationTopic.GROWTH, Component.translatable("villagelife.growth.risen",
                risen.displayName(), name).withStyle(ChatFormatting.GOLD));
        VillageService.tellManagers(level.getServer(), risen, NotificationTopic.GROWTH, Component.translatable(grew ? "villagelife.growth.bonus"
                : "villagelife.growth.bonus_no_radius", VillageStages.maxResidents(next), risen.radius(),
                VillageStages.maxContracts(next)));
        VillageService.tellManagers(level.getServer(), risen, NotificationTopic.GROWTH, Component.translatable(allStored
                ? "villagelife.growth.parcel" : "villagelife.growth.parcel_center"));
        for (ServerPlayer player : level.players()) { // everybody in the village sees the celebration
            if (risen.area().contains(player.blockPosition().getX(), player.blockPosition().getZ()) && !risen.mayManage(player.getUUID())) {
                player.sendSystemMessage(Component.translatable("villagelife.growth.risen", risen.displayName(), name)
                        .withStyle(ChatFormatting.GOLD));
            }
        }
    }

    private static boolean overlapsOther(VillageManager manager, Village village, int radius) {
        VillageArea bigger = new VillageArea(village.center().getX(), village.center().getZ(), radius);
        return manager.all().stream().anyMatch(other -> !other.id().equals(village.id()) && other.area().overlaps(bigger));
    }

    /** Five rockets in different colours over the village center. */
    private static void celebrate(ServerLevel level, Village village) {
        if (!level.isLoaded(village.center())) {
            return;
        }
        BlockPos center = village.center();
        for (int i = 0; i < FIREWORK_COLORS.length; i++) {
            ItemStack rocket = new ItemStack(Items.FIREWORK_ROCKET);
            FireworkExplosion.Shape shape = i % 2 == 0 ? FireworkExplosion.Shape.LARGE_BALL : FireworkExplosion.Shape.STAR;
            rocket.set(DataComponents.FIREWORKS, new Fireworks(1 + i % 2, List.of(new FireworkExplosion(shape,
                    IntList.of(FIREWORK_COLORS[i]), IntList.of(0xFFFFFF), true, true))));
            double x = center.getX() + 0.5 + (i - 2) * 2.0;
            double z = center.getZ() + 0.5 + ((i % 2) * 2 - 1) * 1.5;
            level.addFreshEntity(new FireworkRocketEntity(level, x, center.getY() + 1.0, z, rocket));
        }
    }

    /** The supply parcel: into the storage, whatever does not fit at the village center. True if all fit. */
    private static boolean deliverParcel(ServerLevel level, Village village, int stage) {
        MarketService.loadStorage(level, village);
        boolean allStored = true;
        BlockPos center = village.center();
        for (StageReward.Stack stack : StageRewards.forStage(stage)) {
            Item item = BuiltInRegistries.ITEM.getValue(stack.item());
            if (item == Items.AIR) {
                continue;
            }
            int left = stack.count();
            while (left > 0) {
                ItemStack like = new ItemStack(item);
                int part = Math.min(left, like.getMaxStackSize());
                int fits = Math.min(part, MarketService.room(level, village, like));
                if (fits > 0) {
                    MarketService.insert(level, village, new ItemStack(item, fits));
                }
                if (fits < part) {
                    allStored = false;
                    level.addFreshEntity(new ItemEntity(level, center.getX() + 0.5, center.getY() + 1.0, center.getZ() + 0.5,
                            new ItemStack(item, part - fits)));
                }
                left -= part;
            }
        }
        return allStored;
    }

    // ---- book section --------------------------------------------------------------------------

    /** Section "growth": stage, its advantages, and the requirements of the next (and the current) stage. */
    public static void write(ServerPlayer player, Village village, CompoundTag out) {
        int stage = village.stage();
        Status status = status(player.level(), village);
        out.putInt("stage", stage);
        out.putInt("max_residents", maxResidents(village));
        out.putInt("radius", village.radius());
        out.putInt("max_contracts", VillageStages.maxContracts(stage));
        out.putInt("residents", status.residents());
        out.putInt("satisfaction", status.satisfaction());
        out.putInt("professions", status.professions());
        out.putBoolean("storage", status.storage());
        out.putBoolean("market", status.market());
        out.putInt("guards", status.guards());
        if (stage < VillageStages.MAX_STAGE) {
            out.put("next", requirements(VillageStages.requirementsFor(stage + 1)));
        }
        if (stage > 0) { // "4a": no falling back, only a warning
            out.putBoolean("below", !VillageStages.meets(VillageStages.requirementsFor(stage), status));
        }
    }

    private static CompoundTag requirements(Requirements r) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("residents", r.residents());
        tag.putInt("satisfaction", r.satisfaction());
        tag.putInt("professions", r.professions());
        tag.putBoolean("storage", r.storage());
        tag.putBoolean("market", r.market());
        tag.putInt("guards", r.guards());
        return tag;
    }
}
