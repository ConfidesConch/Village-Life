package io.github.confidesconch.villagelife.core.registry;

import net.neoforged.bus.api.IEventBus;

/**
 * Single place that attaches all deferred registers of the mod to the mod event bus.
 * New registers (entities, menus, data attachments, ...) are added here when a milestone needs them.
 */
public final class ModRegistries {
    private ModRegistries() {}

    public static void register(IEventBus modEventBus) {
        ModBlocks.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModCreativeTabs.CREATIVE_MODE_TABS.register(modEventBus);
        ModEntities.ENTITIES.register(modEventBus);
        modEventBus.addListener(ModEntities::onAttributeCreation);
    }
}
