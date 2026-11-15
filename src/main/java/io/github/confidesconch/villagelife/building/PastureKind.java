package io.github.confidesconch.villagelife.building;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Which animals a pasture is for (M28b, owner's choice "at the pasture sign, move the wrong ones"): set by
 * right-clicking the pasture's marker, which then shows the animal. Stored in the marker block itself
 * (its symbol), so nothing new is saved. {@link #ALL} is the old open pasture.
 */
public enum PastureKind {
    ALL(MarkerSymbol.PASTURE),
    COWS(MarkerSymbol.PASTURE_COW),
    PIGS(MarkerSymbol.PASTURE_PIG),
    CHICKENS(MarkerSymbol.PASTURE_CHICKEN),
    SHEEP(MarkerSymbol.PASTURE_SHEEP);

    private final MarkerSymbol symbol;

    PastureKind(MarkerSymbol symbol) {
        this.symbol = symbol;
    }

    public MarkerSymbol symbol() {
        return symbol;
    }

    /** The next kind when the sign is right-clicked. */
    public PastureKind next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public Component displayName() {
        return Component.translatable("villagelife.pasture.kind." + name().toLowerCase(java.util.Locale.ROOT));
    }

    /** True for the animals that belong here ({@link #ALL}: every herd animal). */
    public boolean accepts(Entity animal) {
        return switch (this) {
            case ALL -> isHerdAnimal(animal);
            case COWS -> animal instanceof Cow;
            case PIGS -> animal instanceof Pig;
            case CHICKENS -> animal instanceof Chicken;
            case SHEEP -> animal instanceof Sheep;
        };
    }

    /** True if a herder of this profession looks after such a pasture (sheep: shepherd; others: breeder). */
    public boolean forShepherd() {
        return this == SHEEP;
    }

    /** Animals the herders keep: cows, pigs, chickens, sheep. */
    public static boolean isHerdAnimal(Entity animal) {
        return animal instanceof Cow || animal instanceof Pig || animal instanceof Chicken || animal instanceof Sheep;
    }

    public static PastureKind ofSymbol(MarkerSymbol symbol) {
        for (PastureKind kind : values()) {
            if (kind.symbol == symbol) {
                return kind;
            }
        }
        return ALL;
    }

    /** Switches the pasture marker at {@code marker} to the next kind; returns the new kind. */
    public static PastureKind cycle(ServerLevel level, BlockPos marker) {
        BlockState state = level.getBlockState(marker);
        PastureKind next = ofSymbol(state.getValue(BuildingMarkerBlock.SYMBOL)).next();
        level.setBlock(marker, state.setValue(BuildingMarkerBlock.SYMBOL, next.symbol()), net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
        level.playSound(null, marker, net.minecraft.sounds.SoundEvents.ITEM_FRAME_ROTATE_ITEM,
                net.minecraft.sounds.SoundSource.BLOCKS, 0.8F, 1.0F);
        return next;
    }

    /** The kind of a pasture, read from its marker (ALL while the marker is not loaded). */
    public static PastureKind of(ServerLevel level, Building pasture) {
        BlockPos marker = pasture.markerPos();
        if (!level.isLoaded(marker)) {
            return ALL;
        }
        BlockState state = level.getBlockState(marker);
        return state.hasProperty(BuildingMarkerBlock.SYMBOL) ? ofSymbol(state.getValue(BuildingMarkerBlock.SYMBOL)) : ALL;
    }
}
