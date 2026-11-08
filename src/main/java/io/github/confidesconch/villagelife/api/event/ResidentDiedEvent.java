package io.github.confidesconch.villagelife.api.event;

import java.util.UUID;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

/** A resident died and was removed from its village (defenders only get knocked out and do not die). */
public class ResidentDiedEvent extends VillageLifeEvent {
    private final Entity resident;

    public ResidentDiedEvent(ServerLevel level, UUID villageId, Entity resident) {
        super(level, villageId);
        this.resident = resident;
    }

    public Entity getResident() {
        return resident;
    }
}
