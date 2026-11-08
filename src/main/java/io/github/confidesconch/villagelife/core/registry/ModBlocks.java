package io.github.confidesconch.villagelife.core.registry;

import io.github.confidesconch.villagelife.VillageLife;
import io.github.confidesconch.villagelife.building.BuildingMarkerBlock;
import io.github.confidesconch.villagelife.village.VillageCenterBlock;

import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/** All blocks of Village Life. */
public final class ModBlocks {
    private ModBlocks() {}

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(VillageLife.MOD_ID);

    /**
     * Founds a village. Explosion-proof and not movable by pistons, so a village cannot be
     * dissolved by accident.
     */
    public static final DeferredBlock<VillageCenterBlock> VILLAGE_CENTER = BLOCKS.registerBlock("village_center",
            VillageCenterBlock::new,
            properties -> properties
                    .mapColor(MapColor.GOLD)
                    .strength(5.0F, 3_600_000.0F)
                    .sound(SoundType.ANVIL)
                    .noOcclusion()
                    .pushReaction(PushReaction.IMMOVEABLE));

    /** Registers a self-built room as a building. No collision, so it never blocks the room. */
    public static final DeferredBlock<BuildingMarkerBlock> BUILDING_MARKER = BLOCKS.registerBlock("building_marker",
            BuildingMarkerBlock::new,
            properties -> properties
                    .mapColor(MapColor.WOOD)
                    .strength(1.0F)
                    .sound(SoundType.WOOD)
                    .lightLevel(state -> state.getValue(BuildingMarkerBlock.VALID) ? 6 : 0) // M18d: valid buildings glow
                    .noCollision()
                    .noOcclusion()
                    .pushReaction(PushReaction.IMMOVEABLE));
}
