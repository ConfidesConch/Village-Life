package io.github.confidesconch.villagelife.profession.work;

import java.util.function.Predicate;

import io.github.confidesconch.villagelife.building.Building;
import io.github.confidesconch.villagelife.resident.ResidentEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;

/**
 * Looks after one real furnace-like block in a workplace (cook's smoker M13f, smith's blast furnace
 * M13g): says what the oven needs next (raw input, fuel, emptying) and fills/empties it from the
 * worker's inventory. The oven keeps working while the worker is away, like in Minecraft.
 */
final class OvenTender {
    private static final int SLOT_INPUT = 0;
    private static final int SLOT_FUEL = 1;
    private static final int SLOT_RESULT = 2;

    /** What to do next for the oven. */
    enum Need { NONE, FETCH_RAW, FETCH_FUEL, TEND, NO_FUEL }

    private final ResidentEntity worker;
    private final Class<? extends AbstractFurnaceBlockEntity> ovenType;
    private final Predicate<ItemStack> isRaw;
    private BlockPos oven;

    OvenTender(ResidentEntity worker, Class<? extends AbstractFurnaceBlockEntity> ovenType, Predicate<ItemStack> isRaw) {
        this.worker = worker;
        this.ovenType = ovenType;
        this.isRaw = isRaw;
    }

    /** Coal, charcoal, logs and planks – never tools, saplings or a lava bucket. */
    static boolean isFuel(ItemStack stack) {
        return stack.is(ItemTags.COALS) || stack.is(ItemTags.LOGS_THAT_BURN) || stack.is(ItemTags.PLANKS);
    }

    /** Finds the oven among the workplace's workstations; null if there is none. */
    BlockPos find(ServerLevel level, Building workplace) {
        oven = null;
        for (BlockPos pos : workplace.workstations()) {
            if (level.isLoaded(pos) && ovenType.isInstance(level.getBlockEntity(pos))) {
                oven = pos;
                break;
            }
        }
        return oven;
    }

    BlockPos oven() {
        return oven;
    }

    /** True while something lies in the input slot (it is being cooked or waits for fuel). */
    boolean hasInput(ServerLevel level) {
        AbstractFurnaceBlockEntity furnace = furnace(level);
        return furnace != null && !furnace.getItem(SLOT_INPUT).isEmpty();
    }

    boolean hasResult(ServerLevel level) {
        AbstractFurnaceBlockEntity furnace = furnace(level);
        return furnace != null && !furnace.getItem(SLOT_RESULT).isEmpty();
    }

    /** The next thing the oven needs; fetching is only suggested if it is allowed right now. */
    Need need(ServerLevel level, boolean mayFetchRaw, boolean mayFetchFuel) {
        AbstractFurnaceBlockEntity furnace = furnace(level);
        if (furnace == null) {
            return Need.NONE;
        }
        if (!furnace.getItem(SLOT_RESULT).isEmpty()) {
            return Need.TEND;
        }
        ItemStack input = furnace.getItem(SLOT_INPUT);
        boolean hasRaw = worker.carriedCount(isRaw) > 0;
        if (!hasRaw && input.isEmpty() && mayFetchRaw) {
            return Need.FETCH_RAW;
        }
        // room in the input slot for something the worker carries (the slot takes one kind at a time)
        boolean inputFree = input.isEmpty() ? hasRaw : input.getCount() < input.getMaxStackSize()
                && worker.carriedCount(stack -> ItemStack.isSameItemSameComponents(stack, input)) > 0;
        boolean needsFuel = (!input.isEmpty() || hasRaw) && furnace.getItem(SLOT_FUEL).isEmpty();
        boolean hasFuel = worker.carriedCount(OvenTender::isFuel) > 0;
        if (needsFuel && !hasFuel && mayFetchFuel) {
            return Need.FETCH_FUEL;
        }
        if (inputFree || (needsFuel && hasFuel)) {
            return Need.TEND;
        }
        if (needsFuel && !hasFuel && !furnace.getBlockState().getValue(AbstractFurnaceBlock.LIT)) {
            return Need.NO_FUEL;
        }
        return Need.NONE;
    }

    /** Takes out what is done (into the worker's inventory) and puts in raw input and fuel. */
    void tend(ServerLevel level, SoundEvent sound) {
        AbstractFurnaceBlockEntity furnace = furnace(level);
        if (furnace == null) {
            return;
        }
        ItemStack done = furnace.removeItem(SLOT_RESULT, 64);
        if (!done.isEmpty()) {
            worker.carry(done);
            level.playSound(null, oven, sound, SoundSource.BLOCKS, 0.6F, 1.0F);
        }
        insert(furnace, SLOT_INPUT, isRaw);
        insert(furnace, SLOT_FUEL, OvenTender::isFuel);
        furnace.setChanged();
    }

    private AbstractFurnaceBlockEntity furnace(ServerLevel level) {
        return oven != null && level.isLoaded(oven) && level.getBlockEntity(oven) instanceof AbstractFurnaceBlockEntity furnace
                ? furnace : null;
    }

    /** Puts carried items matching {@code wanted} into the slot (same kind only, up to a full stack). */
    private void insert(AbstractFurnaceBlockEntity furnace, int slot, Predicate<ItemStack> wanted) {
        ItemStack inSlot = furnace.getItem(slot);
        Predicate<ItemStack> fits = inSlot.isEmpty()
                ? wanted : stack -> wanted.test(stack) && ItemStack.isSameItemSameComponents(stack, inSlot);
        int room = inSlot.isEmpty() ? 64 : inSlot.getMaxStackSize() - inSlot.getCount();
        if (room <= 0) {
            return;
        }
        ItemStack taken = worker.takeCarried(fits, room);
        if (taken.isEmpty()) {
            return;
        }
        if (inSlot.isEmpty()) {
            furnace.setItem(slot, taken);
        } else {
            inSlot.grow(taken.getCount());
        }
    }
}
