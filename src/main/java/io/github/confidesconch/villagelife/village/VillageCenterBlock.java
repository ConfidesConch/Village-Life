package io.github.confidesconch.villagelife.village;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The village center (looks like a bell). Placing it founds a village, removing it dissolves the village.
 * All logic runs on the server via {@link VillageService}.
 */
public class VillageCenterBlock extends Block {

    public VillageCenterBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        if (context.getLevel() instanceof ServerLevel level
                && !VillageService.canFoundVillageAt(level, context.getClickedPos(), context.getPlayer())) {
            return null; // placement refused, item stays in the player's hand
        }
        return super.getStateForPlacement(context);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level instanceof ServerLevel serverLevel) {
            VillageService.onCenterPlaced(serverLevel, pos, placer);
        }
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        VillageService.onCenterRemoved(level, pos);
    }
}
