package io.github.confidesconch.villagelife.waypoint;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import io.github.confidesconch.villagelife.core.util.MarkerColors;
import io.github.confidesconch.villagelife.core.util.TextRules;

/**
 * Changes to a player's waypoint list (M11e), free of Minecraft classes so they can be unit tested.
 * Every method checks its input and returns the new list, or empty if the change is not allowed.
 */
public final class WaypointRules {
    private WaypointRules() {}

    public static final int MAX_NAME_LENGTH = 24;
    /** Farther out than this, the world border makes coordinates meaningless. */
    public static final int WORLD_LIMIT = 30_000_000;

    /** Adds a waypoint if the limit allows it; an empty name becomes "#id". */
    public static Optional<List<Waypoint>> add(List<Waypoint> list, String name, int color, String dimension,
                                               int x, int y, int z, int limit) {
        if (list.size() >= limit || Math.abs(x) > WORLD_LIMIT || Math.abs(z) > WORLD_LIMIT || dimension.isBlank()) {
            return Optional.empty();
        }
        int id = list.stream().mapToInt(Waypoint::id).max().orElse(0) + 1;
        List<Waypoint> result = new ArrayList<>(list);
        result.add(new Waypoint(id, nameOrDefault(name, id), MarkerColors.clamp(color), dimension, x, y, z, true, false));
        return Optional.of(List.copyOf(result));
    }

    /** Renames, recolours and shares (or unshares) waypoint {@code id}. */
    public static Optional<List<Waypoint>> edit(List<Waypoint> list, int id, String name, int color, boolean shared) {
        return replace(list, id, w -> w.withDetails(nameOrDefault(name, id), MarkerColors.clamp(color), shared));
    }

    /** Shows or hides waypoint {@code id} on the map. */
    public static Optional<List<Waypoint>> toggle(List<Waypoint> list, int id) {
        return replace(list, id, w -> w.withVisible(!w.visible()));
    }

    public static Optional<List<Waypoint>> remove(List<Waypoint> list, int id) {
        if (list.stream().noneMatch(w -> w.id() == id)) {
            return Optional.empty();
        }
        return Optional.of(list.stream().filter(w -> w.id() != id).toList());
    }

    private static Optional<List<Waypoint>> replace(List<Waypoint> list, int id, java.util.function.UnaryOperator<Waypoint> change) {
        if (list.stream().noneMatch(w -> w.id() == id)) {
            return Optional.empty();
        }
        return Optional.of(list.stream().map(w -> w.id() == id ? change.apply(w) : w).toList());
    }

    static String nameOrDefault(String name, int id) {
        String cleaned = TextRules.cleanName(name, MAX_NAME_LENGTH);
        return cleaned.isEmpty() ? "#" + id : cleaned;
    }
}
