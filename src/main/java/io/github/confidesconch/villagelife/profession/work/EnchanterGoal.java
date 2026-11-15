package io.github.confidesconch.villagelife.profession.work;

import java.util.function.Predicate;

import io.github.confidesconch.villagelife.resident.ResidentEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EnchantingTableBlock;
import net.minecraft.resources.ResourceKey;

/**
 * Work of the enchanter (M26, owner's choice "bookshelves, useful enchantments, players pay emeralds"):
 * takes village tools from the storage, enchants them at his table – efficiency, then unbreaking, as
 * strong as the bookshelves around the table allow ({@link EnchanterRules#tier}) – and brings them back.
 * Nothing is used up. Workers then pick the faster tools themselves (efficiency counts in their tool
 * speed). Enchanting for players: {@code market.EnchantingService}.
 */
public class EnchanterGoal extends WorkGoal {
    public static final String PROFESSION = "villagelife:enchanter";

    private static final int SHELF_CHECK_TICKS = 200;

    private enum Phase { FETCH, TO_TABLE, ENCHANT, DELIVER }

    private Phase phase = Phase.FETCH;
    private int phaseTicks;
    private int tier = 1;
    private long nextShelfCheck;

    public EnchanterGoal(ResidentEntity resident, double speedModifier) {
        // the "tool tag" only decides what he keeps for himself: books (he never carries any), so every
        // enchanted tool goes back to the storage
        super(resident, speedModifier, PROFESSION, ItemTags.BOOKSHELF_BOOKS, Items.BOOK);
    }

    /** Strength (1–3) of an enchanter working at this table, from the bookshelves around it. */
    public static int tierAt(Level level, BlockPos table) {
        int shelves = 0;
        for (BlockPos offset : EnchantingTableBlock.BOOKSHELF_OFFSETS) {
            if (EnchantingTableBlock.isValidBookShelf(level, table, offset)) {
                shelves++;
            }
        }
        return EnchanterRules.tier(Math.min(shelves, EnchanterRules.MAX_SHELVES));
    }

    /** The enchanting table among the workstations of a workplace, or null. */
    public static BlockPos tableOf(Level level, Iterable<BlockPos> workstations) {
        for (BlockPos pos : workstations) {
            if (level.isLoaded(pos) && level.getBlockState(pos).is(Blocks.ENCHANTING_TABLE)) {
                return pos;
            }
        }
        return null;
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
        setPhase(Phase.FETCH);
    }

    @Override
    protected boolean deliversToWorkplace() {
        return false; // the tools belong in the storage, where the workers look for them
    }

    @Override
    public void tick() {
        if (!(resident.level() instanceof ServerLevel level)) {
            return;
        }
        phaseTicks++;
        BlockPos table = tableOf(level, workplace().workstations());
        if (table == null) {
            return;
        }
        if (level.getGameTime() >= nextShelfCheck) {
            nextShelfCheck = level.getGameTime() + SHELF_CHECK_TICKS;
            tier = tierAt(level, table);
        }
        switch (phase) {
            case FETCH -> fetch(level, table);
            case TO_TABLE -> {
                if (table.closerToCenterThan(resident.position(), 2.5)) {
                    resident.getNavigation().stop();
                    setPhase(Phase.ENCHANT);
                } else {
                    moveTowards(table, 1);
                }
            }
            case ENCHANT -> enchant(level, table);
            case DELIVER -> {
                if (deliverStep(level)) {
                    setPhase(Phase.FETCH);
                }
            }
        }
    }

    // ---- steps -------------------------------------------------------------------------------------

    private void fetch(ServerLevel level, BlockPos table) {
        if (resident.carriedCount(candidate()) > 0) {
            setPhase(Phase.TO_TABLE);
            return;
        }
        if (hasSomethingToDeliver()) {
            setPhase(Phase.DELIVER);
            return;
        }
        FetchStatus status = fetchStep(level, "enchant", candidate(), 1);
        if (status == FetchStatus.DONE) {
            setPhase(resident.carriedCount(candidate()) > 0 ? Phase.TO_TABLE : Phase.FETCH);
        } else if (status == FetchStatus.NOTHING) {
            standAt(table); // nothing to enchant: reads at his table
        }
    }

    private void enchant(ServerLevel level, BlockPos table) {
        resident.getLookControl().setLookAt(table.getX() + 0.5, table.getY() + 1.0, table.getZ() + 0.5);
        if (phaseTicks % 20 == 1) {
            swing();
            level.sendParticles(ParticleTypes.ENCHANT, table.getX() + 0.5, table.getY() + 1.2, table.getZ() + 0.5,
                    12, 0.8, 0.4, 0.8, 0.6);
        }
        if (phaseTicks < EnchanterRules.ENCHANT_TICKS / workSpeed()) {
            return;
        }
        ItemStack tool = resident.takeCarried(candidate());
        if (tool.isEmpty()) {
            setPhase(Phase.FETCH);
            return;
        }
        EnchanterRules.Step step = step(tool);
        if (step != EnchanterRules.Step.NONE) {
            tool.enchant(holder(level, step == EnchanterRules.Step.EFFICIENCY ? Enchantments.EFFICIENCY : Enchantments.UNBREAKING), tier);
            level.playSound(null, table, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.BLOCKS, 0.8F, 1.0F);
        }
        resident.carry(tool);
        // both enchantments in one go if the tool can take another one; otherwise back to the storage
        setPhase(step(tool) != EnchanterRules.Step.NONE ? Phase.ENCHANT : Phase.DELIVER);
    }

    // ---- helpers -----------------------------------------------------------------------------------

    /** Village tools he can still improve at the current strength (not worn out). */
    private Predicate<ItemStack> candidate() {
        return stack -> isVillageTool(stack) && !ToolTiers.isWorn(stack.getDamageValue(), stack.getMaxDamage())
                && step(stack) != EnchanterRules.Step.NONE;
    }

    private EnchanterRules.Step step(ItemStack tool) {
        return EnchanterRules.next(ToolEnchants.level(tool, Enchantments.EFFICIENCY),
                ToolEnchants.level(tool, Enchantments.UNBREAKING), tier, digs(tool));
    }

    private static boolean isVillageTool(ItemStack stack) {
        return digs(stack) || stack.is(Items.SHEARS) || stack.is(Items.FISHING_ROD);
    }

    private static boolean digs(ItemStack stack) {
        return stack.is(ItemTags.AXES) || stack.is(ItemTags.PICKAXES) || stack.is(ItemTags.SHOVELS)
                || stack.is(ItemTags.HOES) || stack.is(Items.SHEARS);
    }

    private static Holder<Enchantment> holder(ServerLevel level, ResourceKey<Enchantment> key) {
        return level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
    }

    private void standAt(BlockPos table) {
        if (!table.closerToCenterThan(resident.position(), 2.5)) {
            moveTowards(table, 1);
        } else {
            resident.getNavigation().stop();
        }
    }

    private void setPhase(Phase next) {
        phase = next;
        phaseTicks = 0;
    }
}
