package io.github.confidesconch.villagelife.profession.work;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import io.github.confidesconch.villagelife.resident.ResidentEntity;
import io.github.confidesconch.villagelife.resident.ResidentService;
import io.github.confidesconch.villagelife.resident.ai.ResidentHooks;
import io.github.confidesconch.villagelife.resident.schedule.ScheduleClock;
import io.github.confidesconch.villagelife.village.Village;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Items;

/**
 * Work of the healer (M24, owner's choice "1a 2a 3a": cauldron, goes to the injured, heals for free):
 * walks to residents of the village who are hurt – also knocked-out defenders, who are back on duty once
 * healed – and treats them heart by heart. Every morning he first blesses the village at its center:
 * residents work {@link HealerRules#BLESSING_SPEED 10 %} faster until 18:00. Otherwise he stands at his
 * cauldron. Blessings are kept in memory only (they end the same evening anyway).
 */
public class HealerGoal extends WorkGoal {
    public static final String PROFESSION = "villagelife:healer";

    private static final int SCAN_TICKS = 100;
    private static final double REACH = 2.0;
    private static final double CENTER_RANGE = 4.0;
    private static final int BLESS_TICKS = 60;
    /** A patient that could not be reached is left alone for this long. */
    private static final long SKIP_TICKS = 1200;

    /** Village → game time until which it is blessed; village → day of the last blessing. */
    private static final Map<UUID, Long> BLESSED_UNTIL = new HashMap<>();
    private static final Map<UUID, Long> BLESSED_DAY = new HashMap<>();

    private enum Phase { IDLE, TO_PATIENT, TREAT, TO_CENTER, BLESS }

    private final Map<UUID, Long> skipUntil = new HashMap<>();
    private Phase phase = Phase.IDLE;
    private ResidentEntity patient;
    private int phaseTicks;
    private long nextScan;

    public HealerGoal(ResidentEntity resident, double speedModifier) {
        super(resident, speedModifier, PROFESSION, ItemTags.SHOVELS, Items.POTION); // a flask in the hand
    }

    /** Work speed factor from the morning blessing (1.0 if the resident's village is not blessed). */
    public static double workBonus(ResidentEntity resident) {
        Village village = resident.brain().village();
        if (village == null) {
            return 1.0;
        }
        Long until = BLESSED_UNTIL.get(village.id());
        return until != null && until > resident.level().getGameTime() ? HealerRules.BLESSING_SPEED : 1.0;
    }

    /** Forgets all blessings when the server stops. */
    public static void clear() {
        BLESSED_UNTIL.clear();
        BLESSED_DAY.clear();
    }

