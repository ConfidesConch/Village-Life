package io.github.confidesconch.villagelife.api.event;

import java.util.UUID;

import io.github.confidesconch.villagelife.api.VillageLifeApi;

import net.minecraft.server.level.ServerLevel;

/**
 * A player placed or clicked a building marker and the building was (re)registered – valid or with a
 * problem. Automatic rescans do not fire this event.
 */
public class BuildingRegisteredEvent extends VillageLifeEvent {
    private final VillageLifeApi.BuildingInfo building;
    private final boolean isNew;

    public BuildingRegisteredEvent(ServerLevel level, UUID villageId, VillageLifeApi.BuildingInfo building, boolean isNew) {
        super(level, villageId);
        this.building = building;
        this.isNew = isNew;
    }

    public VillageLifeApi.BuildingInfo getBuilding() {
        return building;
    }

    /** True if the marker was not registered before. */
    public boolean isNew() {
        return isNew;
    }
}
