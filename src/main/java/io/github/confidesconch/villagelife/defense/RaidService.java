package io.github.confidesconch.villagelife.defense;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import io.github.confidesconch.villagelife.inventory.StorageAccess;
import io.github.confidesconch.villagelife.profession.Professions;
import io.github.confidesconch.villagelife.resident.ResidentEntity;
import io.github.confidesconch.villagelife.resident.ai.ResidentHooks;
import io.github.confidesconch.villagelife.resident.schedule.ScheduleClock;
import io.github.confidesconch.villagelife.village.NotificationTopic;
import io.github.confidesconch.villagelife.village.Village;
import io.github.confidesconch.villagelife.village.VillageManager;
import io.github.confidesconch.villagelife.village.VillageService;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Bandit attacks at night (M20b, owner's choice "1a 2a 3a 4a", bandits instead of monsters): from stage
 * "village" bandits attack on about every 5th night at 22:00. Their number and equipment grow with the
 * village's defenders – very easy at first. They only fight defenders (and players); during the attack
 * the alarm sends everybody else home. All bandits beaten: their loot goes into the storage and the
 * village is happier the next day. Bandits that are not fighting walk to a storage chest, take a
 * handful and run out of the village with it ({@link BanditRaidGoal}); killed on the way, they drop it.
 * Running attacks are not saved: after a restart leftover bandits are ordinary illagers.
 */
public final class RaidService {
    private RaidService() {}

    public static final String BANDIT_TAG = "villagelife_bandit";
    private static final int START_CHECK_TICKS = 100;
    private static final int UPDATE_TICKS = 20;
    /** Bandits appear this far outside the village border and disappear again a bit further out. */
    private static final int SPAWN_OUTSIDE = 12;
    private static final int ESCAPE_OUTSIDE = 18;
    /** An attack started during the day (debug) lasts at most six hours. */
    private static final long DAY_ATTACK_TICKS = 6000;
    private static final int RINGS = 5;
    private static final int VICTORY_MOOD = 5;
    private static final long VICTORY_MOOD_TICKS = 24000;

    /** A running attack: the bandits still around and what happened so far. */
    private static final class Raid {
        final Set<UUID> bandits = new HashSet<>();
        int beaten;
        int escaped;
        int stolen;
    }

    private static final Map<UUID, Raid> RAIDS = new HashMap<>();
    /** What each bandit carries away (it drops it when killed). */
    private static final Map<UUID, List<ItemStack>> LOOT = new HashMap<>();
    private static final Map<UUID, Long> LAST_CHECKED_DAY = new HashMap<>();
    private static final Map<UUID, Long> VICTORY_UNTIL = new HashMap<>();

    /** Forgets everything kept in memory for the stopped server (bug review 08.11.2026). */
    public static void clear() {
        RAIDS.clear();
        LOOT.clear();
        LAST_CHECKED_DAY.clear();
        VICTORY_UNTIL.clear();
    }

    public static boolean isUnderAttack(UUID village) {
        return RAIDS.containsKey(village);
    }

    /** +5 mood the day after a won defense (owner's choice "3a"). */
    public static int eventMood(UUID village, long gameTime) {
        return VICTORY_UNTIL.getOrDefault(village, 0L) > gameTime ? VICTORY_MOOD : 0;
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        for (ServerLevel level : event.getServer().getAllLevels()) {
            long now = level.getGameTime();
            if (now % UPDATE_TICKS != 0) {
                continue;
            }
            int minute = ScheduleClock.minuteOfDay(level.getOverworldClockTime());
            for (Village village : List.copyOf(VillageManager.get(level).all())) {
                Raid raid = RAIDS.get(village.id());
                if (raid != null) {
                    update(level, village, raid);
                } else if (now % START_CHECK_TICKS == 0 && isAttackNight(level, village, minute)) {
                    start(level, village);
                }
            }
        }
    }

    /** Once per night at 22:00: stage "village", attacks switched on, 20 % chance. */
    private static boolean isAttackNight(ServerLevel level, Village village, int minute) {
        if (village.stage() < 1 || !village.settings().attacks()
                || minute < DefenseRules.ATTACK_FROM_MINUTE || minute >= DefenseRules.ATTACK_UNTIL_MINUTE) {
            return false;
        }
        long day = level.getOverworldClockTime() / ScheduleClock.TICKS_PER_DAY;
        Long last = LAST_CHECKED_DAY.put(village.id(), day);
        return (last == null || last != day) && level.getRandom().nextDouble() < DefenseRules.ATTACK_CHANCE;
    }

    /** Starts an attack now (also {@code /villagelife debug attack}); false if the place outside is not loaded. */
    public static boolean start(ServerLevel level, Village village) {
        double power = defenderPower(village);
        int count = DefenseRules.banditCount(power);
        int banditLevel = DefenseRules.banditLevel(power);
        int crossbows = DefenseRules.crossbowBandits(count, banditLevel);
        double angle = level.getRandom().nextDouble() * Math.PI * 2;
        BlockPos center = village.center();
        BlockPos camp = around(level, center, angle, village.radius() + SPAWN_OUTSIDE);
        if (camp == null) {
            return false;
        }
        long retreatAt = retreatTime(level);
        Raid raid = new Raid();
        for (int i = 0; i < count; i++) {
            BlockPos at = around(level, camp, level.getRandom().nextDouble() * Math.PI * 2, level.getRandom().nextInt(3));
            Mob bandit = at == null ? null : spawnBandit(level, at, i < crossbows, banditLevel);
            if (bandit != null) {
                bandit.goalSelector.addGoal(3, new BanditRaidGoal(bandit, village.id(), center, camp,
                        village.radius() + ESCAPE_OUTSIDE, retreatAt));
                raid.bandits.add(bandit.getUUID());
            }
        }
        if (raid.bandits.isEmpty()) {
            return false;
        }
        RAIDS.put(village.id(), raid);
        AlarmService.ring(level, village, RINGS, null); // like a church clock
        VillageService.tellManagers(level.getServer(), village, NotificationTopic.DEFENSE,
                Component.translatable("villagelife.defense.attack", village.displayName(), raid.bandits.size())
                        .withStyle(ChatFormatting.RED));
        return true;
    }

    /** A point on the ground {@code distance} blocks from {@code from} in direction {@code angle}, or null if not loaded. */
    private static BlockPos around(ServerLevel level, BlockPos from, double angle, int distance) {
        int x = from.getX() + (int) Math.round(Math.cos(angle) * distance);
        int z = from.getZ() + (int) Math.round(Math.sin(angle) * distance);
        if (!level.isLoaded(new BlockPos(x, from.getY(), z))) {
            return null;
        }
        return new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
    }

    /** Bandits run at 6:00; an attack started during the day (debug) after six hours. */
    private static long retreatTime(ServerLevel level) {
        long now = level.getGameTime();
        int minute = ScheduleClock.minuteOfDay(level.getOverworldClockTime());
        if (minute >= DefenseRules.RETREAT_FROM_MINUTE && minute < DefenseRules.ATTACK_FROM_MINUTE) {
            return now + DAY_ATTACK_TICKS;
        }
        int untilMorning = Math.floorMod(DefenseRules.RETREAT_FROM_MINUTE - minute, 24 * 60);
        return now + untilMorning * 1000L / 60;
    }

    /** Defenders' strength from the jobs of the village. */
    public static double defenderPower(Village village) {
        int guards = 0;
        int archers = 0;
        int captains = 0;
        for (var record : village.residents()) {
            String profession = record.job().map(job -> job.profession()).orElse("");
            switch (profession) {
                case DefenseRules.ARCHER -> archers++;
                case DefenseRules.CAPTAIN -> captains++;
                default -> guards += Professions.get(profession).map(p -> p.defender()).orElse(false) ? 1 : 0;
            }
        }
        return DefenseRules.defenderPower(guards, archers, captains);
    }

    /** A bandit at {@code at} that goes for defenders and players (also used for raids on convoys, M20c). */
    public static Mob spawnBandit(ServerLevel level, BlockPos at, boolean crossbow, int banditLevel) {
        Mob bandit = crossbow ? EntityTypes.PILLAGER.create(level, EntitySpawnReason.EVENT)
                : EntityTypes.VINDICATOR.create(level, EntitySpawnReason.EVENT);
        if (bandit == null) {
            return null;
        }
        bandit.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, level.getRandom().nextFloat() * 360.0F, 0.0F);
        bandit.finalizeSpawn(level, level.getCurrentDifficultyAt(at), EntitySpawnReason.EVENT, null);
        if (!crossbow) {
            Item[] swords = {Items.WOODEN_SWORD, Items.STONE_SWORD, Items.IRON_SWORD, Items.IRON_SWORD, Items.DIAMOND_SWORD};
            bandit.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(swords[banditLevel]));
            bandit.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(1.0); // the sword decides the damage
        }
        bandit.getAttribute(Attributes.MAX_HEALTH).setBaseValue(DefenseRules.banditHealth(banditLevel));
        bandit.setHealth(DefenseRules.banditHealth(banditLevel));
        bandit.setCustomName(Component.translatable("villagelife.entity.bandit"));
        bandit.addTag(BANDIT_TAG);
        // they really walk in: through doors to the storage chests (no tricks)
        bandit.getNavigation().setCanOpenDoors(true);
        bandit.goalSelector.addGoal(2, new OpenDoorGoal(bandit, true));
        // owner's choice "2a": bandits only go for defenders (and players, as illagers always do)
        bandit.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(bandit, ResidentEntity.class, false,
                (target, serverLevel) -> target instanceof ResidentEntity resident && ResidentHooks.isDefender(resident)
                        && !resident.isKnockedOut()));
        level.addFreshEntity(bandit);
        return bandit;
    }

    // ---- during the attack ---------------------------------------------------------------------

    private static void update(ServerLevel level, Village village, Raid raid) {
        Iterator<UUID> it = raid.bandits.iterator();
        while (it.hasNext()) {
            UUID id = it.next();
            Entity entity = level.getEntity(id);
            if (entity == null) { // chunk unloaded or gone in some other way
                it.remove();
                LOOT.remove(id);
            } else if (!entity.isAlive()) {
                it.remove();
                raid.beaten++;
            } else if (entity instanceof Mob bandit && bandit.getTarget() instanceof ResidentEntity resident
                    && resident.isKnockedOut()) {
                bandit.setTarget(null); // leave knocked-out defenders alone
            }
        }
        if (!raid.bandits.isEmpty()) {
            return;
        }
        RAIDS.remove(village.id());
        if (raid.escaped == 0) {
            VICTORY_UNTIL.put(village.id(), level.getGameTime() + VICTORY_MOOD_TICKS);
            VillageService.tellManagers(level.getServer(), village, NotificationTopic.DEFENSE,
                    Component.translatable("villagelife.defense.won", village.displayName()).withStyle(ChatFormatting.GREEN));
        } else {
            VillageService.tellManagers(level.getServer(), village, NotificationTopic.DEFENSE,
                    Component.translatable("villagelife.defense.plundered", village.displayName(), raid.escaped, raid.stolen,
                            raid.beaten).withStyle(ChatFormatting.RED));
        }
    }

    /** A bandit took {@code stolen} from a chest and now carries it ({@link BanditRaidGoal}). */
    static void carry(UUID bandit, ItemStack stolen) {
        LOOT.computeIfAbsent(bandit, k -> new ArrayList<>()).add(stolen);
    }

    /** A bandit got away (out of the village): its loot is lost. */
    static void escaped(Mob bandit) {
        for (Raid raid : RAIDS.values()) {
            if (raid.bandits.remove(bandit.getUUID())) {
                raid.escaped++;
                raid.stolen += LOOT.getOrDefault(bandit.getUUID(), List.of()).stream().mapToInt(ItemStack::getCount).sum();
            }
        }
        LOOT.remove(bandit.getUUID());
    }

    /** Bandit loot (and one emerald each) goes into the storage of the attacked village (owner's choice "3a"). */
    public static void onDrops(LivingDropsEvent event) {
        if (!(event.getEntity().level() instanceof ServerLevel level) || !event.getEntity().entityTags().contains(BANDIT_TAG)) {
            return;
        }
        UUID banditId = event.getEntity().getUUID();
        UUID villageId = RAIDS.entrySet().stream().filter(e -> e.getValue().bandits.contains(banditId))
                .map(Map.Entry::getKey).findFirst().orElse(null);
        Village village = villageId == null ? null : VillageManager.get(level).byId(villageId).orElse(null);
        if (village == null) {
            return;
        }
        List<ItemStack> loot = new ArrayList<>(LOOT.getOrDefault(banditId, List.of())); // stolen goods come back
        LOOT.remove(banditId);
        event.getDrops().forEach(item -> loot.add(item.getItem().copy()));
        loot.add(new ItemStack(Items.EMERALD));
        List<ItemStack> rest = store(level, village, loot);
        event.getDrops().clear();
        for (ItemStack stack : rest) {
            event.getDrops().add(new ItemEntity(level, event.getEntity().getX(), event.getEntity().getY(), event.getEntity().getZ(), stack));
        }
    }

    private static List<ItemStack> store(ServerLevel level, Village village, List<ItemStack> loot) {
        List<ItemStack> rest = new ArrayList<>();
        List<BlockPos> containers = StorageAccess.containers(village, null, village.center());
        for (ItemStack stack : loot) {
            ItemStack left = stack;
            for (BlockPos pos : containers) {
                Container container = StorageAccess.containerAt(level, pos);
                if (container != null && !left.isEmpty()) {
                    left = StorageAccess.insert(container, left);
                }
            }
            if (!left.isEmpty()) {
                rest.add(left);
            }
        }
        return rest;
    }
}
