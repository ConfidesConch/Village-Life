package io.github.confidesconch.villagelife.family;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import io.github.confidesconch.villagelife.core.config.SyncedConfig;
import io.github.confidesconch.villagelife.resident.ResidentEntity;
import io.github.confidesconch.villagelife.resident.ResidentRecord;
import io.github.confidesconch.villagelife.resident.ResidentService;
import io.github.confidesconch.villagelife.village.Village;
import io.github.confidesconch.villagelife.village.NotificationTopic;
import io.github.confidesconch.villagelife.village.VillageManager;
import io.github.confidesconch.villagelife.village.VillageService;
import io.github.confidesconch.villagelife.village.VillageStages;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Families (M18b, owner's choice "2a 3a 4a"): once a minute every couple living in the same house may
 * have a child ({@link FamilyRules}) – if a bed is free there and the village's stage has room. The
 * child appears next to a parent (or at the parents' bed) and grows up after five days, with a small
 * celebration. Immigration goes on as before.
 */
public final class FamilyService {
    private FamilyService() {}

    static final int UPDATE_INTERVAL_TICKS = 1200;
    private static final int OFFSET = 600;

    public static void onServerTick(ServerTickEvent.Post event) {
        for (ServerLevel level : event.getServer().getAllLevels()) {
            long now = level.getGameTime();
            VillageManager manager = VillageManager.get(level);
            for (Village village : List.copyOf(manager.all())) {
                if (Math.floorMod(now + OFFSET + village.id().hashCode(), UPDATE_INTERVAL_TICKS) == 0) {
                    update(level, manager, village, now);
                }
            }
        }
    }

    private static void update(ServerLevel level, VillageManager manager, Village village, long now) {
        announceGrownUps(level, village, now);
        if (!level.isLoaded(village.center())) {
            return;
        }
        for (ResidentRecord parent : village.residents()) {
            Optional<ResidentRecord> partner = parent.partner().flatMap(village::residentById);
            // each couple once: from the partner with the smaller id
            if (partner.isEmpty() || parent.id().compareTo(partner.get().id()) > 0) {
                continue;
            }
            Village current = manager.byId(village.id()).orElse(village);
            if (FamilyRules.hasChild(childrenOf(current, parent.id(), partner.get().id()), freeBedAtHome(current, parent, partner.get()),
                    hasRoom(current), level.getRandom().nextDouble())) {
                birth(level, current, parent, partner.get());
            }
        }
    }

    /** Debug ({@code /villagelife debug baby}): the first couple of the village gets a child now. */
    public static boolean debugBirth(ServerLevel level, Village village) {
        for (ResidentRecord parent : village.residents()) {
            Optional<ResidentRecord> partner = parent.partner().flatMap(village::residentById);
            if (partner.isPresent()) {
                birth(level, village, parent, partner.get());
                return true;
            }
        }
        return false;
    }

    private static void birth(ServerLevel level, Village village, ResidentRecord a, ResidentRecord b) {
        BlockPos pos = place(level, village, a, b);
        ResidentEntity child = ResidentService.spawnChild(level, pos, village, a.lastName(), List.of(a.id(), b.id()),
                FamilyRules.GROW_UP_TICKS);
        level.sendParticles(ParticleTypes.HEART, child.getX(), child.getY() + 0.8, child.getZ(), 8, 0.4, 0.3, 0.4, 0.0);
        VillageService.tellManagers(level.getServer(), village, NotificationTopic.FAMILY, Component.translatable("villagelife.family.born",
                a.fullName(), b.fullName(), child.getFullName()).withStyle(ChatFormatting.LIGHT_PURPLE));
    }

    /** Next to a loaded parent, else at the parents' bed, else at the village center. */
    private static BlockPos place(ServerLevel level, Village village, ResidentRecord a, ResidentRecord b) {
        for (ResidentRecord parent : List.of(a, b)) {
            Entity entity = level.getEntity(parent.id());
            if (entity != null) {
                return entity.blockPosition();
            }
        }
        return a.bed().map(BlockPos::above).orElse(village.center().above());
    }

    private static void announceGrownUps(ServerLevel level, Village village, long now) {
        for (ResidentRecord r : village.residents()) {
            long since = now - r.grownUpGameTime();
            if (r.grownUpGameTime() > 0 && since >= 0 && since < UPDATE_INTERVAL_TICKS) {
                VillageService.tellManagers(level.getServer(), village, NotificationTopic.FAMILY, Component.translatable("villagelife.family.grown_up",
                        r.fullName()).withStyle(ChatFormatting.GREEN));
                Entity entity = level.getEntity(r.id());
                if (entity != null) {
                    level.sendParticles(ParticleTypes.HAPPY_VILLAGER, entity.getX(), entity.getY() + 2.2, entity.getZ(),
                            10, 0.4, 0.3, 0.4, 0.0);
                }
            }
        }
    }

    static int childrenOf(Village village, UUID a, UUID b) {
        return (int) village.residents().stream().filter(r -> r.parents().contains(a) && r.parents().contains(b)).count();
    }

    /** Both live in the same house, and one of its beds is still free. */
    private static boolean freeBedAtHome(Village village, ResidentRecord a, ResidentRecord b) {
        if (a.home().isEmpty() || !a.home().equals(b.home())) {
            return false;
        }
        UUID home = a.home().get();
        int beds = village.buildings().stream().filter(building -> building.id().equals(home))
                .mapToInt(building -> building.beds().size()).sum();
        long used = village.residents().stream().filter(r -> r.home().map(home::equals).orElse(false)).count();
        return beds > used;
    }

    private static boolean hasRoom(Village village) {
        int max = Math.min(SyncedConfig.MAX_RESIDENTS_PER_VILLAGE.getAsInt(), VillageStages.maxResidents(village.stage()));
        return village.residents().size() < max;
    }
}
