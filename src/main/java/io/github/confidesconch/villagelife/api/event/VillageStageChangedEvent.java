package io.github.confidesconch.villagelife.api.event;

import java.util.UUID;

import net.minecraft.server.level.ServerLevel;

/** The village rose to the next development stage (0 = settlement, 1 = village, 2 = town). */
public class VillageStageChangedEvent extends VillageLifeEvent {
    private final int oldStage;
    private final int newStage;

    public VillageStageChangedEvent(ServerLevel level, UUID villageId, int oldStage, int newStage) {
        super(level, villageId);
        this.oldStage = oldStage;
        this.newStage = newStage;
    }

    public int getOldStage() {
        return oldStage;
    }

    public int getNewStage() {
        return newStage;
    }
}
