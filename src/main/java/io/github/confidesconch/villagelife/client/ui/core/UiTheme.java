package io.github.confidesconch.villagelife.client.ui.core;

import io.github.confidesconch.villagelife.VillageLife;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * The look of the village window (M21b1, owner's choice "1a"): dark wood, parchment and gold, drawn
 * from the sprites in {@code textures/gui/sprites/ui/} (nine-slice, so they stretch to any size; each
 * PNG can be replaced by a better one with the same size and name). Colours with a meaning: gold =
 * selected / important, green = good, amber = warning, red = problem.
 */
public final class UiTheme {
    private UiTheme() {}

    /** Light text on dark wood. */
    public static final int TEXT = 0xFFF1E4C6;
    public static final int TEXT_DIM = 0xFFB9A887;
    /** Dark text on parchment. */
    public static final int INK = 0xFF3A2A18;
    public static final int INK_DIM = 0xFF7A664A;
    public static final int GOLD = 0xFFE7C66B;
    public static final int GOOD = 0xFF7FCF6A;
    public static final int WARN = 0xFFE9A93C;
    public static final int BAD = 0xFFE0614F;
    public static final int GOOD_INK = 0xFF2F7A2A;
    public static final int BAD_INK = 0xFFA8321F;

    public static final Identifier WINDOW = sprite("window");
    public static final Identifier HEADER = sprite("header");
    public static final Identifier PANEL = sprite("panel");
    public static final Identifier PANEL_DARK = sprite("panel_dark");
    public static final Identifier NAV_HOVER = sprite("nav_hover");
    public static final Identifier NAV_SELECTED = sprite("nav_selected");
    public static final Identifier CHIP = sprite("chip");
    public static final Identifier BAR_BACKGROUND = sprite("bar_background");
    public static final Identifier BAR_FILL = sprite("bar_fill");
    public static final Identifier SCROLL_TRACK = sprite("scroll_track");
    public static final Identifier SCROLL_THUMB = sprite("scroll_thumb");
    public static final Identifier SLOT = sprite("slot");
    public static final Identifier DIVIDER = sprite("divider");

    /** Button looks. */
    public enum ButtonState {
        NORMAL("button_normal"), HOVER("button_hover"), PRESSED("button_pressed"), DISABLED("button_disabled");

        final Identifier sprite;

        ButtonState(String name) {
            sprite = UiTheme.sprite(name);
        }
    }

    private static Identifier sprite(String name) {
        return Identifier.fromNamespaceAndPath(VillageLife.MOD_ID, "ui/" + name);
    }

    public static void draw(GuiGraphicsExtractor g, Identifier sprite, UiRect r) {
        if (r.w() > 0 && r.h() > 0) {
            g.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, r.x(), r.y(), r.w(), r.h());
        }
    }

    public static void button(GuiGraphicsExtractor g, UiRect r, ButtonState state) {
        draw(g, state.sprite, r);
    }

    /** A bar filled to {@code fraction} (0–1) in {@code color}. */
    public static void bar(GuiGraphicsExtractor g, UiRect r, float fraction, int color) {
        draw(g, BAR_BACKGROUND, r);
        int filled = Math.round(r.w() * Math.max(0F, Math.min(1F, fraction)));
        if (filled > 0) {
            g.blitSprite(RenderPipelines.GUI_TEXTURED, BAR_FILL, r.x(), r.y(), filled, r.h(), color);
        }
    }

    /** {@code text} cut to {@code maxWidth} pixels, with "…" if it was too long (no more overlapping texts). */
    public static String fit(Font font, String text, int maxWidth) {
        if (font.width(text) <= maxWidth) {
            return text;
        }
        String dots = "…";
        int end = text.length();
        while (end > 0 && font.width(text.substring(0, end)) + font.width(dots) > maxWidth) {
            end--;
        }
        return text.substring(0, end) + dots;
    }

    public static String fit(Font font, Component text, int maxWidth) {
        return fit(font, text.getString(), maxWidth);
    }
}