    @Override
    public boolean canUse() {
        return isWorkTime() && resident.level() instanceof ServerLevel;
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void start() {
        super.start();
        setPhase(Phase.IDLE);
    }

    @Override
    public void stop() {
        patient = null;
        super.stop();
    }

    @Override
    public void tick() {
        if (!(resident.level() instanceof ServerLevel level)) {
            return;
        }
        phaseTicks++;
        long now = level.getGameTime();
        switch (phase) {
            case IDLE -> idle(level, now);
            case TO_PATIENT -> toPatient(now);
            case TREAT -> treat(level, now);
            case TO_CENTER -> {
                BlockPos center = resident.brain().village().center();
                if (horizontallyNear(center, CENTER_RANGE) || phaseTicks > GIVE_UP_TICKS) {
                    resident.getNavigation().stop();
                    setPhase(Phase.BLESS);
                } else {
                    moveTowards(center, 3);
                }
            }
            case BLESS -> bless(level, now);
        }
    }

    // ---- standing, finding patients ----------------------------------------------------------------

    private void idle(ServerLevel level, long now) {
        Village village = resident.brain().village();
        long clock = level.getOverworldClockTime();
        if (village != null && HealerRules.blessingDue(ScheduleClock.minuteOfDay(clock), HealerRules.day(clock),
                BLESSED_DAY.getOrDefault(village.id(), Long.MIN_VALUE))) {
            resetPath();
            setPhase(Phase.TO_CENTER);
            return;
        }
        if (now >= nextScan) {
            nextScan = now + SCAN_TICKS;
            patient = findPatient(level, village, now);
            if (patient != null) {
                resetPath();
                setPhase(Phase.TO_PATIENT);
                return;
            }
        }
        BlockPos cauldron = cauldron(level);
        if (cauldron != null && !cauldron.closerToCenterThan(resident.position(), 2.5)) {
            moveTowards(cauldron, 1);
        } else {
            resident.getNavigation().stop();
        }
    }

    /** The nearest hurt resident of the village (the healer himself too), or null. */
    private ResidentEntity findPatient(ServerLevel level, Village village, long now) {
        if (village == null) {
            return null;
        }
        ResidentEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (ResidentEntity other : ResidentService.loadedResidents(level, village)) {
            if (other.isAlive() && other.getHealth() < other.getMaxHealth()
                    && skipUntil.getOrDefault(other.getUUID(), 0L) <= now) {
                double distance = other.distanceToSqr(resident);
                if (distance < bestDistance) {
                    best = other;
                    bestDistance = distance;
                }
            }
        }
        return best;
    }

    private boolean needsCare(ResidentEntity other) {
        return other != null && other.isAlive() && !other.isRemoved() && other.getHealth() < other.getMaxHealth();
    }

    // ---- treating ----------------------------------------------------------------------------------

    private void toPatient(long now) {
        if (!needsCare(patient)) {
            setPhase(Phase.IDLE);
            return;
        }
        if (patient == resident || patient.distanceTo(resident) <= REACH) {
            resident.getNavigation().stop();
            setPhase(Phase.TREAT);
            return;
        }
        if (phaseTicks > GIVE_UP_TICKS) { // cannot get there: try the others first
            skipUntil.put(patient.getUUID(), now + SKIP_TICKS);
            setPhase(Phase.IDLE);
            return;
        }
        if (phaseTicks % 20 == 1) { // the patient may walk around: aim again every second
            resident.getNavigation().moveTo(patient, speedModifier);
        }
    }

    private void treat(ServerLevel level, long now) {
        if (!needsCare(patient)) {
            setPhase(Phase.IDLE);
            return;
        }
        if (patient != resident && patient.distanceTo(resident) > REACH + 1) {
            setPhase(Phase.TO_PATIENT);
            return;
        }
        if (patient != resident) {
            resident.getLookControl().setLookAt(patient, 30.0F, 30.0F);
            // bug review 13.11.2026: never hold back a defender, he may be fighting
            if (!ResidentHooks.isDefender(patient) || patient.isKnockedOut()) {
                patient.getNavigation().stop(); // holds still for a moment
            }
        }
        if (phaseTicks % 10 == 0) {
            swing();
        }
        if (phaseTicks < HealerRules.TREATMENT_TICKS / workSpeed()) {
            return;
        }
        patient.setHealth(HealerRules.healed(patient.getHealth(), patient.getMaxHealth()));
        level.sendParticles(ParticleTypes.HEART, patient.getX(), patient.getY() + patient.getBbHeight() + 0.3,
                patient.getZ(), 2, 0.3, 0.2, 0.3, 0.0);
        resident.playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 0.8F, 1.2F);
        phaseTicks = 0;
        if (patient.getHealth() >= patient.getMaxHealth()) {
            if (patient.isKnockedOut()) {
                patient.knockOutUntil(now); // a healed defender is back on duty at once
            }
            nextScan = now; // look for the next one right away
            setPhase(Phase.IDLE);
        }
    }

    // ---- the morning blessing ----------------------------------------------------------------------

    private void bless(ServerLevel level, long now) {
        if (phaseTicks % 20 == 1) {
            swing();
            level.sendParticles(ParticleTypes.ENCHANT, resident.getX(), resident.getY() + 1.5, resident.getZ(),
                    20, 0.6, 0.6, 0.6, 0.5);
        }
        if (phaseTicks < BLESS_TICKS) {
            return;
        }
        Village village = resident.brain().village();
        long clock = level.getOverworldClockTime();
        BLESSED_UNTIL.put(village.id(), HealerRules.blessingEnds(now, clock));
        BLESSED_DAY.put(village.id(), HealerRules.day(clock));
        resident.playSound(SoundEvents.BELL_RESONATE, 1.0F, 1.2F);
        for (ResidentEntity blessed : ResidentService.loadedResidents(level, village)) {
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, blessed.getX(), blessed.getY() + blessed.getBbHeight() + 0.4,
                    blessed.getZ(), 4, 0.3, 0.2, 0.3, 0.0);
        }
        setPhase(Phase.IDLE);
    }

    // ---- helpers -----------------------------------------------------------------------------------

    private BlockPos cauldron(ServerLevel level) {
        for (BlockPos pos : workplace().workstations()) {
            if (level.isLoaded(pos) && level.getBlockState(pos).is(BlockTags.CAULDRONS)) {
                return pos;
            }
        }
        return null;
    }

    private boolean horizontallyNear(BlockPos pos, double range) {
        double dx = resident.getX() - (pos.getX() + 0.5);
        double dz = resident.getZ() - (pos.getZ() + 0.5);
        return dx * dx + dz * dz <= range * range;
    }

    private void setPhase(Phase next) {
        phase = next;
        phaseTicks = 0;
    }
}
