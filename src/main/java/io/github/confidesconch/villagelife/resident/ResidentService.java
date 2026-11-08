package io.github.confidesconch.villagelife.resident;

import java.util.List;
import java.util.OptionalInt;
import java.util.Random;

import io.github.confidesconch.villagelife.VillageLife;
import io.github.confidesconch.villagelife.core.config.SyncedConfig;
import io.github.confidesconch.villagelife.core.registry.ModEntities;
import io.github.confidesconch.villagelife.village.Village;
import io.github.confidesconch.villagelife.village.VillageManager;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;

/**
 * Creates residents and keeps the village files ({@link ResidentRecord}) in sync with the entities.
 */
public final class ResidentService {
    private ResidentService() {}

    private static final Random RANDOM = new Random();
    /** Two Minecraft days without hunger and without a drop in mood. */
    static final long NEWCOMER_GRACE_TICKS = 2 * 24000L;
    /** What every newcomer brings along and puts into the storage. */
    private static final List<ItemStack> ARRIVAL_PACK = List.of(
            new ItemStack(Items.BREAD, 4), new ItemStack(Items.APPLE, 2), new ItemStack(Items.CARROT, 4));

    public sealed interface SpawnResult {
        record Spawned(ResidentEntity resident) implements SpawnResult {}
        record VillageFull(int max) implements SpawnResult {}
    }

    /** Creates a new resident of {@code village} standing at {@code pos}. */
    public static SpawnResult spawn(ServerLevel level, BlockPos pos, Village village) {
        return spawn(level, pos, village, false);
    }

    /** Like {@link #spawn(ServerLevel, BlockPos, Village)}; {@code ignoreLimit}: only for the stress test (M21). */
    public static SpawnResult spawn(ServerLevel level, BlockPos pos, Village village, boolean ignoreLimit) {
        int max = SyncedConfig.MAX_RESIDENTS_PER_VILLAGE.getAsInt();
        if (!ignoreLimit && village.residents().size() >= max) {
            return new SpawnResult.VillageFull(max);
        }
        // M21c: woman or man, the name fits; now and then one of the hidden names (07.11.2026)
        List<String> taken = village.residents().stream().map(r -> r.firstName() + " " + r.lastName()).toList();
        NameGenerator.Name name = NameGenerator.newcomer(RANDOM, taken);
        int skin = RANDOM.nextInt(); // M21c: look number, see ResidentLook

        ResidentEntity resident = new ResidentEntity(ModEntities.RESIDENT.get(), level);
        resident.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        resident.initialize(village.id(), name, skin);
        // newcomers (wish of the owner, 04.11.2026): two worry-free days and a little food for the storage
        resident.needs().startGrace(level.getGameTime(), NEWCOMER_GRACE_TICKS);
        ARRIVAL_PACK.forEach(stack -> resident.arrivalPack().add(stack.copy()));
        level.addFreshEntity(resident);

        VillageManager.get(level).addResident(village,
                ResidentRecord.create(resident.getUUID(), name.first(), name.last(), skin, level.getGameTime()));
        VillageLife.LOGGER.info("Resident {} ({}) joined village {}", name.full(), resident.getUUID(), village.id());
        io.github.confidesconch.villagelife.api.ApiEvents.residentJoined(level, village.id(), resident, false); // M22a
        return new SpawnResult.Spawned(resident);
    }

    /**
     * A child born in the village (M18b) at {@code pos}: a new first name, the family name of its
     * parents, no grace period or provisions. Grows up after {@code growUpTicks}.
     */
    public static ResidentEntity spawnChild(ServerLevel level, BlockPos pos, Village village, String lastName,
                                            List<java.util.UUID> parents, long growUpTicks) {
        NameGenerator.Name name = new NameGenerator.Name(NameGenerator.random(RANDOM).first(), lastName);
        int skin = RANDOM.nextInt();
        ResidentEntity child = new ResidentEntity(ModEntities.RESIDENT.get(), level);
        child.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        child.initialize(village.id(), name, skin);
        child.setChildSize(true);
        level.addFreshEntity(child);
        long now = level.getGameTime();
        VillageManager.get(level).addResident(village,
                ResidentRecord.child(child.getUUID(), name.first(), name.last(), skin, now, parents, now + growUpTicks));
        VillageLife.LOGGER.info("Child {} ({}) was born in village {}", name.full(), child.getUUID(), village.id());
        io.github.confidesconch.villagelife.api.ApiEvents.residentJoined(level, village.id(), child, true); // M22a
        return child;
    }

    public static void onDeath(ServerLevel level, ResidentEntity resident) {
        VillageManager manager = VillageManager.get(level);
        resident.getVillageId().flatMap(manager::byId).ifPresent(village -> {
            manager.removeResident(village, resident.getUUID());
            VillageLife.LOGGER.info("Resident {} of village {} died", resident.getFullName(), village.id());
            io.github.confidesconch.villagelife.api.ApiEvents.residentDied(level, village.id(), resident); // M22a
            io.github.confidesconch.villagelife.village.EventLog.record(level.getServer(), village, "residents",
                    Component.translatable("villagelife.event.died", resident.getFullName())); // M21b3
            ServerPlayer owner = level.getServer().getPlayerList().getPlayer(village.owner());
            if (owner != null) {
                owner.sendSystemMessage(Component.translatable("villagelife.resident.died",
                        resident.getFullName(), village.displayName()).withStyle(ChatFormatting.GRAY));
            }
        });
    }

    /** The village's residents that are currently loaded (entities near players). */
    public static List<ResidentEntity> loadedResidents(ServerLevel level, Village village) {
        BlockPos center = village.center();
        int radius = village.radius();
        AABB box = new AABB(center.getX() - radius, level.getMinY(), center.getZ() - radius,
                center.getX() + radius + 1, level.getMaxY(), center.getZ() + radius + 1);
        return level.getEntitiesOfClass(ResidentEntity.class, box,
                resident -> resident.getVillageId().filter(village.id()::equals).isPresent());
    }

    /** Average satisfaction of the loaded residents, empty if none is loaded (M10). */
    public static OptionalInt averageSatisfaction(ServerLevel level, Village village) {
        List<ResidentEntity> residents = loadedResidents(level, village);
        if (residents.isEmpty()) {
            return OptionalInt.empty();
        }
        return OptionalInt.of((int) Math.round(residents.stream().mapToInt(ResidentEntity::satisfaction).average().orElse(0)));
    }
}
