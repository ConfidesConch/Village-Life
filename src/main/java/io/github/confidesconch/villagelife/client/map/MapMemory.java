package io.github.confidesconch.villagelife.client.map;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import io.github.confidesconch.villagelife.VillageLife;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.storage.LevelResource;

/**
 * The map remembers what the player has seen (1.0.1, owner's wish): whenever the client receives or
 * drops a chunk, the surface of its 16×16 columns (height and map colour) is kept. Areas that are no
 * longer loaded are drawn from this memory instead of staying blank. Stored per world (or server) and
 * dimension in {@code <game folder>/villagelife/maps/}, written when leaving the world and every few
 * minutes in the background – only on this computer, nothing goes to the server.
 */
public final class MapMemory {
    private MapMemory() {}

    /** Surface of one chunk: map colour id and height (first free block above the ground) per column. */
    record Snapshot(byte[] colors, short[] heights) {}

    private static final int MAGIC = 0x564C4D50; // "VLMP"
    private static final int VERSION = 1;
    /** Saving in the background at most this often (ms). */
    private static final long SAVE_INTERVAL_MS = 5 * 60 * 1000;

    private static final ExecutorService SAVER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "Village Life map memory");
        thread.setDaemon(true);
        return thread;
    });

    /** Dimension id → chunk key → surface. Only touched on the client's main thread. */
    private static final Map<String, Map<Long, Snapshot>> DIMENSIONS = new HashMap<>();
    private static final Set<String> LOADED_FILES = new java.util.HashSet<>();
    private static final Set<LevelChunk> LIVE = Collections.newSetFromMap(new WeakHashMap<>());
    private static Path folder;
    private static long lastSave = System.currentTimeMillis();
    private static ResourceKey<Level> lastDimension;
    private static Map<Long, Snapshot> lastChunks;

    // ---- events (registered in VillageLifeClient) -----------------------------------------------

    /** Joined a world or server: remember where its maps are stored. */
    public static void onLogin() {
        DIMENSIONS.clear();
        LOADED_FILES.clear();
        LIVE.clear();
        lastDimension = null;
        folder = Minecraft.getInstance().gameDirectory.toPath().resolve("villagelife").resolve("maps").resolve(worldKey());
    }

    /** Left the world: remember the chunks still loaded and save everything. */
    public static void onLogout() {
        for (LevelChunk chunk : LIVE.toArray(LevelChunk[]::new)) {
            capture(chunk);
        }
        saveAll();
        DIMENSIONS.clear();
        LOADED_FILES.clear();
        LIVE.clear();
        lastDimension = null;
        lastChunks = null;
        folder = null;
    }

    public static void onChunkLoad(LevelChunk chunk) {
        if (folder != null) {
            LIVE.add(chunk);
            capture(chunk);
        }
    }

    /** A chunk goes out of view: keep its latest surface (it may have changed while it was loaded). */
    public static void onChunkUnload(LevelChunk chunk) {
        if (folder != null) {
            capture(chunk);
            LIVE.remove(chunk);
            if (System.currentTimeMillis() - lastSave > SAVE_INTERVAL_MS) {
                saveAll();
            }
        }
    }

    // ---- lookups for the map painters -----------------------------------------------------------

    static boolean has(Level level, int x, int z) {
        return snapshot(level, x, z) != null;
    }

    /** Remembered height of the column ({@code Level#getHeight(WORLD_SURFACE)}); only if {@link #has}. */
    static int height(Level level, int x, int z) {
        return snapshot(level, x, z).heights()[index(x, z)];
    }

    static MapColor color(Level level, int x, int z) {
        return MapColor.byId(snapshot(level, x, z).colors()[index(x, z)] & 0x3F);
    }

    // ---- internals ------------------------------------------------------------------------------

    private static Snapshot snapshot(Level level, int x, int z) {
        if (level.dimension() != lastDimension) { // the painters ask many times per pixel – no string per call
            lastDimension = level.dimension();
            lastChunks = chunks(lastDimension.identifier().toString());
        }
        return lastChunks == null ? null : lastChunks.get(ChunkPos.pack(x >> 4, z >> 4));
    }

    private static int index(int x, int z) {
        return (z & 15) * 16 + (x & 15);
    }

    private static void capture(LevelChunk chunk) {
        Map<Long, Snapshot> chunks = chunks(chunk.getLevel().dimension().identifier().toString());
        if (chunks == null) {
            return;
        }
        byte[] colors = new byte[256];
        short[] heights = new short[256];
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int baseX = chunk.getPos().getMinBlockX();
        int baseZ = chunk.getPos().getMinBlockZ();
        int minY = chunk.getMinY();
        boolean any = false;
        for (int dz = 0; dz < 16; dz++) {
            for (int dx = 0; dx < 16; dx++) {
                int height = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, dx, dz) + 1;
                MapColor color = MapColor.NONE;
                int y = height - 1;
                if (y >= minY) {
                    color = chunk.getBlockState(pos.set(baseX + dx, y, baseZ + dz)).getMapColor(chunk, pos);
                    if (color == MapColor.NONE && y > minY) {
                        color = chunk.getBlockState(pos.set(baseX + dx, y - 1, baseZ + dz)).getMapColor(chunk, pos);
                    }
                }
                any |= color != MapColor.NONE;
                colors[dz * 16 + dx] = (byte) color.id;
                heights[dz * 16 + dx] = (short) height;
            }
        }
        if (any) { // an empty chunk (not sent yet) must not overwrite what we knew
            chunks.put(chunk.getPos().pack(), new Snapshot(colors, heights));
        }
    }

    /** The remembered chunks of a dimension, read from disk the first time; null outside a world. */
    private static Map<Long, Snapshot> chunks(String dimension) {
        if (folder == null) {
            return null;
        }
        Map<Long, Snapshot> chunks = DIMENSIONS.computeIfAbsent(dimension, d -> new HashMap<>());
        if (LOADED_FILES.add(dimension)) {
            read(file(dimension), chunks);
        }
        return chunks;
    }

    private static Path file(String dimension) {
        return folder.resolve(dimension.replace(':', '_').replace('/', '_') + ".dat");
    }

    private static void read(Path file, Map<Long, Snapshot> into) {
        if (!Files.isRegularFile(file)) {
            return;
        }
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new GZIPInputStream(Files.newInputStream(file))))) {
            if (in.readInt() != MAGIC || in.readInt() != VERSION) {
                VillageLife.LOGGER.warn("Map memory {} has an unknown format; starting a new one", file);
                return;
            }
            int count = in.readInt();
            for (int i = 0; i < count; i++) {
                long key = in.readLong();
                byte[] colors = new byte[256];
                in.readFully(colors);
                short[] heights = new short[256];
                for (int j = 0; j < 256; j++) {
                    heights[j] = in.readShort();
                }
                into.putIfAbsent(key, new Snapshot(colors, heights));
            }
        } catch (IOException e) {
            VillageLife.LOGGER.warn("Could not read map memory {}; starting a new one", file, e);
        }
    }

    /** Writes every dimension in the background (snapshots are never changed after creation). */
    private static void saveAll() {
        lastSave = System.currentTimeMillis();
        if (folder == null) {
            return;
        }
        for (Map.Entry<String, Map<Long, Snapshot>> entry : DIMENSIONS.entrySet()) {
            Path file = file(entry.getKey());
            Map<Long, Snapshot> copy = new HashMap<>(entry.getValue());
            SAVER.execute(() -> write(file, copy));
        }
    }

    private static void write(Path file, Map<Long, Snapshot> chunks) {
        try {
            Files.createDirectories(file.getParent());
            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new GZIPOutputStream(Files.newOutputStream(temp))))) {
                out.writeInt(MAGIC);
                out.writeInt(VERSION);
                out.writeInt(chunks.size());
                for (Map.Entry<Long, Snapshot> chunk : chunks.entrySet()) {
                    out.writeLong(chunk.getKey());
                    out.write(chunk.getValue().colors());
                    for (short height : chunk.getValue().heights()) {
                        out.writeShort(height);
                    }
                }
            }
            Files.move(temp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            VillageLife.LOGGER.warn("Could not save map memory {}", file, e);
        }
    }

    /** A folder name for this world (single player) or server (multiplayer), safe for the file system. */
    private static String worldKey() {
        Minecraft minecraft = Minecraft.getInstance();
        String key;
        if (minecraft.getSingleplayerServer() != null) {
            Path root = minecraft.getSingleplayerServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
            key = "local_" + root.getFileName();
        } else {
            ServerData server = minecraft.getCurrentServer();
            key = "server_" + (server != null ? server.ip : "unknown");
        }
        return key.replaceAll("[^A-Za-z0-9._-]", "_");
    }
}
