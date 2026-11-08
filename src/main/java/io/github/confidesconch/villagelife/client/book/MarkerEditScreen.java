package io.github.confidesconch.villagelife.client.book;

import com.mojang.blaze3d.platform.InputConstants;

import io.github.confidesconch.villagelife.core.util.MarkerColors;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * Small dialog for a waypoint or zone (M11e): name, one of 16 colours, optionally "share with friends"
 * (waypoints, M11g) and "Delete".
 * The caller sends the result to the server; the dialog then returns to the book.
 */
public final class MarkerEditScreen extends Screen {
    /** What the dialog hands back on "Done". */
    @FunctionalInterface
    public interface Result {
        void done(String name, int color, boolean shared);
    }

    private static final int SWATCH = 12;
    private static final int SWATCH_STEP = 14;

    private final Screen book;
    private final Component subtitle;
    private final String initialName;
    private final Result onDone;
    /** Null: no share switch (zones). */
    private final Boolean showShare;
    private boolean shared;
    private final Runnable onDelete;
    private int color;
    private EditBox nameBox;

    /** {@code onDelete} may be null (new marker: no delete button); {@code shared} null hides the share switch. */
    public MarkerEditScreen(Screen book, Component title, Component subtitle, String name, int color, Boolean shared,
                     Result onDone, Runnable onDelete) {
        super(title);
        this.book = book;
        this.subtitle = subtitle;
        this.initialName = name;
        this.color = MarkerColors.clamp(color);
        this.onDone = onDone;
        this.showShare = shared;
        this.shared = shared != null && shared;
        this.onDelete = onDelete;
    }

    private int swatchesX() {
        return width / 2 - MarkerColors.COUNT * SWATCH_STEP / 2;
    }

    private int swatchesY() {
        return height / 2 + 2;
    }

    @Override
    protected void init() {
        int x = width / 2 - 100;
        int y = height / 2 - 26;
        nameBox = addRenderableWidget(new EditBox(font, x, y, 200, 20, Component.translatable("villagelife.marker.name")));
        nameBox.setMaxLength(24);
        nameBox.setValue(initialName);
        nameBox.setHint(Component.translatable("villagelife.marker.name_hint"));
        setInitialFocus(nameBox);
        int buttonsY = swatchesY() + SWATCH + 10;
        if (showShare != null) {
            addRenderableWidget(Button.builder(shareLabel(), button -> {
                shared = !shared;
                button.setMessage(shareLabel());
            }).bounds(x, buttonsY, 200, 20).build());
            buttonsY += 24;
        }
        int buttonWidth = onDelete == null ? 98 : 64;
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> done())
                .bounds(x, buttonsY, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> minecraft.gui.setScreen(book))
                .bounds(x + buttonWidth + 4, buttonsY, buttonWidth, 20).build());
        if (onDelete != null) {
            addRenderableWidget(Button.builder(Component.translatable("villagelife.marker.delete"), button -> {
                onDelete.run();
                minecraft.gui.setScreen(book);
            }).bounds(x + 2 * (buttonWidth + 4), buttonsY, 200 - 2 * (buttonWidth + 4), 20).build());
        }
    }

    private Component shareLabel() {
        return Component.translatable(shared ? "villagelife.marker.shared_on" : "villagelife.marker.shared_off");
    }

    private void done() {
        onDone.done(nameBox.getValue(), color, shared);
        minecraft.gui.setScreen(book); // the server's answer refreshes map and book
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            int index = swatchAt((int) event.x(), (int) event.y());
            if (index >= 0) {
                color = index;
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    private int swatchAt(int mouseX, int mouseY) {
        if (mouseY < swatchesY() || mouseY >= swatchesY() + SWATCH || mouseX < swatchesX()) {
            return -1;
        }
        int index = (mouseX - swatchesX()) / SWATCH_STEP;
        boolean onSwatch = (mouseX - swatchesX()) % SWATCH_STEP < SWATCH;
        return index < MarkerColors.COUNT && onSwatch ? index : -1;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractRenderState(graphics, mouseX, mouseY, a);
        Component title = getTitle();
        graphics.text(font, title, (width - font.width(title)) / 2, height / 2 - 54, 0xFFFFFFFF, true);
        graphics.text(font, subtitle, (width - font.width(subtitle)) / 2, height / 2 - 42, 0xFFAAAAAA, true);
        for (int i = 0; i < MarkerColors.COUNT; i++) {
            int sx = swatchesX() + i * SWATCH_STEP;
            int sy = swatchesY();
            int frame = i == color ? 0xFFFFFFFF : (swatchAt(mouseX, mouseY) == i ? 0xFFAAAAAA : 0xFF000000);
            graphics.fill(sx - 1, sy - 1, sx + SWATCH + 1, sy + SWATCH + 1, frame);
            graphics.fill(sx, sy, sx + SWATCH, sy + SWATCH, MarkerColors.argb(i));
        }
    }

    /** Escape goes back to the book (still open as a container), never straight to the game. */
    @Override
    public void onClose() {
        minecraft.gui.setScreen(book);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
