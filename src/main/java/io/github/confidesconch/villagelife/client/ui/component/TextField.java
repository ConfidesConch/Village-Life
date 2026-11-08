package io.github.confidesconch.villagelife.client.ui.component;

import com.mojang.blaze3d.platform.InputConstants;

import io.github.confidesconch.villagelife.client.ui.core.Ui;
import io.github.confidesconch.villagelife.client.ui.core.UiRect;
import io.github.confidesconch.villagelife.client.ui.core.UiTheme;

import net.minecraft.client.Minecraft;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

/**
 * A one-line text field for the village window (M21b4), e.g. the search: click to type, Enter or Esc
 * (or a click elsewhere) to stop. While it has the focus it takes every key, so typing "e" or "b" does
 * not close the window.
 */
public final class TextField {
    private final int maxLength;
    private String value = "";
    private boolean focused;

    public TextField(int maxLength) {
        this.maxLength = maxLength;
    }

    public String value() {
        return value;
    }

    public void setValue(String value) {
        this.value = value.length() > maxLength ? value.substring(0, maxLength) : value;
    }

    public boolean focused() {
        return focused;
    }

    public void blur() {
        setFocused(false);
    }

    /**
     * Since 26.x the game only sends typed characters while text input is switched on (SDL), so the
     * field switches it on when it gets the focus and off again when it loses it (like vanilla's EditBox).
     */
    private void setFocused(boolean focus) {
        if (focus == focused) {
            return;
        }
        focused = focus;
        var input = Minecraft.getInstance().textInputManager();
        if (focus) {
            input.startTextInput(this);
        } else {
            input.stopTextInput(this);
        }
    }

    /** Draws the field; {@code placeholder} shows while it is empty and not focused. */
    public void render(Ui ui, UiRect r, Component placeholder) {
        UiTheme.draw(ui.graphics(), UiTheme.PANEL_DARK, r);
        if (focused) {
            ui.graphics().fill(r.x(), r.bottom() - 1, r.right(), r.bottom(), UiTheme.GOLD);
        }
        UiRect inner = r.inset(5, 0, 5, 0);
        if (value.isEmpty() && !focused) {
            ui.text(placeholder, inner, UiTheme.TEXT_DIM, -1);
        } else {
            String shown = value;
            while (!shown.isEmpty() && ui.font().width(shown) > inner.w() - 4) {
                shown = shown.substring(1); // the end stays visible while typing
            }
            int y = inner.y() + (inner.h() - ui.font().lineHeight) / 2 + 1;
            ui.graphics().text(ui.font(), shown, inner.x(), y, UiTheme.TEXT, false);
            if (focused && System.currentTimeMillis() / 500 % 2 == 0) {
                int x = inner.x() + ui.font().width(shown) + 1;
                ui.graphics().fill(x, y - 1, x + 1, y + ui.font().lineHeight - 1, UiTheme.GOLD);
            }
        }
        ui.clickable(r, () -> setFocused(true));
    }

    /** True if the key was used (always while focused). */
    public boolean keyPressed(KeyEvent event) {
        if (!focused) {
            return false;
        }
        if (event.key() == InputConstants.KEY_BACKSPACE && !value.isEmpty()) {
            value = value.substring(0, value.offsetByCodePoints(value.length(), -1));
        } else if (event.key() == InputConstants.KEY_ESCAPE || event.key() == InputConstants.KEY_RETURN) {
            setFocused(false);
        }
        return true;
    }

    public boolean charTyped(CharacterEvent event) {
        if (!focused || !event.isAllowedChatCharacter()) {
            return false;
        }
        if (value.length() < maxLength) {
            value += event.codepointAsString();
        }
        return true;
    }
}
