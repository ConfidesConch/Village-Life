package io.github.confidesconch.villagelife.api.event;

import java.util.UUID;

import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.Event;

/**
 * Base of all Village Life events for addon mods (M22a). Fired on {@code NeoForge.EVENT_BUS}, on the server
 * thread only, after the change happened (none can be cancelled). Use {@link
 * io.github.confidesconch.villagelife.api.VillageLifeApi} to look up more about the village.
 */
public abstract class VillageLifeEvent extends Event {
    private final ServerLevel level;
    private final UUID villageId;

    protected VillageLifeEvent(ServerLevel level, UUID villageId) {
        this.level = level;
        this.villageId = villageId;
    }

    /** The dimension the village lies in. */
    public ServerLevel getLevel() {
        return level;
    }

    public UUID getVillageId() {
        return villageId;
    }
}
