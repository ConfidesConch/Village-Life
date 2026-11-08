package io.github.confidesconch.villagelife.profession.work;

import io.github.confidesconch.villagelife.profession.work.OvenTender.Need;
import io.github.confidesconch.villagelife.resident.ResidentEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.entity.SmokerBlockEntity;

/**
 * Work of the cook (M13f, owner's choice "1a 2a"): uses the real smoker in the kitchen
 * ({@link OvenTender}). Fetches raw food (everything the smoker can cook: meat, fish, potatoes) and
 * fuel from storage, fills the smoker, takes out what is done and brings it to storage. The smoker
 * keeps cooking while the cook is away. Without fuel it reports the problem in the book.
 */
public class CookGoal extends WorkGoal {
    public static final String PROFESSION = "villagelife:cook";
    /** Shown in the book as {@code villagelife.problem.work.cook.no_fuel}. */
    public static final String ISSUE_NO_FUEL = "cook.no_fuel";
    /** Shown in the book as {@code villagelife.problem.work.cook.no_food} (07.11.2026). */
    public static final String ISSUE_NO_FOOD = "cook.no_food";

    private static final int RAW_TO_FETCH = 32;
    private static final int FUEL_TO_FETCH = 8;
    private static final double OVEN_DISTANCE = 2.5;

    private enum Phase { TO_OVEN, FETCH_RAW, FETCH_FUEL, DELIVER }

    private final OvenTender smoker;
    private Phase phase;
    private int phaseTicks;

    public CookGoal(ResidentEntity resident, double speedModifier) {
        super(resident, speedModifier, PROFESSION, ItemTags.SHOVELS, Items.BOWL); // a bowl in the hand; no real tool
        this.smoker = new OvenTender(resident, SmokerBlockEntity.class, this::isRaw);
    }

    @Override
    public boolean canUse() {
        if (!isWorkTime() || !(resident.level() instanceof ServerLevel level)) {
            return false;
        }
        return smoker.find(level, workplace()) != null && chooseWork(level);
    }

    @Override
    public boolean canContinueToUse() {
        return isWorkTime() && phase != null;
    }

    @Override
    public void stop() {
        phase = null;
        super.stop();
    }

    /** Raw food and fuel stay with the cook until they are in the smoker; cooked food is delivered. */
    @Override
    protected int keepMaterial(ItemStack stack) {
        return OvenTender.isFuel(stack) ? FUEL_TO_FETCH : isRaw(stack) ? RAW_TO_FETCH * 2 : 0;
    }

    /** Deliver what is cooked, fetch raw food or fuel if the smoker needs it, or tend the smoker. */
    private boolean chooseWork(ServerLevel level) {
        if (hasSomethingToDeliver() && (isFull() || !smoker.hasResult(level))) {
            setPhase(Phase.DELIVER);
            return true;
        }
        Need need = smoker.need(level, mayFetch(level, "raw"), mayFetch(level, "fuel"));
        // nothing to cook: no raw food carried, none in the smoker, and the storage had none either
        boolean noFood = resident.carriedCount(this::isRaw) == 0 && !smoker.hasInput(level) && !mayFetch(level, "raw");
        String issue = need == Need.NO_FUEL ? ISSUE_NO_FUEL : noFood ? ISSUE_NO_FOOD : null;
        String now = resident.brain().workIssue();
        if (issue != null || ISSUE_NO_FUEL.equals(now) || ISSUE_NO_FOOD.equals(now)) {
            resident.brain().setWorkIssue(issue);
        }
        switch (need) {
            case FETCH_RAW -> setPhase(Phase.FETCH_RAW);
            case FETCH_FUEL -> setPhase(Phase.FETCH_FUEL);
            case TEND -> setPhase(Phase.TO_OVEN);
            default -> {
                if (!hasSomethingToDeliver()) {
                    return false;
                }
                setPhase(Phase.DELIVER);
            }
        }
        return true;
    }

    @Override
    public void tick() {
        if (!(resident.level() instanceof ServerLevel level) || phase == null) {
            return;
        }
        phaseTicks++;
        switch (phase) {
            case TO_OVEN -> tendOven(level);
            case FETCH_RAW -> {
                if (fetchStep(level, "raw", this::isRaw, RAW_TO_FETCH) != FetchStatus.RUNNING) {
                    next(level);
                }
            }
            case FETCH_FUEL -> {
                if (fetchStep(level, "fuel", OvenTender::isFuel, FUEL_TO_FETCH) != FetchStatus.RUNNING) {
                    next(level);
                }
            }
            case DELIVER -> {
                if (deliverStep(level)) {
                    next(level);
                }
            }
        }
    }

    private void next(ServerLevel level) {
        if (!chooseWork(level)) {
            phase = null;
        }
    }

    private void setPhase(Phase next) {
        phase = next;
        phaseTicks = 0;
        resetPath();
    }

    /** Walks to the smoker, takes out what is done, puts in raw food and fuel. */
    private void tendOven(ServerLevel level) {
        BlockPos oven = smoker.oven();
        if (oven == null) {
            phase = null;
            return;
        }
        if (!oven.closerToCenterThan(resident.position(), OVEN_DISTANCE)) {
            if (phaseTicks > GIVE_UP_TICKS) {
                phase = null;
                return;
            }
            moveTowards(oven, 1);
            return;
        }
        resident.getNavigation().stop();
        resident.getLookControl().setLookAt(oven.getX() + 0.5, oven.getY() + 0.5, oven.getZ() + 0.5);
        swing();
        smoker.tend(level, SoundEvents.SMOKER_SMOKE);
        next(level);
    }

    /** Food the smoker can cook (raw meat, fish, potatoes …). */
    private boolean isRaw(ItemStack stack) {
        return !stack.isEmpty() && resident.level() instanceof ServerLevel level
                && level.recipeAccess().getRecipeFor(RecipeType.SMOKING, new SingleRecipeInput(stack), level).isPresent();
    }
}
