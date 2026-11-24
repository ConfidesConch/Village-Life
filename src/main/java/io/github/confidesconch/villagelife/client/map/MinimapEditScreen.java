package io.github.confidesconch.villagelife.client.map;

import io.github.confidesconch.villagelife.client.book.VillageBookKeys;
import io.github.confidesconch.villagelife.client.ui.core.WalkWhileOpen;
import io.github.confidesconch.villagelife.core.config.ClientConfig;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * "Unlocked" minimap (1.6, owner's wish, key "Adjust minimap", default U): drag the minimap to move it,
 * drag its lower left corner to resize it, zoom with − / + or the mouse wheel, choose "north up" or
 * "turns with you", and lock it again. The game goes on behind it and the player can walk.
 */
public final class MinimapEditScreen extends Screen {
    private static final int HANDLE = 7;
    private static final int FRAME = 0xFFFFD060;

    private enum Drag { NONE, MOVE, RESIZE }

    private Drag drag = Drag.NONE;
    /** Where the mouse grabbed the minimap, relative to its top left corner. */
    private double grabX;
    private double grabY;
    private Button turnButton;
    private Button zoomLabel;

    public MinimapEditScreen() {
        super(Component.translatable("villagelife.minimap.edit.title"));
    }

    @Override
    protected void init() {
        if (!ClientConfig.SHOW_MINIMAP.get()) {
            ClientConfig.SHOW_MINIMAP.set(true); // adjusting a hidden minimap would make no sense
        }
        int x = width / 2 - 110;
        int y = height - 56;
        addRenderableWidget(Button.builder(Component.literal("−"), b -> zoom(1)).bounds(x, y, 20, 20).build());
        zoomLabel = addRenderableWidget(Button.builder(zoomText(), b -> { }).bounds(x + 22, y, 76, 20).build());
        zoomLabel.active = false;
        addRenderableWidget(Button.builder(Component.literal("+"), b -> zoom(-1)).bounds(x + 100, y, 20, 20).build());
        turnButton = addRenderableWidget(Button.builder(turnText(), b -> {
            ClientConfig.MINIMAP_ROTATE.set(!ClientConfig.MINIMAP_ROTATE.get());
            b.setMessage(turnText());
        }).bounds(x + 124, y, 96, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("villagelife.minimap.edit.reset"), b -> reset())
                .bounds(x, y + 24, 108, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("villagelife.minimap.edit.lock"), b -> onClose())
                .bounds(x + 112, y + 24, 108, 20).build());
    }

    private Component zoomText() {
        float blocks = ClientConfig.MINIMAP_ZOOMS[ClientConfig.MINIMAP_ZOOM.get()];
        return Component.translatable("villagelife.minimap.edit.zoom", blocks < 1 ? "½" : String.valueOf((int) blocks));
    }

    private Component turnText() {
        return Component.translatable(ClientConfig.MINIMAP_ROTATE.get()
                ? "villagelife.minimap.edit.turning" : "villagelife.minimap.edit.north_up");
    }

    /** {@code step} −1 = closer, +1 = farther. */
    private void zoom(int step) {
        int next = Math.max(0, Math.min(ClientConfig.MINIMAP_ZOOMS.length - 1, ClientConfig.MINIMAP_ZOOM.get() + step));
        ClientConfig.MINIMAP_ZOOM.set(next);
        zoomLabel.setMessage(zoomText());
    }

    private void reset() {
        ClientConfig.MINIMAP_SIZE.set(64);
        ClientConfig.MINIMAP_RIGHT.set(6);
        ClientConfig.MINIMAP_TOP.set(6);
        ClientConfig.MINIMAP_ZOOM.set(1);
        ClientConfig.MINIMAP_ROTATE.set(false);
        zoomLabel.setMessage(zoomText());
        turnButton.setMessage(turnText());
    }

    @Override
    public void tick() {
        super.tick();
        WalkWhileOpen.update(drag != Drag.NONE);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (VillageBookKeys.EDIT_MINIMAP.matches(event)) {
            onClose(); // the same key locks it again
            return true;
        }
        if (minecraft.options.keyJump.matches(event)) {
            return true; // space jumps; it must not press the last clicked button again
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            int[] at = MinimapLayer.bounds(width, height);
            double mx = event.x();
            double my = event.y();
            if (mx >= at[0] - HANDLE && mx < at[0] + HANDLE && my >= at[1] + at[2] - HANDLE && my < at[1] + at[2] + HANDLE) {
                drag = Drag.RESIZE;
                return true;
            }
            if (mx >= at[0] && mx < at[0] + at[2] && my >= at[1] && my < at[1] + at[2]) {
                drag = Drag.MOVE;
                grabX = mx - at[0];
                grabY = my - at[1];
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        int[] at = MinimapLayer.bounds(width, height);
        if (drag == Drag.MOVE) {
            int left = (int) Math.round(event.x() - grabX);
            int top = (int) Math.round(event.y() - grabY);
            ClientConfig.MINIMAP_RIGHT.set(Math.max(0, width - left - at[2]));
            ClientConfig.MINIMAP_TOP.set(Math.max(0, top));
            return true;
        }
        if (drag == Drag.RESIZE) { // the upper right corner stays where it is
            int right = at[0] + at[2];
            int size = (int) Math.round(Math.max(right - event.x(), event.y() - at[1]));
            size = Math.max(ClientConfig.MIN_MINIMAP_SIZE, Math.min(ClientConfig.MAX_MINIMAP_SIZE, size));
            ClientConfig.MINIMAP_SIZE.set(size);
            ClientConfig.MINIMAP_RIGHT.set(Math.max(0, width - right));
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        drag = Drag.NONE;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        int[] at = MinimapLayer.bounds(width, height);
        if (x >= at[0] && x < at[0] + at[2] && y >= at[1] && y < at[1] + at[2] && scrollY != 0) {
            zoom(scrollY > 0 ? -1 : 1);
            return true;
        }
        return super.mouseScrolled(x, y, scrollX, scrollY);
    }

    /** No dark or blurred background: the game and the minimap stay visible. */
    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        int[] at = MinimapLayer.bounds(width, height);
        int x = at[0];
        int y = at[1];
        int size = at[2];
        graphics.outline(x - 4, y - 4, size + 8, size + 8, FRAME); // unlocked: a golden frame
        graphics.fill(x - HANDLE / 2 - 1, y + size - HANDLE / 2 - 1, x + HANDLE / 2 + 2, y + size + HANDLE / 2 + 2, 0xFF202020);
        graphics.fill(x - HANDLE / 2, y + size - HANDLE / 2, x + HANDLE / 2 + 1, y + size + HANDLE / 2 + 1, FRAME);
        Component title = getTitle();
        graphics.text(font, title, (width - font.width(title)) / 2, height - 84, 0xFFFFFFFF, true);
        Component hint = Component.translatable("villagelife.minimap.edit.hint");
        graphics.text(font, hint, (width - font.width(hint)) / 2, height - 72, 0xFFCCCCCC, true);
        super.extractRenderState(graphics, mouseX, mouseY, a);
    }

    @Override
    public void onClose() {
        ClientConfig.SHOW_MINIMAP.save(); // all minimap values live in the same file
        WalkWhileOpen.release();
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
