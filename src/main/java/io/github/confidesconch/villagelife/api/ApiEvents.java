package io.github.confidesconch.villagelife.api;

import java.util.UUID;

import io.github.confidesconch.villagelife.api.event.BuildingRegisteredEvent;
import io.github.confidesconch.villagelife.api.event.BuildingRemovedEvent;
import io.github.confidesconch.villagelife.api.event.ResidentDiedEvent;
import io.github.confidesconch.villagelife.api.event.ResidentJoinedVillageEvent;
import io.github.confidesconch.villagelife.api.event.VillageDissolvedEvent;
import io.github.confidesconch.villagelife.api.event.VillageFoundedEvent;
import io.github.confidesconch.villagelife.api.event.VillageLifeEvent;
import io.github.confidesconch.villagelife.api.event.VillageStageChangedEvent;
import io.github.confidesconch.villagelife.building.Building;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Fires the addon events (M22a); called by the mod's own services. Not meant for addons. An addon that
 * throws in its listener must not break the village: such errors are logged and swallowed here.
 */
public final class ApiEvents {
    private ApiEvents() {}

    public static void villageFounded(ServerLevel level, UUID villageId, BlockPos center, UUID owner) {
        post(new VillageFoundedEvent(level, villageId, center, owner));
    }

    public static void villageDissolved(ServerLevel level, UUID villageId, BlockPos center) {
        post(new VillageDissolvedEvent(level, villageId, center));
    }

    public static void residentJoined(ServerLevel level, UUID villageId, Entity resident, boolean born) {
        post(new ResidentJoinedVillageEvent(level, villageId, resident, born));
    }

    public static void residentDied(ServerLevel level, UUID villageId, Entity resident) {
        post(new ResidentDiedEvent(level, villageId, resident));
    }

    public static void buildingRegistered(ServerLevel level, UUID villageId, Building building, boolean isNew) {
        post(new BuildingRegisteredEvent(level, villageId, VillageLifeApi.info(building), isNew));
    }

    public static void buildingRemoved(ServerLevel level, UUID villageId, Building building) {
        post(new BuildingRemovedEvent(level, villageId, VillageLifeApi.info(building)));
    }

    public static void stageChanged(ServerLevel level, UUID villageId, int oldStage, int newStage) {
        post(new VillageStageChangedEvent(level, villageId, oldStage, newStage));
    }

    private static void post(VillageLifeEvent event) {
        try {
            NeoForge.EVENT_BUS.post(event);
        } catch (RuntimeException e) {
            io.github.confidesconch.villagelife.VillageLife.LOGGER.error("An addon failed handling {}", event.getClass().getSimpleName(), e);
        }
    }
}
