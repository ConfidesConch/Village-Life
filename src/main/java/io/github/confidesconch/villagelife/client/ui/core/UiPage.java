package io.github.confidesconch.villagelife.client.ui.core;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * One area of the village window (M21b1), e.g. "Residents": an entry in the navigation and the content
 * beside it. New systems (version 1.5 …) add areas via {@link UiPages#register}; nothing else changes.
 * A page only reads its sections of the server's data and sends actions – it never decides anything.
 */
public interface UiPage {
    /** Unique id, also used to remember the open area. */
    String id();

    /** Symbol in the navigation. */
    ItemStack icon();

    Component title();

    /** Position in the navigation, small first. */
    int order();

    /** False for areas that also work without a village (map, player). */
    default boolean needsVillage() {
        return true;
    }

    /**
     * Where the player's real inventory slots go on this page (M21b10a, owner's choice "3b"): the corner
     * the slot positions of {@code VillageBookMenu} are relative to, or null (default) for no inventory.
     */
    default int[] inventoryOrigin(UiRect area) {
        return null;
    }

    /** Sections of the server data this area reads (M21b2); asked for when it opens and every few seconds. */
    default java.util.List<String> sections() {
        return java.util.List.of();
    }

    /** Called when the area is opened from the navigation. */
    default void onOpen() {
    }

    /**
     * Opened from another area with a hint what to show (M21b4), e.g. "citizens:hungry" opens the
     * residents filtered to the hungry ones. Called after {@link #onOpen}.
     */
    default void focus(String hint) {
    }

    /** A key while this area is open; true if used (e.g. typing into a search field). */
    default boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        return false;
    }

    default boolean charTyped(net.minecraft.client.input.CharacterEvent event) {
        return false;
    }

    /** A mouse click somewhere: text fields lose the focus (a click on a field gives it back). */
    default void blur() {
    }

    /** Draws the content into {@code area}; {@code data} is the whole snapshot of the server. */
    void render(Ui ui, UiRect area, CompoundTag data);

    /** Mouse wheel over the content; true if used. */
    default boolean mouseScrolled(double x, double y, double amount) {
        return false;
    }

    /** Mouse dragged with the left button held (scroll bars, map); true if used. */
    default boolean mouseDragged(double x, double y) {
        return false;
    }

    default void mouseReleased() {
    }
}
