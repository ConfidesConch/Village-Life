package io.github.confidesconch.villagelife.client.ui.component;

import io.github.confidesconch.villagelife.client.ui.core.Ui;
import io.github.confidesconch.villagelife.client.ui.core.UiRect;
import io.github.confidesconch.villagelife.client.ui.core.UiTheme;

/**
 * A scrolling list (M21b1): mouse wheel and a draggable gold scroll bar; only the rows that can be seen
 * are drawn, so 200 residents cost no more than 15. Keeps its own scroll position.
 */
public final class ScrollList {
    /** Draws one visible row. */
    @FunctionalInterface
    public interface RowRenderer {
        void row(Ui ui, int index, UiRect row);
    }

    private static final int BAR = 6;

    private int offset;
    private UiRect lastArea = new UiRect(0, 0, 0, 0);
    private int lastMax;
    private int rowHeight = 1;
    private boolean dragging;

    /** Back to the top (e.g. after a new search). */
    public void reset() {
        offset = 0;
    }

    public void render(Ui ui, UiRect area, int count, int rowHeight, RowRenderer renderer) {
        this.rowHeight = Math.max(1, rowHeight);
        lastArea = area;
        lastMax = Math.max(0, count * rowHeight - area.h());
        offset = Math.max(0, Math.min(offset, lastMax));
        boolean bar = lastMax > 0;
        UiRect rows = bar ? area.inset(0, 0, BAR + 2, 0) : area;
        ui.graphics().enableScissor(rows.x(), rows.y(), rows.right(), rows.bottom());
        ui.clip(rows); // half-visible rows only react where they can be seen
        int first = offset / rowHeight;
        int last = Math.min(count - 1, (offset + area.h()) / rowHeight);
        for (int i = first; i <= last; i++) {
            UiRect row = new UiRect(rows.x(), rows.y() + i * rowHeight - offset, rows.w(), rowHeight);
            renderer.row(ui, i, row);
        }
        ui.graphics().disableScissor();
        ui.unclip();
        if (bar) {
            UiRect track = new UiRect(area.right() - BAR, area.y(), BAR, area.h());
            UiTheme.draw(ui.graphics(), UiTheme.SCROLL_TRACK, track);
            int thumbHeight = Math.max(12, area.h() * area.h() / (lastMax + area.h()));
            int thumbY = track.y() + (track.h() - thumbHeight) * offset / lastMax;
            UiTheme.draw(ui.graphics(), UiTheme.SCROLL_THUMB, new UiRect(track.x(), thumbY, BAR, thumbHeight));
            ui.clickable(track, () -> dragging = true);
        }
    }

    public boolean mouseScrolled(double x, double y, double amount) {
        if (!lastArea.contains(x, y) || lastMax <= 0) {
            return false;
        }
        offset = Math.max(0, Math.min(lastMax, offset - (int) Math.round(amount * rowHeight * 3)));
        return true;
    }

    /** While the scroll bar is held: jump to the mouse position. */
    public boolean mouseDragged(double x, double y) {
        if (!dragging || lastMax <= 0) {
            return false;
        }
        double share = (y - lastArea.y()) / Math.max(1, lastArea.h());
        offset = (int) Math.round(Math.max(0, Math.min(1, share)) * lastMax);
        return true;
    }

    public void mouseReleased() {
        dragging = false;
    }
}
