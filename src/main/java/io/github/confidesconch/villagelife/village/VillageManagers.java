package io.github.confidesconch.villagelife.village;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Who may manage a village (M11g2): the owner and the friends he made managers. Managers may change
 * settings and zones and claim chunks of the village for the owner; only the owner may rename the
 * village and choose managers. Free of Minecraft classes (unit tested).
 */
public final class VillageManagers {
    private VillageManagers() {}

    /** Upper bound so a village record stays small. */
    public static final int MAX_MANAGERS = 32;

    public static boolean mayManage(UUID owner, List<UUID> managers, UUID player) {
        return owner.equals(player) || managers.contains(player);
    }

    /**
     * Makes {@code target} a manager or removes him. Only friends of the owner can become managers;
     * the owner himself is never in the list. Empty if nothing changes or the change is not allowed.
     */
    public static Optional<List<UUID>> set(List<UUID> managers, UUID owner, UUID target, boolean manager, boolean isFriend) {
        if (target.equals(owner) || managers.contains(target) == manager) {
            return Optional.empty();
        }
        if (manager && (!isFriend || managers.size() >= MAX_MANAGERS)) {
            return Optional.empty();
        }
        List<UUID> result = new ArrayList<>(managers);
        if (manager) {
            result.add(target);
        } else {
            result.remove(target);
        }
        return Optional.of(List.copyOf(result));
    }
}
