package io.github.confidesconch.villagelife.social;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * An invisible seat on a stair or slab (M21e, tavern as a bar): a guest "rides" it and is drawn sitting.
 * It exists only while somebody sits on it and the guest keeps it ({@link #keepUntil}); otherwise it
 * drops its rider and vanishes. That time is not saved, so after loading a world every guest stands up
 * and the seat disappears – nobody can stay stuck sitting.
 */
public class SeatEntity extends Entity {
    /** Seat surface of a bottom stair or slab, minus the thigh: where the rider's feet origin goes. */
    public static final double RIDER_DROP = 0.625;

    private long keepUntil;

    public SeatEntity(EntityType<? extends SeatEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        setInvisible(true);
    }

    /** The guest still sits here until this game time. */
    public void keepUntil(long gameTime) {
        keepUntil = gameTime;
    }

    @Override
    public void tick() {
        super.tick();
        // bug review 13.11.2026: also when the chair below was broken (nobody floats in the air)
        if (level() instanceof ServerLevel level && (level.getGameTime() > keepUntil || !isVehicle()
                || level.getBlockState(BlockPos.containing(getX(), getY() + RIDER_DROP, getZ())).isAir())) {
            ejectPassengers();
            discard();
        }
    }

    /** The rider's feet sit right at the seat's position. */
    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scale) {
        return Vec3.ZERO;
    }

    /** Stand up next to the seat on free floor (in front of a stair first), not inside the stair block. */
    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        BlockPos seat = BlockPos.containing(getX(), getY() + RIDER_DROP, getZ());
        for (Direction side : Direction.Plane.HORIZONTAL) {
            BlockPos next = seat.relative(side);
            if (level().isLoaded(next) && level().getBlockState(next).getCollisionShape(level(), next).isEmpty()
                    && level().getBlockState(next.above()).getCollisionShape(level(), next.above()).isEmpty()
                    && !level().getBlockState(next.below()).getCollisionShape(level(), next.below()).isEmpty()) {
                return Vec3.atBottomCenterOf(next);
            }
        }
        return new Vec3(getX(), seat.getY() + 1.0, getZ()); // on top of the stair
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        return false;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
    }
}
