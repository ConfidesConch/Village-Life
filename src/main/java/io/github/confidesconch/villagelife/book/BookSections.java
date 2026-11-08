package io.github.confidesconch.villagelife.book;

import java.util.LinkedHashMap;
import java.util.Map;

import io.github.confidesconch.villagelife.village.Village;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

/**
 * Server side of the village book's chapters: each chapter registers a provider that writes its
 * data into its own section of the snapshot. Later systems (trade, defence, reputation …) add their
 * own sections here – the book and the payload stay unchanged.
 */
public final class BookSections {
    private BookSections() {}

    /** Writes one chapter's data for the given player and village. */
    @FunctionalInterface
    public interface Provider {
        void write(ServerPlayer player, Village village, CompoundTag out);
    }

    /** Writes a chapter's data that does not depend on a village (e.g. the character page, M11h). */
    @FunctionalInterface
    public interface PlayerProvider {
        void write(ServerPlayer player, CompoundTag out);
    }

    private static final Map<String, Provider> PROVIDERS = new LinkedHashMap<>();
    private static final Map<String, PlayerProvider> GLOBAL = new LinkedHashMap<>();

    /** Register during mod construction; written into every snapshot, with or without a village. */
    public static synchronized void registerGlobal(String key, PlayerProvider provider) {
        if (GLOBAL.putIfAbsent(key, provider) != null || PROVIDERS.containsKey(key)) {
            throw new IllegalStateException("Book section registered twice: " + key);
        }
    }

    static void addGlobal(ServerPlayer player, CompoundTag data) {
        GLOBAL.forEach((key, provider) -> {
            CompoundTag section = new CompoundTag();
            provider.write(player, section);
            data.put(key, section);
        });
    }

    /** Register during mod construction. The key is the chapter id, e.g. {@code overview}. */
    public static synchronized void register(String key, Provider provider) {
        if (PROVIDERS.putIfAbsent(key, provider) != null) {
            throw new IllegalStateException("Book section registered twice: " + key);
        }
    }

    /** True if {@code key} is a known section (village or global). */
    static synchronized boolean isKnown(String key) {
        return PROVIDERS.containsKey(key) || GLOBAL.containsKey(key);
    }

    /** M21b2: only the sections {@code keys} (unknown keys are skipped). */
    static CompoundTag buildSome(ServerPlayer player, Village village, java.util.Collection<String> keys) {
        CompoundTag data = new CompoundTag();
        for (String key : keys) {
            Provider provider = PROVIDERS.get(key);
            PlayerProvider global = GLOBAL.get(key);
            if (provider == null && global == null) {
                continue;
            }
            CompoundTag section = new CompoundTag();
            if (provider != null) {
                provider.write(player, village, section);
            } else {
                global.write(player, section);
            }
            data.put(key, section);
        }
        return data;
    }
}
