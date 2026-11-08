package io.github.confidesconch.villagelife.api.event;

import java.util.UUID;

import io.github.confidesconch.villagelife.api.VillageLifeApi;

import net.minecraft.server.level.ServerLevel;

/** A building marker was removed and its building unregistered. */
public class BuildingRemovedEvent extends VillageLifeEvent {
    private final VillageLifeApi.BuildingInfo building;

    public BuildingRemovedEvent(ServerLevel level, UUID villageId, VillageLifeApi.BuildingInfo building) {
        super(level, villageId);
        this.building = building;
    }

    /** The building as it was before removal. */
    public VillageLifeApi.BuildingInfo getBuilding() {
        return building;
    }
}
