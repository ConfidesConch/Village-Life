package io.github.confidesconch.villagelife.client.ui.component;

import io.github.confidesconch.villagelife.client.ui.core.Ui;
import io.github.confidesconch.villagelife.client.ui.core.UiRect;
import io.github.confidesconch.villagelife.client.ui.core.UiTheme;

/**
 * Free content that scrolls (M21b4), e.g. a resident's details: {@link #begin} gives the top y to draw
 * at, {@link #end} takes the height that was used and draws the scroll bar. Content outside the area is
 * cut off.
 */
public final class ScrollPanel {
    private static final int BAR = 6;

    private int offset;
    private int max;
    private UiRect area = new UiRect(0, 0, 0, 0);
    private boolean dragging;

    public void reset() {
        offset = 0;
    }

    /** Width left for the content (room for the bar is always kept). */
    public static int contentWidth(UiRect area) {
        return Math.max(0, area.w() - BAR - 4);
    }

    /** Starts drawing into {@code area}; returns the y of the content's top. */
    public int begin(Ui ui, UiRect area) {
        this.area = area;
        ui.graphics().enableScissor(area.x(), area.y(), area.right(), area.bottom());
        ui.clip(area);
        return area.y() - offset;
    }

    /** Ends drawing; {@code height}: how tall the content was. */
    public void end(Ui ui, int height) {
        ui.graphics().disableScissor();
        ui.unclip();
        max = Math.max(0, height - area.h());
        offset = Math.max(0, Math.min(offset, max));
        if (max > 0) {
            UiRect track = new UiRect(area.right() - BAR, area.y(), BAR, area.h());
            UiTheme.draw(ui.graphics(), UiTheme.SCROLL_TRACK, track);
            int thumbHeight = Math.max(12, area.h() * area.h() / (max + area.h()));
            int thumbY = track.y() + (track.h() - thumbHeight) * offset / max;
            UiTheme.draw(ui.graphics(), UiTheme.SCROLL_THUMB, new UiRect(track.x(), thumbY, BAR, thumbHeight));
            ui.clickable(track, () -> dragging = true);
        }
    }

    public boolean mouseScrolled(double x, double y, double amount) {
        if (!area.contains(x, y) || max <= 0) {
            return false;
        }
        offset = Math.max(0, Math.min(max, offset - (int) Math.round(amount * 24)));
        return true;
    }

    public boolean mouseDragged(double x, double y) {
        if (!dragging || max <= 0) {
            return false;
        }
        double share = (y - area.y()) / Math.max(1, area.h());
        offset = (int) Math.round(Math.max(0, Math.min(1, share)) * max);
        return true;
    }

    public void mouseReleased() {
        dragging = false;
    }
}
