package io.github.confidesconch.villagelife.core.registry;

import io.github.confidesconch.villagelife.VillageLife;

import net.minecraft.world.item.BlockItem;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** All items of Village Life (including block items). Every item is shown in the Village Life creative tab. */
public final class ModItems {
    private ModItems() {}

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(VillageLife.MOD_ID);

    public static final DeferredItem<BlockItem> VILLAGE_CENTER = ITEMS.registerSimpleBlockItem(ModBlocks.VILLAGE_CENTER);

    public static final DeferredItem<BlockItem> BUILDING_MARKER = ITEMS.registerSimpleBlockItem(ModBlocks.BUILDING_MARKER);
}
