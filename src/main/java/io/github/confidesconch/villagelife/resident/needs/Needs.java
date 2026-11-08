package io.github.confidesconch.villagelife.resident.needs;

import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * A resident's needs (M10): how full it is and in which night it last slept in its bed.
 * Stored with the entity (it is the body's state); the rules are in {@link NeedsRules}.
 * Residents in unloaded chunks do not get hungry. Newcomers get a grace period (wish of the owner,
 * 04.11.2026): for their first two Minecraft days they do not get hungry, count as rested and as
 * having a home, so neither food nor mood can drop.
 */
public final class Needs {
    private double food = NeedsRules.MAX_FOOD;
    private long lastSleptNight = Long.MIN_VALUE;
    /** Game time until which the newcomer's grace period lasts (0 = none). */
    private long graceUntil;

    public double food() {
        return food;
    }

    /** Starts the newcomer's grace period of {@code ticks} from {@code gameTime}. */
    public void startGrace(long gameTime, long ticks) {
        graceUntil = gameTime + ticks;
    }

    public boolean inGrace(long gameTime) {
        return gameTime < graceUntil;
    }

    /** Called regularly with the time passed since the last call. */
    public void update(long elapsedTicks, int foodPerDay, boolean sleeping, long dayTime, long gameTime) {
        if (!inGrace(gameTime)) {
            food = NeedsRules.drain(food, elapsedTicks, foodPerDay, sleeping);
        }
        if (sleeping) {
            lastSleptNight = NeedsRules.nightIndex(dayTime);
        }
    }

    public void eat(int nutrition) {
        food = NeedsRules.eat(food, nutrition);
    }

    public boolean isRested(long dayTime, long gameTime) {
        return inGrace(gameTime) || NeedsRules.isRested(lastSleptNight, dayTime);
    }

    public int satisfaction(long dayTime, long gameTime, boolean hasHome) {
        return NeedsRules.satisfaction(food, isRested(dayTime, gameTime), hasHome || inGrace(gameTime));
    }

    public double workSpeed(long dayTime, long gameTime, boolean hasHome) {
        return NeedsRules.workSpeed(food, satisfaction(dayTime, gameTime, hasHome));
    }

    public void save(ValueOutput output) {
        output.putDouble("food", food);
        output.putLong("last_slept_night", lastSleptNight);
        output.putLong("grace_until", graceUntil);
    }

    public void load(ValueInput input) {
        food = Math.max(0, Math.min(NeedsRules.MAX_FOOD, input.getDoubleOr("food", NeedsRules.MAX_FOOD)));
        lastSleptNight = input.getLongOr("last_slept_night", Long.MIN_VALUE);
        graceUntil = input.getLongOr("grace_until", 0L);
    }
}
