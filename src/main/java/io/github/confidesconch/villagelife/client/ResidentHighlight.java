package io.github.confidesconch.villagelife.client;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import io.github.confidesconch.villagelife.resident.ResidentEntity;

import net.minecraft.world.InteractionHand;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * A resident the player right-clicked glows for two minutes (owner's wish 15.11.2026): a gold outline
 * that is also seen through walls, only on this player's screen. Drawn by {@link ResidentRenderer}.
 */
public final class ResidentHighlight {
    private ResidentHighlight() {}

    /** Gold, like the selection in the village window. */
    public static final int COLOR = 0xFFE7C66B;
    private static final long DURATION_MS = 2 * 60 * 1000;

    private static final Map<UUID, Long> UNTIL = new HashMap<>();

    static void init() {
        // before the village window's own listener, which cancels the click (it would not reach us then)
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, ResidentHighlight::onInteract);
    }

    private static void onInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getLevel().isClientSide() && event.getHand() == InteractionHand.MAIN_HAND
                && event.getTarget() instanceof ResidentEntity resident) {
            UNTIL.clear(); // one at a time: the last one clicked
            UNTIL.put(resident.getUUID(), System.currentTimeMillis() + DURATION_MS);
        }
    }

    /** True while the resident should glow. */
    public static boolean isHighlighted(ResidentEntity resident) {
        if (UNTIL.isEmpty()) {
            return false;
        }
        Long until = UNTIL.get(resident.getUUID());
        if (until == null) {
            return false;
        }
        if (until < System.currentTimeMillis()) {
            UNTIL.remove(resident.getUUID());
            return false;
        }
        return true;
    }
}
