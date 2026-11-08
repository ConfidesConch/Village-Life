package io.github.confidesconch.villagelife.api.event;

import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** The village center was removed: the village and all its data are gone. */
public class VillageDissolvedEvent extends VillageLifeEvent {
    private final BlockPos center;

    public VillageDissolvedEvent(ServerLevel level, UUID villageId, BlockPos center) {
        super(level, villageId);
        this.center = center;
    }

    public BlockPos getCenter() {
        return center;
    }
}
