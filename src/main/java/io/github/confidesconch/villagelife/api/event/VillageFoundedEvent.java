package io.github.confidesconch.villagelife.api.event;

import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** A player placed a village center and founded a village. */
public class VillageFoundedEvent extends VillageLifeEvent {
    private final BlockPos center;
    private final UUID owner;

    public VillageFoundedEvent(ServerLevel level, UUID villageId, BlockPos center, UUID owner) {
        super(level, villageId);
        this.center = center;
        this.owner = owner;
    }

    public BlockPos getCenter() {
        return center;
    }

    /** The founding player. */
    public UUID getOwner() {
        return owner;
    }
}
