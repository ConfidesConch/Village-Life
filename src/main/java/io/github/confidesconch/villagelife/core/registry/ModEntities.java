package io.github.confidesconch.villagelife.core.registry;

import io.github.confidesconch.villagelife.VillageLife;
import io.github.confidesconch.villagelife.resident.ResidentEntity;
import io.github.confidesconch.villagelife.social.SeatEntity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** All entity types of Village Life. */
public final class ModEntities {
    private ModEntities() {}

    public static final DeferredRegister.Entities ENTITIES = DeferredRegister.createEntities(VillageLife.MOD_ID);

    /** Player-sized village resident. MISC: never spawns naturally. */
    public static final DeferredHolder<EntityType<?>, EntityType<ResidentEntity>> RESIDENT = ENTITIES.registerEntityType(
            "resident", ResidentEntity::new, MobCategory.MISC,
            builder -> builder.sized(0.6F, 1.8F).clientTrackingRange(10));

    /** M21e: invisible seat on stairs and slabs that tavern guests sit on; vanishes when nobody sits on it. */
    public static final DeferredHolder<EntityType<?>, EntityType<SeatEntity>> SEAT = ENTITIES.registerEntityType(
            "seat", SeatEntity::new, MobCategory.MISC,
            builder -> builder.sized(0.1F, 0.1F).clientTrackingRange(10).noSummon());

    /** Mod bus: attributes (health, speed) of our living entities. */
    public static void onAttributeCreation(EntityAttributeCreationEvent event) {
        event.put(RESIDENT.get(), ResidentEntity.createAttributes().build());
    }
}
