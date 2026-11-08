package io.github.confidesconch.villagelife.profession;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Hands out workplaces to residents (Minecraft-free, unit tested). A workplace employs as many residents
 * as it appears in the list (M20b2: bigger buildings have more places). Residents keep their workplace as long as it still exists with the same profession;
 * unemployed residents get free workplaces in the given order.
 */
public final class JobAllocator {
    private JobAllocator() {}

    /** A workplace building and the profession it employs, e.g. {@code villagelife:lumberjack}. */
    public record Workplace(UUID buildingId, String profession) {}

    /** Who works where, and how many places for workers are still free. */
    public record Result(Map<UUID, Workplace> assignment, int freeWorkplaces) {
        public Optional<Workplace> workplaceOf(UUID resident) {
            return Optional.ofNullable(assignment.get(resident));
        }
    }

    /**
     * @param workplaces all available workplaces
     * @param residents  residents in priority order (e.g. oldest first)
     * @param current    current jobs; jobs at workplaces that no longer exist are dropped
     */
    public static Result allocate(List<Workplace> workplaces, List<UUID> residents, Map<UUID, Workplace> current) {
        return allocate(workplaces, residents, current, Map.of());
    }

    /**
     * Like {@link #allocate(List, List, Map)}; a resident with {@code preferred} professions (M18b: the
     * parents' jobs) gets a free workplace of one of them first, if there is one.
     */
    public static Result allocate(List<Workplace> workplaces, List<UUID> residents, Map<UUID, Workplace> current,
                                  Map<UUID, Set<String>> preferred) {
        // M20b2: a workplace may appear several times in the list – once per place for a worker
        Map<Workplace, Integer> free = new HashMap<>();
        workplaces.forEach(w -> free.merge(w, 1, Integer::sum));
        Map<UUID, Workplace> assignment = new LinkedHashMap<>();
        for (UUID resident : residents) {
            Workplace job = current.get(resident);
            if (job != null && take(free, job)) {
                assignment.put(resident, job);
            }
        }
        Set<UUID> seen = new HashSet<>();
        for (UUID resident : residents) {
            if (assignment.containsKey(resident) || !seen.add(resident)) {
                continue;
            }
            Set<String> wishes = preferred.getOrDefault(resident, Set.of());
            Workplace chosen = workplaces.stream()
                    .filter(w -> wishes.contains(w.profession()) && free.getOrDefault(w, 0) > 0).findFirst()
                    .orElseGet(() -> workplaces.stream().filter(w -> free.getOrDefault(w, 0) > 0).findFirst().orElse(null));
            if (chosen == null) {
                break;
            }
            take(free, chosen);
            assignment.put(resident, chosen);
        }
        int freePlaces = free.values().stream().mapToInt(Integer::intValue).sum();
        return new Result(assignment, freePlaces);
    }

    /** Takes one free place of the workplace; false if there is none (left). */
    private static boolean take(Map<Workplace, Integer> free, Workplace workplace) {
        int left = free.getOrDefault(workplace, 0);
        if (left <= 0) {
            return false;
        }
        free.put(workplace, left - 1);
        return true;
    }
}
