package io.github.confidesconch.villagelife.client.map;

import io.github.confidesconch.villagelife.core.config.ClientConfig;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.client.gui.GuiLayer;

/**
 * The minimap in the top right corner (M11d): 128 × 128 blocks around the player, north up,
 * with claims (M11f), waypoints (M11e), other players (M11g), residents and the player. Shown when {@code showMinimap} is on (minimap key, default N).
 */
public final class MinimapLayer implements GuiLayer {
    private static final int PIXELS = 128;
    private static final int SIZE = 64;
    private static final int MARGIN = 6;
    private static final int ROWS_PER_FRAME = 8;

    private MapImage map;

    @Override
    public void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (!ClientConfig.SHOW_MINIMAP.get() || player == null || minecraft.level == null) {
            return;
        }
        if (map == null) {
            map = new MapImage("minimap", PIXELS, 0xFF1E1E1E);
        }
        map.view(player.getX(), player.getZ(), 1);
        map.paintRows(minecraft.level, ROWS_PER_FRAME);

        int x = graphics.guiWidth() - SIZE - MARGIN;
        int y = MARGIN;
        graphics.fill(x - 2, y - 2, x + SIZE + 2, y + SIZE + 2, 0xFF3A2A1A);
        graphics.fill(x - 1, y - 1, x + SIZE + 1, y + SIZE + 1, 0xFFB08A5A);
        graphics.blit(RenderPipelines.GUI_TEXTURED, map.textureId(), x, y, 0, 0, SIZE, SIZE, PIXELS, PIXELS, PIXELS, PIXELS);
        MapOverlay.claims(graphics, map, x, y, SIZE, false);
        MapOverlay.waypoints(graphics, map, x, y, SIZE, ClientWaypoints.shownHere(), true);
        MapOverlay.otherPlayers(graphics, map, x, y, SIZE);
        MapOverlay.residentsAndPlayer(graphics, map, x, y, SIZE);
        String coords = player.getBlockX() + ", " + player.getBlockZ();
        graphics.text(minecraft.font, coords, x + (SIZE - minecraft.font.width(coords)) / 2, y + SIZE + 4, 0xFFFFFFFF, true);
        graphics.text(minecraft.font, "N", x + SIZE / 2 - 2, y + 1, 0xFFFFFFFF, true);
    }
}
