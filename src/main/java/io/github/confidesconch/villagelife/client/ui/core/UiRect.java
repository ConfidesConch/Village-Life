package io.github.confidesconch.villagelife.client.ui.core;

/** A rectangle in GUI coordinates (M21b1): the building block of every layout in the village window. */
public record UiRect(int x, int y, int w, int h) {

    public int right() {
        return x + w;
    }

    public int bottom() {
        return y + h;
    }

    public boolean contains(double px, double py) {
        return px >= x && px < x + w && py >= y && py < y + h;
    }

    /** Smaller by {@code d} on every side. */
    public UiRect inset(int d) {
        return inset(d, d, d, d);
    }

    public UiRect inset(int left, int top, int right, int bottom) {
        return new UiRect(x + left, y + top, Math.max(0, w - left - right), Math.max(0, h - top - bottom));
    }

    /** The top {@code height} pixels. */
    public UiRect top(int height) {
        return new UiRect(x, y, w, Math.min(h, height));
    }

    /** Everything below the top {@code height} pixels. */
    public UiRect below(int height) {
        return new UiRect(x, y + Math.min(h, height), w, Math.max(0, h - height));
    }

    /** The left {@code width} pixels. */
    public UiRect left(int width) {
        return new UiRect(x, y, Math.min(w, width), h);
    }

    /** Everything right of the left {@code width} pixels. */
    public UiRect rightOf(int width) {
        return new UiRect(x + Math.min(w, width), y, Math.max(0, w - width), h);
    }

    /** The bottom {@code height} pixels. */
    public UiRect bottomPart(int height) {
        return new UiRect(x, y + Math.max(0, h - height), w, Math.min(h, height));
    }
}
