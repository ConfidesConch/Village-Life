package io.github.confidesconch.villagelife.core.util;

/**
 * The 16 colours players can pick for waypoints and zones (M11e). Stored as an index 0–15, so
 * the palette can be tuned later without touching saved data.
 */
public final class MarkerColors {
    private MarkerColors() {}

    /** ARGB, fully opaque; roughly the Minecraft dye colours, a bit brighter for maps. */
    private static final int[] PALETTE = {
            0xFFF9FFFE, 0xFFF9801D, 0xFFC74EBD, 0xFF3AB3DA,
            0xFFFED83D, 0xFF80C71F, 0xFFF38BAA, 0xFF6E7477,
            0xFFB4B9BA, 0xFF169C9C, 0xFF8932B8, 0xFF3C44AA,
            0xFF835432, 0xFF5E7C16, 0xFFB02E26, 0xFF1D1D21
    };

    public static final int COUNT = PALETTE.length;

    /** Colour index brought into range 0–15 (anything invalid becomes 0). */
    public static int clamp(int index) {
        return index >= 0 && index < COUNT ? index : 0;
    }

    /** ARGB colour of the given index (invalid indices give the first colour). */
    public static int argb(int index) {
        return PALETTE[clamp(index)];
    }
}
