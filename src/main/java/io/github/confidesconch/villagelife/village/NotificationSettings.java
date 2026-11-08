package io.github.confidesconch.villagelife.village;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.github.confidesconch.villagelife.VillageLife;

import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Which message topics each player switched off (M19a), saved in the overworld as
 * {@code data/villagelife/notifications.dat}. Applies to all villages the player manages. Players who
 * never changed anything are not stored (everything on). Server thread only.
 *
 * <p>Data versions: 1 = muted topics per player (M19a).
 */
public final class NotificationSettings extends SavedData {
    public static final int DATA_VERSION = 1;

    private static final Codec<NotificationSettings> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("data_version", 1).forGetter(store -> DATA_VERSION),
            Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.STRING.listOf()).optionalFieldOf("muted", Map.of())
                    .forGetter(store -> store.saved())
    ).apply(instance, NotificationSettings::new));

    public static final SavedDataType<NotificationSettings> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(VillageLife.MOD_ID, "notifications"), NotificationSettings::new, CODEC);

    private final Map<UUID, Set<NotificationTopic>> muted = new HashMap<>();

    public NotificationSettings() {}

    private NotificationSettings(int loadedDataVersion, Map<UUID, List<String>> loaded) {
        loaded.forEach((player, topics) -> {
            Set<NotificationTopic> set = new HashSet<>();
            topics.forEach(id -> NotificationTopic.byId(id).ifPresent(set::add)); // unknown topics are dropped
            if (!set.isEmpty()) {
                muted.put(player, set);
            }
        });
        if (loadedDataVersion < DATA_VERSION) {
            setDirty();
        }
    }

    public static NotificationSettings get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    private Map<UUID, List<String>> saved() {
        Map<UUID, List<String>> map = new HashMap<>();
        muted.forEach((player, topics) -> map.put(player, topics.stream().map(NotificationTopic::id).sorted().toList()));
        return map;
    }

    public boolean wants(UUID player, NotificationTopic topic) {
        return !muted.getOrDefault(player, Set.of()).contains(topic);
    }

    public void set(UUID player, NotificationTopic topic, boolean on) {
        Set<NotificationTopic> set = new HashSet<>(muted.getOrDefault(player, Set.of()));
        boolean changed = on ? set.remove(topic) : set.add(topic);
        if (!changed) {
            return;
        }
        if (set.isEmpty()) {
            muted.remove(player);
        } else {
            muted.put(player, set);
        }
        setDirty();
    }
}
