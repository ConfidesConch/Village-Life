package io.github.confidesconch.villagelife.core.util;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Keys that should be processed at or after a given tick (Minecraft-free, unit tested).
 * Adding a key that is already waiting keeps its earlier due tick, so repeated changes
 * (e.g. breaking many blocks) cause one rescan instead of many, without delaying it forever.
 */
public final class DueQueue<K> {
    private final Map<K, Long> due = new LinkedHashMap<>();

    public void add(K key, long dueTick) {
        due.merge(key, dueTick, Math::min);
    }

    /** Removes and returns a key whose due tick is reached, oldest first. */
    public Optional<K> pollDue(long now) {
        Iterator<Map.Entry<K, Long>> it = due.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<K, Long> entry = it.next();
            if (entry.getValue() <= now) {
                it.remove();
                return Optional.of(entry.getKey());
            }
        }
        return Optional.empty();
    }

    public void remove(K key) {
        due.remove(key);
    }

    public int size() {
        return due.size();
    }

    public void clear() {
        due.clear();
    }
}
