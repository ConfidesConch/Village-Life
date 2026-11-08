package io.github.confidesconch.villagelife.building;

import java.util.Map;
import java.util.Optional;

import io.github.confidesconch.villagelife.village.Village;
import io.github.confidesconch.villagelife.village.VillageManager;

import net.minecraft.ChatFormatting;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Building marker (M18d, owner's wish "like TekTopia"): a frame hung on the outside (or inside) wall of a
 * self-built building, or standing on the floor, that shows the building type's symbol – house, chest or hammer. When
 * the building meets all requirements the frame glows purple and sparkles. Registers the room as a building.
 * Right-click on a registered marker opens the building's page in the village book (M11i, handled by the
 * book); otherwise right-click cycles the building type and rescans; sneak + right-click only rescans.
 * Has no collision, so it does not block the room it marks.
 */
public class BuildingMarkerBlock extends Block {
    public static final EnumProperty<BuildingType> TYPE = EnumProperty.create("building_type", BuildingType.class);
    /** The side the frame's picture faces; it hangs on the wall behind it. */
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    /** True while the building meets all requirements: the frame glows purple (M18d). */
    public static final BooleanProperty VALID = BooleanProperty.create("valid");
    /** The picture in the frame: house, storage or the workplace's profession (M18d). */
    public static final EnumProperty<MarkerSymbol> SYMBOL = EnumProperty.create("symbol", MarkerSymbol.class);

    private static final Map<Direction, VoxelShape> SHAPES = Map.of(
            Direction.NORTH, Block.box(1, 1, 15, 15, 15, 16),
            Direction.SOUTH, Block.box(1, 1, 0, 15, 15, 1),
            Direction.EAST, Block.box(0, 1, 1, 1, 15, 15),
            Direction.WEST, Block.box(15, 1, 1, 16, 15, 15));

    public BuildingMarkerBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(TYPE, BuildingType.HOUSE).setValue(FACING, Direction.NORTH)
                .setValue(VALID, false).setValue(SYMBOL, MarkerSymbol.HOUSE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(TYPE, FACING, VALID, SYMBOL);
    }

    /** On a wall: hangs there, facing into the room. On the floor: stands upright, facing the player. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction face = context.getClickedFace();
        Direction facing = face.getAxis().isHorizontal() ? face : context.getHorizontalDirection().getOpposite();
        return defaultBlockState().setValue(FACING, facing);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    /** A valid building's frame sparkles purple now and then (only drawn for players nearby). */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(VALID) && random.nextInt(4) == 0) {
            Direction facing = state.getValue(FACING);
            double x = pos.getX() + 0.5 - facing.getStepX() * 0.35 + (random.nextDouble() - 0.5) * 0.6;
            double z = pos.getZ() + 0.5 - facing.getStepZ() * 0.35 + (random.nextDouble() - 0.5) * 0.6;
            level.addParticle(ParticleTypes.WITCH, x, pos.getY() + 0.3 + random.nextDouble() * 0.5, z, 0, 0, 0);
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level instanceof ServerLevel serverLevel) {
            if (!mayMark(serverLevel, pos, player)) {
                return InteractionResult.SUCCESS;
            }
            BlockState newState = player.isShiftKeyDown() ? state : state.cycle(TYPE);
            if (newState != state) {
                level.setBlockAndUpdate(pos, newState);
            }
            BuildingService.scanAndRegister(serverLevel, pos, newState.getValue(TYPE), player);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level instanceof ServerLevel serverLevel && placer instanceof Player player && mayMark(serverLevel, pos, player)) {
            BuildingService.scanAndRegister(serverLevel, pos, state.getValue(TYPE), player);
        }
    }

    /**
     * Only the owner, managers and admins may mark buildings in a village or change their markers (found in
     * the bug review 08.11.2026: anybody could cycle the type of a stranger's marker). Outside villages
     * everybody may (the marker then reports that it stands in no village).
     */
    private static boolean mayMark(ServerLevel level, BlockPos pos, Player player) {
        Optional<Village> village = VillageManager.get(level).findAt(pos);
        if (village.isEmpty() || village.get().mayManage(player.getUUID())
                || player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
            return true;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.sendOverlayMessage(Component.translatable("villagelife.building.problem.not_yours",
                    village.get().displayName()).withStyle(ChatFormatting.RED));
        }
        return false;
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        BuildingService.onMarkerRemoved(level, pos);
    }
}
