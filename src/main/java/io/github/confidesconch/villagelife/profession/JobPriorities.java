package io.github.confidesconch.villagelife.profession;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import io.github.confidesconch.villagelife.profession.JobAllocator.Workplace;

/**
 * Job priorities (M19a, owner's choice "1b 2a", Minecraft-free and unit tested): the owner sorts all
 * professions in the book; free workplaces of higher professions get workers first, and each morning a
 * worker of a lower profession moves to a free workplace of a higher one.
 */
public final class JobPriorities {
    private JobPriorities() {}

    /**
     * The full ranking: the stored order (professions that still exist), then all others in their
     * default order. Duplicates are ignored.
     */
    public static List<String> ranking(List<String> stored, List<String> defaults) {
        Set<String> known = new HashSet<>(defaults);
        List<String> result = new ArrayList<>();
        for (String id : stored) {
            if (known.contains(id) && !result.contains(id)) {
                result.add(id);
            }
        }
        for (String id : defaults) {
            if (!result.contains(id)) {
                result.add(id);
            }
        }
        return result;
    }

    /** The ranking with {@code id} moved one place up ({@code delta} -1) or down (+1); unchanged at the ends. */
    public static List<String> move(List<String> ranking, String id, int delta) {
        int from = ranking.indexOf(id);
        int to = from + Integer.signum(delta);
        if (from < 0 || to < 0 || to >= ranking.size()) {
            return ranking;
        }
        List<String> result = new ArrayList<>(ranking);
        result.set(from, ranking.get(to));
        result.set(to, id);
        return result;
    }

    /** Workplaces sorted by the rank of their profession (stable; unknown professions last). */
    public static List<Workplace> sort(List<Workplace> workplaces, List<String> ranking) {
        List<Workplace> sorted = new ArrayList<>(workplaces);
        sorted.sort(Comparator.comparingInt(w -> rank(ranking, w.profession())));
        return sorted;
    }

    static int rank(List<String> ranking, String profession) {
        int index = ranking.indexOf(profession);
        return index < 0 ? Integer.MAX_VALUE : index;
    }

    /**
     * The morning moves: for every free place, best first, the worker with the lowest-ranked profession
     * below it moves there (each worker at most once). A workplace may appear several times in
     * {@code workplaces} (one entry per place, M20b2). Places left behind by a mover stay free until the
     * next morning (no chains). Returns resident → new workplace, in move order.
     */
    public static Map<UUID, Workplace> rebalance(List<Workplace> workplaces, Map<UUID, Workplace> assignment,
                                                 List<String> ranking) {
        Map<Workplace, Integer> free = new HashMap<>();
        workplaces.forEach(w -> free.merge(w, 1, Integer::sum));
        assignment.values().forEach(w -> free.computeIfPresent(w, (k, n) -> n - 1));
        Map<UUID, Workplace> current = new LinkedHashMap<>(assignment);
        Map<UUID, Workplace> moves = new LinkedHashMap<>();
        for (Workplace place : sort(new ArrayList<>(new LinkedHashSet<>(workplaces)), ranking)) {
            int freeRank = rank(ranking, place.profession());
            while (free.getOrDefault(place, 0) > 0) {
                UUID mover = null;
                int moverRank = freeRank;
                for (Map.Entry<UUID, Workplace> entry : current.entrySet()) {
                    int r = rank(ranking, entry.getValue().profession());
                    if (r > moverRank && !moves.containsKey(entry.getKey())) {
                        mover = entry.getKey();
                        moverRank = r;
                    }
                }
                if (mover == null) {
                    break;
                }
                free.merge(place, -1, Integer::sum);
                current.put(mover, place);
                moves.put(mover, place);
            }
        }
        return moves;
    }
}
