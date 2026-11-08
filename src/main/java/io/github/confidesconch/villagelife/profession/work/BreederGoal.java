package io.github.confidesconch.villagelife.profession.work;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.github.confidesconch.villagelife.resident.ResidentEntity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Work of the breeder (M13d): cows, pigs and chickens in its work area. Picks up the eggs the chickens
 * lay, milks each cow once a day if it has empty buckets (fetched from storage), breeds every kind with
 * its own feed up to the village's herd limit and slaughters adults above it (beef, leather, pork,
 * chicken, feathers). The herd limit counts each kind separately.
 */
public class BreederGoal extends HerdGoal<Animal> {
    public static final String PROFESSION = "villagelife:breeder";

    /** A cow gives milk once per Minecraft day. */
    private static final long MILK_COOLDOWN_TICKS = 24000;
    private static final int BUCKETS_TO_KEEP = 4;

    private final Map<UUID, Long> milkedAt = new HashMap<>();

    public BreederGoal(ResidentEntity resident, double speedModifier) {
        super(resident, speedModifier, PROFESSION, ItemTags.SWORDS, Items.WOODEN_SWORD, Animal.class);
    }

    @Override
    protected boolean accepts(Animal animal) {
        return animal instanceof Cow || animal instanceof Pig || animal instanceof Chicken;
    }

    @Override
    protected boolean isFeed(ItemStack stack) {
        return stack.is(ItemTags.COW_FOOD) || stack.is(ItemTags.PIG_FOOD) || stack.is(ItemTags.CHICKEN_FOOD);
    }

    @Override
    protected int keepMaterial(ItemStack stack) {
        return stack.is(Items.BUCKET) ? BUCKETS_TO_KEEP : super.keepMaterial(stack);
    }

    @Override
    protected boolean isLooseProduct(ItemStack stack) {
        return stack.is(ItemTags.EGGS) || stack.is(Items.FEATHER);
    }

    @Override
    protected Supply neededSupply(List<Animal> adults) {
        if (hasBucket() || adults.stream().noneMatch(this::wantsMilking)) {
            return null;
        }
        return new Supply("buckets", stack -> stack.is(Items.BUCKET), BUCKETS_TO_KEEP);
    }

    @Override
    protected Animal specialTarget(List<Animal> adults) {
        if (!hasBucket()) {
            return null;
        }
        return adults.stream().filter(this::wantsMilking).findFirst().orElse(null);
    }

    @Override
    protected void doSpecial(ServerLevel level, Animal cow) {
        ItemStack bucket = resident.takeCarried(stack -> stack.is(Items.BUCKET), 1);
        if (bucket.isEmpty()) {
            return;
        }
        level.playSound(null, cow, SoundEvents.COW_MILK, SoundSource.NEUTRAL, 1.0F, 1.0F);
        resident.carry(new ItemStack(Items.MILK_BUCKET));
        milkedAt.put(cow.getUUID(), level.getGameTime());
    }

    @Override
    protected boolean specialDrops() {
        return false;
    }

    @Override
    protected boolean specialWearsTool() {
        return false;
    }

    private boolean wantsMilking(Animal animal) {
        if (!(animal instanceof Cow) || animal.isBaby()) {
            return false;
        }
        Long last = milkedAt.get(animal.getUUID());
        return last == null || animal.level().getGameTime() - last >= MILK_COOLDOWN_TICKS;
    }

    private boolean hasBucket() {
        return resident.carriedCount(stack -> stack.is(Items.BUCKET)) > 0;
    }
}
