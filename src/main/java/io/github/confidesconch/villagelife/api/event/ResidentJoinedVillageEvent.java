package io.github.confidesconch.villagelife.api.event;

import java.util.UUID;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

/** A newcomer moved in or a child was born. */
public class ResidentJoinedVillageEvent extends VillageLifeEvent {
    private final Entity resident;
    private final boolean born;

    public ResidentJoinedVillageEvent(ServerLevel level, UUID villageId, Entity resident, boolean born) {
        super(level, villageId);
        this.resident = resident;
        this.born = born;
    }

    /** The resident entity (type {@code villagelife:resident}). */
    public Entity getResident() {
        return resident;
    }

    /** True for a child born in the village, false for a newcomer. */
    public boolean isBorn() {
        return born;
    }
}
