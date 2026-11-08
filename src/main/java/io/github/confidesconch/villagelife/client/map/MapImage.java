package io.github.confidesconch.villagelife.client.map;

import io.github.confidesconch.villagelife.VillageLife;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.material.MapColor;

/**
 * A top-down map picture drawn from the chunks the client has loaded, using Minecraft's map colours
 * (like a vanilla map item, with height shading). It is repainted a few rows at a time, so a full
 * repaint is spread over several frames and never causes a stutter. Unloaded areas get
 * {@code unknownColor}. With {@code parchment} (village window, M21b8) the picture is drawn like an ink
 * map on parchment: softened colours, gentle shading, contour lines and dark shores; {@link #panTo}
 * then moves the picture smoothly instead of repainting it.
 */
public final class MapImage {
    private final int size;
    private final int unknownColor;
    private final DynamicTexture texture;
    private final boolean parchment;
    private boolean placed;
    private final Identifier id;
    private int nextRow;

    private double centerX;
    private double centerZ;
    private float scale = 1;

    public MapImage(String name, int size, int unknownColor) {
        this(name, size, unknownColor, false);
    }

    public MapImage(String name, int size, int unknownColor, boolean parchment) {
        this.size = size;
        this.parchment = parchment;
        this.unknownColor = unknownColor;
        this.texture = new DynamicTexture(() -> "villagelife map " + name, size, size, true);
        this.id = Identifier.fromNamespaceAndPath(VillageLife.MOD_ID, "map/" + name);
        Minecraft.getInstance().getTextureManager().register(id, texture);
        texture.getPixels().fillRect(0, 0, size, size, 0);
        texture.upload();
    }

    public Identifier textureId() {
        return id;
    }

    public int size() {
        return size;
    }

    /** Sets what the picture shows: centre in world coordinates and blocks per pixel. */
    public void view(double centerX, double centerZ, float blocksPerPixel) {
        boolean jump = Math.abs(centerX - this.centerX) > size * scale / 4 || Math.abs(centerZ - this.centerZ) > size * scale / 4
                || blocksPerPixel != scale;
        this.centerX = centerX;
        this.centerZ = centerZ;
        this.scale = blocksPerPixel;
        if (jump) {
            nextRow = 0; // big change: start the repaint from the top
        }
    }

    /** World X of a pixel column / Z of a pixel row. */
    public int worldX(int pixel) {
        return (int) Math.floor(centerX + (pixel - size / 2.0) * scale);
    }

    public int worldZ(int pixel) {
        return (int) Math.floor(centerZ + (pixel - size / 2.0) * scale);
    }

    /** Pixel position of a world coordinate (may be outside 0..size). */
    public float pixelX(double worldX) {
        return (float) ((worldX - centerX) / scale + size / 2.0);
    }

    public float pixelZ(double worldZ) {
        return (float) ((worldZ - centerZ) / scale + size / 2.0);
    }

    /** Repaints the next {@code rows} rows and uploads the picture. */
    public void paintRows(ClientLevel level, int rows) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int i = 0; i < rows; i++) {
            int row = nextRow;
            nextRow = (nextRow + 1) % size;
            for (int column = 0; column < size; column++) {
                paintPixel(level, pos, column, row);
            }
        }
        texture.upload();
    }

    /**
     * Smooth view change (M21b8): a move of a few pixels shifts the picture and paints only the strip
     * that came into view, so dragging the map is fluid. A new zoom or a big jump repaints row by row.
     * The centre snaps to whole pixels.
     */
    public void panTo(ClientLevel level, double centerX, double centerZ, float blocksPerPixel) {
        int dx = (int) Math.round((centerX - this.centerX) / scale);
        int dz = (int) Math.round((centerZ - this.centerZ) / scale);
        if (!placed || blocksPerPixel != scale || Math.abs(dx) >= size / 2 || Math.abs(dz) >= size / 2) {
            placed = true;
            this.centerX = centerX;
            this.centerZ = centerZ;
            this.scale = blocksPerPixel;
            nextRow = 0;
            return;
        }
        if (dx == 0 && dz == 0) {
            return;
        }
        var pixels = texture.getPixels();
        int[] copy = new int[size * size];
        for (int row = 0; row < size; row++) {
            for (int column = 0; column < size; column++) {
                copy[row * size + column] = pixels.getPixel(column, row);
            }
        }
        this.centerX += dx * scale;
        this.centerZ += dz * scale;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int row = 0; row < size; row++) {
            for (int column = 0; column < size; column++) {
                int fromColumn = column + dx;
                int fromRow = row + dz;
                if (fromColumn >= 0 && fromColumn < size && fromRow >= 0 && fromRow < size) {
                    pixels.setPixel(column, row, copy[fromRow * size + fromColumn]);
                } else {
                    paintPixel(level, pos, column, row); // came into view
                }
            }
        }
        texture.upload();
    }

    private void paintPixel(ClientLevel level, BlockPos.MutableBlockPos pos, int column, int row) {
        int x = worldX(column);
        int z = worldZ(row);
        int step = Math.max(1, (int) scale);
        int color = parchment ? MapInk.color(level, pos, x, z, step, unknownColor) : color(level, pos, x, z, z - step);
        texture.getPixels().setPixel(column, row, color);
    }

    private int color(ClientLevel level, BlockPos.MutableBlockPos pos, int x, int z, int northZ) {
        if (!MapSurface.known(level, x, z)) { // 1.0.1: remembered areas too
            return unknownColor;
        }
        int height = MapSurface.height(level, x, z);
        MapColor mapColor = MapSurface.color(level, pos, x, height, z);
        if (mapColor == MapColor.NONE) {
            return unknownColor;
        }
        MapColor.Brightness brightness = MapColor.Brightness.NORMAL;
        if (MapSurface.known(level, x, northZ)) {
            int northHeight = MapSurface.height(level, x, northZ);
            if (height > northHeight) {
                brightness = MapColor.Brightness.HIGH;
            } else if (height < northHeight) {
                brightness = MapColor.Brightness.LOW;
            }
        }
        return 0xFF000000 | mapColor.calculateARGBColor(brightness);
    }
}
