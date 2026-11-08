package io.github.confidesconch.villagelife.client.ui.core;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import io.github.confidesconch.villagelife.client.ui.core.UiTheme.ButtonState;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;

/**
 * Drawing and clicking in one place (M21b1): pages and components draw through this object every
 * frame and register what can be clicked at the same time ("immediate mode") – so what you see is
 * always exactly what reacts to the mouse, and no widget objects have to be kept in sync with the data.
 */
public final class Ui {
    /** Something clickable drawn this frame. */
    public record Clickable(UiRect rect, Runnable left, Runnable right) {}

    private static final int TOOLTIP_WIDTH = 220;

    private final GuiGraphicsExtractor graphics;
    private final Font font;
    private final int mouseX;
    private final int mouseY;
    private final List<Clickable> clickables;
    private final Consumer<String> navigator;
    /** While set, only this part of the screen can be clicked or hovered (scrolling content, M21b5). */
    private UiRect clip;

    public Ui(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY, List<Clickable> clickables) {
        this(graphics, font, mouseX, mouseY, clickables, id -> {});
    }

    public Ui(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY, List<Clickable> clickables,
              Consumer<String> navigator) {
        this.graphics = graphics;
        this.font = font;
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        this.clickables = clickables;
        this.navigator = navigator;
    }

    /**
     * Opens another area of the window by its id (M21b3), e.g. from a problem in the overview; "id:hint"
     * also tells the area what to show (M21b4, see {@link UiPage#focus}).
     */
    public void navigate(String pageId) {
        navigator.accept(pageId);
    }

    public GuiGraphicsExtractor graphics() {
        return graphics;
    }

    public Font font() {
        return font;
    }

    public int mouseX() {
        return mouseX;
    }

    public int mouseY() {
        return mouseY;
    }

    public boolean hovered(UiRect r) {
        return r.contains(mouseX, mouseY) && (clip == null || clip.contains(mouseX, mouseY));
    }

    /** From now on, clicks and hovers only count inside {@code r} (content scrolled out of sight stays dead). */
    public void clip(UiRect r) {
        clip = r;
    }

    public void unclip() {
        clip = null;
    }

    // ---- text ----------------------------------------------------------------------------------

    /** Text at (x, y), cut with "…" to {@code maxWidth}. Returns the width drawn. */
    public int text(Component text, int x, int y, int color, int maxWidth) {
        String fitted = UiTheme.fit(font, text, maxWidth);
        graphics.text(font, fitted, x, y, color, false);
        if (!fitted.equals(text.getString()) && hovered(new UiRect(x, y - 1, font.width(fitted), font.lineHeight + 1))) {
            graphics.setTooltipForNextFrame(text, mouseX, mouseY); // the whole text when it was cut
        }
        return font.width(fitted);
    }

    /** Text centred vertically in {@code r}; {@code align}: -1 left, 0 centre, 1 right. */
    public void text(Component text, UiRect r, int color, int align) {
        String fitted = UiTheme.fit(font, text, r.w());
        int width = font.width(fitted);
        int x = align < 0 ? r.x() : align > 0 ? r.right() - width : r.x() + (r.w() - width) / 2;
        int y = r.y() + (r.h() - font.lineHeight) / 2 + 1;
        graphics.text(font, fitted, x, y, color, false);
        if (!fitted.equals(text.getString()) && hovered(r)) {
            graphics.setTooltipForNextFrame(text, mouseX, mouseY);
        }
    }

    /** Text broken into lines of {@code width}; returns the height used. */
    public int paragraph(Component text, int x, int y, int width, int color) {
        var lines = font.split(text, width);
        for (int i = 0; i < lines.size(); i++) {
            graphics.text(font, lines.get(i), x, y + i * (font.lineHeight + 1), color, false);
        }
        return lines.size() * (font.lineHeight + 1);
    }

    // ---- components ----------------------------------------------------------------------------

    /** A button; returns true if the mouse is over it. A disabled button draws, but does nothing. */
    public boolean button(UiRect r, Component label, boolean enabled, Runnable action) {
        boolean hover = enabled && hovered(r);
        UiTheme.button(graphics, r, !enabled ? ButtonState.DISABLED : hover ? ButtonState.HOVER : ButtonState.NORMAL);
        text(label, r.inset(4, 0, 4, 0), enabled ? (hover ? UiTheme.GOLD : UiTheme.TEXT) : UiTheme.TEXT_DIM, 0);
        if (enabled) {
            clickable(r, action);
        }
        return hover;
    }

    /** A small value badge: a label and its value. */
    public void chip(UiRect r, Component label, Component value, int valueColor) {
        UiTheme.draw(graphics, UiTheme.CHIP, r);
        UiRect inner = r.inset(6, 0, 6, 0);
        int labelWidth = Math.max(0, Math.min(font.width(label), inner.w() - font.width(value) - 4)); // the value never gets cut
        text(label, inner.left(labelWidth), UiTheme.TEXT_DIM, -1);
        text(value, inner.rightOf(labelWidth + 4), valueColor, 1);
    }

    public void item(ItemStack stack, int x, int y) {
        graphics.item(stack, x, y);
    }

    /** An item in an 18 × 18 slot at (x, y) with count and its normal tooltip (M21b6). */
    public void slot(ItemStack stack, int x, int y) {
        slot(stack, x, y, null);
    }

    /** Like {@link #slot(ItemStack, int, int)}, but hovering shows {@code tooltip} (if not null) instead. */
    public void slot(ItemStack stack, int x, int y, Component tooltip) {
        UiRect r = new UiRect(x, y, 18, 18);
        UiTheme.draw(graphics, UiTheme.SLOT, r);
        graphics.item(stack, x + 1, y + 1);
        graphics.itemDecorations(font, stack, x + 1, y + 1);
        if (hovered(r)) {
            if (tooltip != null) {
                tooltip(r, tooltip);
            } else if (!stack.isEmpty()) {
                graphics.setTooltipForNextFrame(font, stack, mouseX, mouseY);
            }
        }
    }

    /** Tooltip while the mouse is over {@code r}: each line wrapped to a readable width (M21b3). */
    public void tooltip(UiRect r, Component... lines) {
        if (hovered(r)) {
            List<net.minecraft.util.FormattedCharSequence> wrapped = new ArrayList<>();
            for (Component line : lines) {
                wrapped.addAll(font.split(line, TOOLTIP_WIDTH));
            }
            graphics.setTooltipForNextFrame(wrapped, mouseX, mouseY);
        }
    }

    // ---- clicks --------------------------------------------------------------------------------

    public void clickable(UiRect r, Runnable action) {
        clickable(r, action, null);
    }

    public void clickable(UiRect r, Runnable left, Runnable right) {
        if (clip != null) {
            int x0 = Math.max(r.x(), clip.x());
            int y0 = Math.max(r.y(), clip.y());
            int x1 = Math.min(r.right(), clip.right());
            int y1 = Math.min(r.bottom(), clip.bottom());
            if (x1 <= x0 || y1 <= y0) {
                return;
            }
            r = new UiRect(x0, y0, x1 - x0, y1 - y0);
        }
        clickables.add(new Clickable(r, left, right));
    }

    /** Runs what was clicked (the last drawn wins, it lies on top). */
    public static boolean click(List<Clickable> clickables, double x, double y, boolean right) {
        for (Clickable c : new ArrayList<>(clickables).reversed()) {
            Runnable action = right ? c.right() : c.left();
            if (action != null && c.rect().contains(x, y)) {
                action.run();
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                return true;
            }
        }
        return false;
    }
}
