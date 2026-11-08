package io.github.confidesconch.villagelife.resident.housing;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Assigns beds in valid houses to residents (Minecraft-free, unit tested).
 * <ul>
 *   <li>A resident keeps its bed as long as the bed still exists in a valid house.</li>
 *   <li>Residents without a (valid) bed get free beds in the given order.</li>
 *   <li>No bed is ever given to two residents.</li>
 * </ul>
 */
public final class HousingAllocator {
    private HousingAllocator() {}

    /** One bed (head position) inside a house. */
    public record Bed(UUID buildingId, int x, int y, int z) {}

    public record Result(Map<UUID, Bed> assignment, int freeBeds) {
        public Optional<Bed> bedOf(UUID resident) {
            return Optional.ofNullable(assignment.get(resident));
        }
    }

    /**
     * @param beds      all beds of all valid houses
     * @param residents residents in priority order (e.g. oldest first)
     * @param current   the beds residents had so far
     */
    public static Result allocate(List<Bed> beds, List<UUID> residents, Map<UUID, Bed> current) {
        return allocate(beds, residents, current, Map.of());
    }

    /**
     * Like {@link #allocate(List, List, Map)}; couples (M18, {@code partners}: resident → partner) live
     * in the same house whenever a bed there is free: a partner moves to the other's house, and a
     * newcomer with a partner gets a bed in the partner's house first.
     */
    public static Result allocate(List<Bed> beds, List<UUID> residents, Map<UUID, Bed> current, Map<UUID, UUID> partners) {
        Set<Bed> available = new HashSet<>(beds);
        Set<Bed> taken = new HashSet<>();
        Map<UUID, Bed> assignment = new LinkedHashMap<>();

        // 1) keep existing beds that are still valid
        for (UUID resident : residents) {
            Bed bed = current.get(resident);
            if (bed != null && available.contains(bed) && taken.add(bed)) {
                assignment.put(resident, bed);
            }
        }
        // 1b) couples in different houses move together if there is room
        for (UUID resident : residents) {
            UUID partner = partners.get(resident);
            Bed own = assignment.get(resident);
            Bed theirs = partner == null ? null : assignment.get(partner);
            if (own == null || theirs == null || own.buildingId().equals(theirs.buildingId())) {
                continue;
            }
            Bed free = freeIn(beds, taken, theirs.buildingId());
            if (free != null) {
                taken.remove(own);
                taken.add(free);
                assignment.put(resident, free);
            }
        }
        // 2) give free beds to everyone else, next to the partner if possible
        for (UUID resident : residents) {
            if (assignment.containsKey(resident)) {
                continue;
            }
            UUID partner = partners.get(resident);
            Bed partnerBed = partner == null ? null : assignment.get(partner);
            Bed bed = partnerBed == null ? null : freeIn(beds, taken, partnerBed.buildingId());
            if (bed == null) {
                bed = freeIn(beds, taken, null);
            }
            if (bed != null) {
                taken.add(bed);
                assignment.put(resident, bed);
            }
        }
        return new Result(assignment, beds.size() - taken.size());
    }

    /** The first free bed (in house {@code building}, or anywhere if null). */
    private static Bed freeIn(List<Bed> beds, Set<Bed> taken, UUID building) {
        for (Bed bed : beds) {
            if (!taken.contains(bed) && (building == null || bed.buildingId().equals(building))) {
                return bed;
            }
        }
        return null;
    }
}
