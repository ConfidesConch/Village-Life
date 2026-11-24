package io.github.confidesconch.villagelife.client.ui.core;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.neoforged.neoforge.client.settings.IKeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyConflictContext;

/**
 * Walking while one of the mod's windows is open (1.6, owner's wish, "like in other games").
 *
 * <p>Normally the game lets go of all keys when a window opens, and NeoForge only counts the movement
 * keys while no window is open ("in game"). While a village window or the minimap editor is open, this
 * reads the keyboard every tick and sets the movement keys (forward, back, left, right, jump, sneak,
 * sprint) to what is really held, counting them everywhere for that time. Sneak and sprint are left
 * alone when they are set to "toggle" (setting them every tick would switch them on and off). Nothing
 * happens while a text field is typed into or the minimap is being dragged; on closing, everything goes
 * back to how it was.
 */
public final class WalkWhileOpen {
    /** The keys changed right now, with the context they had before. */
    private static final Map<KeyMapping, IKeyConflictContext> CHANGED = new IdentityHashMap<>();

    private WalkWhileOpen() {
    }

    /** Called every tick by an open window; {@code paused}: typing or dragging, so no walking. */
    public static void update(boolean paused) {
        Minecraft minecraft = Minecraft.getInstance();
        if (paused || minecraft.player == null) {
            release();
            return;
        }
        Options options = minecraft.options;
        for (KeyMapping key : List.of(options.keyUp, options.keyDown, options.keyLeft, options.keyRight,
                options.keyJump, options.keyShift, options.keySprint)) {
            if (key.getKey().getType() != InputConstants.Type.KEYBOARD || key.isUnbound()) {
                continue; // a key on the mouse: the click belongs to the window
            }
            if (key == options.keyShift && options.toggleCrouch().get()
                    || key == options.keySprint && options.toggleSprint().get()) {
                continue;
            }
            CHANGED.putIfAbsent(key, key.getKeyConflictContext());
            key.setKeyConflictContext(KeyConflictContext.UNIVERSAL);
            key.setDown(InputConstants.isKeyDown(key.getKey().getValue()));
        }
    }

    /** Lets go of the movement keys and gives them their old context back. */
    public static void release() {
        CHANGED.forEach((key, context) -> {
            key.setDown(false);
            key.setKeyConflictContext(context);
        });
        CHANGED.clear();
    }
}
