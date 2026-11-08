package io.github.confidesconch.villagelife.client.book;

import io.github.confidesconch.villagelife.book.UpdateVillageSettingsPayload;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** Small text input for the village name (M11c); the server checks and stores it, then reopens the book. */
public final class RenameVillageScreen extends Screen {
    private final Screen book;
    private final String villageId;
    private final String currentName;
    private EditBox nameBox;

    public RenameVillageScreen(Screen book, String villageId, String currentName) {
        super(Component.translatable("villagelife.book.settings.rename"));
        this.book = book;
        this.villageId = villageId;
        this.currentName = currentName;
    }

    @Override
    protected void init() {
        int x = width / 2 - 100;
        int y = height / 2 - 20;
        nameBox = addRenderableWidget(new EditBox(font, x, y, 200, 20, Component.translatable("villagelife.book.settings.name")));
        nameBox.setMaxLength(32);
        nameBox.setValue(currentName);
        nameBox.setHint(Component.translatable("villagelife.book.settings.name_hint"));
        setInitialFocus(nameBox);
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> save()).bounds(x, y + 28, 98, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> minecraft.gui.setScreen(book))
                .bounds(x + 102, y + 28, 98, 20).build());
    }

    private void save() {
        ClientPacketDistributor.sendToServer(new UpdateVillageSettingsPayload(villageId, "name", nameBox.getValue()));
        minecraft.gui.setScreen(book); // the server's answer refreshes the book
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractRenderState(graphics, mouseX, mouseY, a);
        Component title = getTitle();
        graphics.text(font, title, (width - font.width(title)) / 2, height / 2 - 40, 0xFFFFFFFF, true);
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
