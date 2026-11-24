package io.github.confidesconch.villagelife.client.map;

import io.github.confidesconch.villagelife.core.config.ClientConfig;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.client.gui.GuiLayer;

/**
 * The minimap (M11d): blocks around the player with claims (M11f), waypoints (M11e), other players
 * (M11g), residents and the player. Shown when {@code showMinimap} is on (minimap key, default N).
 *
 * <p>1.6 (owner's wish): it can be moved, resized and zoomed ({@link MinimapEditScreen}, key default U),
 * either keeps north up or turns with the player's view, and the player is an arrow pointing where he
 * looks.
 */
public final class MinimapLayer implements GuiLayer {
    /** Resolution of the map picture; it is stretched to the minimap's size. */
    private static final int PIXELS = 128;
    private static final int ROWS_PER_FRAME = 8;
    /** Room under the minimap for the coordinates. */
    static final int LABEL_HEIGHT = 12;
    /** A turned square needs a picture √2 as wide to fill the corners. */
    private static final float TURN_MARGIN = 1.42F;

    /** Parchment look like the map in the village window (1.6, owner's wish). */
    private static final int UNKNOWN = 0xFFD9C9A3;
    private static final int INK = 0xFF5A4630;

    private MapImage map;

    /** Where the minimap is on a screen of this size: {x, y, size}, kept on the screen. */
    public static int[] bounds(int guiWidth, int guiHeight) {
        int size = Math.max(ClientConfig.MIN_MINIMAP_SIZE, Math.min(ClientConfig.MAX_MINIMAP_SIZE,
                Math.min(ClientConfig.MINIMAP_SIZE.get(), Math.min(guiWidth, guiHeight - LABEL_HEIGHT))));
        int x = guiWidth - size - ClientConfig.MINIMAP_RIGHT.get();
        int y = ClientConfig.MINIMAP_TOP.get();
        x = Math.max(0, Math.min(guiWidth - size, x));
        y = Math.max(0, Math.min(guiHeight - size - LABEL_HEIGHT, y));
        return new int[] {x, y, size};
    }

    @Override
    public void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (!ClientConfig.SHOW_MINIMAP.get() || player == null || minecraft.level == null) {
            return;
        }
        if (map == null) {
            map = new MapImage("minimap", PIXELS, UNKNOWN, true); // 1.6: same parchment style as the window's map
        }
        int zoom = Math.max(0, Math.min(ClientConfig.MINIMAP_ZOOMS.length - 1, ClientConfig.MINIMAP_ZOOM.get()));
        boolean turn = ClientConfig.MINIMAP_ROTATE.get();
        // turned, the picture is drawn √2 larger, so it shows √2 more ground: the scale stays the same
        // 1.6: follows the player every frame (shift + paint only the new strip), like the big map;
        // the rows are still repainted slowly in the background so changed blocks show up
        map.panTo(minecraft.level, player.getX(), player.getZ(), ClientConfig.MINIMAP_ZOOMS[zoom] * (turn ? TURN_MARGIN : 1F));
        map.paintRows(minecraft.level, ROWS_PER_FRAME);

        int[] at = bounds(graphics.guiWidth(), graphics.guiHeight());
        int x = at[0];
        int y = at[1];
        int size = at[2];
        float turnAngle = turn ? -MapOverlay.headingOf(player.getYRot()) : 0F;

        graphics.fill(x - 2, y - 2, x + size + 2, y + size + 2, 0xFFB89F72); // parchment edge
        graphics.outline(x - 1, y - 1, size + 2, size + 2, INK);
        graphics.fill(x, y, x + size, y + size, UNKNOWN); // under the thin edge the glide can uncover
        graphics.enableScissor(x, y, x + size, y + size);
        float centerX = x + size / 2F;
        float centerY = y + size / 2F;
        int drawn = turn ? (int) Math.ceil(size * TURN_MARGIN) : size; // picture size on screen
        int dx = Math.round(centerX - drawn / 2F);
        int dy = Math.round(centerY - drawn / 2F);
        var pose = graphics.pose();
        pose.pushMatrix();
        if (turn) {
            pose.translate(centerX, centerY);
            pose.rotate(turnAngle);
            pose.translate(-centerX, -centerY);
        }
        // the picture moves in whole map pixels; shift it by the rest so it glides smoothly
        float pixel = (float) drawn / PIXELS;
        pose.translate((float) ((map.centerX() - player.getX()) / map.scale()) * pixel,
                (float) ((map.centerZ() - player.getZ()) / map.scale()) * pixel);
        graphics.blit(RenderPipelines.GUI_TEXTURED, map.textureId(), dx, dy, 0, 0, drawn, drawn, PIXELS, PIXELS, PIXELS, PIXELS);
        MapOverlay.claims(graphics, map, dx, dy, drawn, false);
        MapOverlay.waypoints(graphics, map, dx, dy, drawn, ClientWaypoints.shownHere(), true);
        MapOverlay.otherPlayers(graphics, map, dx, dy, drawn);
        MapOverlay.residentsAndPlayer(graphics, map, dx, dy, drawn); // the arrow turns back with the map
        pose.popMatrix();
        graphics.disableScissor();

        String coords = player.getBlockX() + ", " + player.getBlockZ();
        graphics.text(minecraft.font, coords, x + (size - minecraft.font.width(coords)) / 2, y + size + 4, 0xFFFFFFFF, true);
        // "N" where north is: at the top, or turned with the map
        float radius = size / 2F - 5;
        int nx = Math.round(centerX + (float) Math.sin(turnAngle) * radius) - 2;
        int ny = Math.round(centerY - (float) Math.cos(turnAngle) * radius) - 4;
        graphics.text(minecraft.font, "N", nx, ny, INK, false); // ink on parchment, like the window's map
    }
}
