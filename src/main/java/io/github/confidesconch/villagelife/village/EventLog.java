package io.github.confidesconch.villagelife.village;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.github.confidesconch.villagelife.VillageLife;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * The village's event log (M21b3, owner's choice "4b"): the last {@value #MAX_ENTRIES} things that
 * happened in each village – everything the managers are told in the chat, plus arrivals, deaths and the
 * alarm. Owner and managers can delete single entries or all of them, and hide a problem until the
 * next day (M21b6b). Saved in the overworld as {@code data/villagelife/event_log.dat}. Server thread only.
 *
 * <p>Data versions: 1 = entries per village (M21b3); 2 = entries have an id, hidden problems (M21b6b).
 */
public final class EventLog extends SavedData {
    public static final int DATA_VERSION = 2;
    /** Older entries are dropped. */
    public static final int MAX_ENTRIES = 40;

    /** One event: id (unique in its village), in-game day, topic (a {@link NotificationTopic} id or "residents"), the message. */
    public record Entry(long id, long day, String topic, Component text) {
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.LONG.optionalFieldOf("id", 0L).forGetter(Entry::id), // missing before version 2
                Codec.LONG.fieldOf("day").forGetter(Entry::day),
                Codec.STRING.fieldOf("topic").forGetter(Entry::topic),
                ComponentSerialization.CODEC.fieldOf("text").forGetter(Entry::text)
        ).apply(instance, Entry::new));
    }

    private static final Codec<EventLog> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("data_version", 1).forGetter(log -> DATA_VERSION),
            Codec.unboundedMap(UUIDUtil.STRING_CODEC, Entry.CODEC.listOf()).optionalFieldOf("villages", Map.of())
                    .forGetter(log -> log.entries),
            Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.unboundedMap(Codec.STRING, Codec.LONG))
                    .optionalFieldOf("hidden_problems", Map.of()).forGetter(log -> log.hidden)
    ).apply(instance, EventLog::new));

    public static final SavedDataType<EventLog> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(VillageLife.MOD_ID, "event_log"), EventLog::new, CODEC);

    /** Oldest first. */
    private final Map<UUID, List<Entry>> entries = new HashMap<>();
    /** Per village: hidden problem → day it was hidden on (it shows again from the next day). */
    private final Map<UUID, Map<String, Long>> hidden = new HashMap<>();

    public EventLog() {}

    private EventLog(int loadedDataVersion, Map<UUID, List<Entry>> loaded, Map<UUID, Map<String, Long>> loadedHidden) {
        loaded.forEach((village, list) -> {
            List<Entry> copy = new ArrayList<>();
            long nextId = 1;
            for (Entry entry : list) { // version 1 had no ids: number them
                copy.add(entry.id() > 0 ? entry : new Entry(nextId, entry.day(), entry.topic(), entry.text()));
                nextId = Math.max(nextId, copy.getLast().id()) + 1;
            }
            entries.put(village, copy);
        });
        loadedHidden.forEach((village, map) -> hidden.put(village, new HashMap<>(map)));
        if (loadedDataVersion < DATA_VERSION) {
            setDirty();
        }
    }

    public static EventLog get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    /** Adds an event to the village's log. */
    public static void record(MinecraftServer server, Village village, String topic, Component text) {
        EventLog log = get(server);
        long day = server.overworld().getGameTime() / 24000L + 1;
        List<Entry> list = log.entries.computeIfAbsent(village.id(), id -> new ArrayList<>());
        long id = list.isEmpty() ? 1 : list.getLast().id() + 1;
        list.add(new Entry(id, day, topic, text));
        while (list.size() > MAX_ENTRIES) {
            list.removeFirst();
        }
        log.setDirty();
    }

    /** The village's events, newest first. */
    public List<Entry> of(UUID village) {
        return entries.getOrDefault(village, List.of()).reversed();
    }

    /** Removes one entry (owner or manager, M21b6b). */
    public void delete(UUID village, long id) {
        List<Entry> list = entries.get(village);
        if (list != null && list.removeIf(e -> e.id() == id)) {
            setDirty();
        }
    }

    /** Empties the village's log. */
    public void clear(UUID village) {
        if (entries.remove(village) != null) {
            setDirty();
        }
    }

    /** Hides problem {@code problemId} until the day after {@code today} (M21b6b, owner's choice). */
    public void hideProblem(UUID village, String problemId, long today) {
        hidden.computeIfAbsent(village, v -> new HashMap<>()).put(problemId, today);
        setDirty();
    }

    /** True if the problem was hidden today. Older hidings are dropped. */
    public boolean isHidden(UUID village, String problemId, long today) {
        Map<String, Long> map = hidden.get(village);
        if (map == null) {
            return false;
        }
        if (map.values().removeIf(day -> day != today)) {
            setDirty();
        }
        return map.containsKey(problemId);
    }

    /** The village was dissolved. */
    public void forget(UUID village) {
        boolean changed = entries.remove(village) != null;
        changed |= hidden.remove(village) != null;
        if (changed) {
            setDirty();
        }
    }
}
