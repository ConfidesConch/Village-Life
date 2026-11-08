package io.github.confidesconch.villagelife.claim;

import java.util.Optional;
import java.util.UUID;

/**
 * Who may claim and free chunks (M11f), free of Minecraft classes so it can be unit tested.
 * Every player has a general limit; chunks inside his own villages first use an extra village
 * limit and only then the general one. Nobody may claim inside somebody else's village.
 */
public final class ClaimRules {
    private ClaimRules() {}

    /** Outcome of a claim attempt; everything except {@link #OK} is a reason shown to the player. */
    public enum Result { OK, ALREADY_YOURS, TAKEN, FOREIGN_VILLAGE, LIMIT, TOO_FAR }

    /** How many claims a player uses and may use. */
    public record Usage(int outside, int inVillage, int limitOutside, int limitVillage) {
        /** General claims in use: claims outside plus village claims above the village limit. */
        public int generalUsed() {
            return outside + Math.max(0, inVillage - limitVillage);
        }

        public boolean canClaimOutside() {
            return generalUsed() < limitOutside;
        }

        public boolean canClaimInVillage() {
            return inVillage < limitVillage || canClaimOutside();
        }
    }

    /**
     * Checks a claim attempt. {@code distanceChunks} is the distance from the player (largest of x/z
     * difference); far chunks are only allowed inside the own village. Admins ignore village and distance rules.
     */
    public static Result check(Optional<UUID> currentOwner, UUID player, boolean inOwnVillage, boolean inForeignVillage,
                               int distanceChunks, int maxDistanceChunks, Usage usage, boolean admin) {
        if (currentOwner.isPresent()) {
            return currentOwner.get().equals(player) ? Result.ALREADY_YOURS : Result.TAKEN;
        }
        if (inForeignVillage && !admin) {
            return Result.FOREIGN_VILLAGE;
        }
        if (!inOwnVillage && !admin && distanceChunks > maxDistanceChunks) {
            return Result.TOO_FAR;
        }
        boolean room = inOwnVillage ? usage.canClaimInVillage() : usage.canClaimOutside();
        return room ? Result.OK : Result.LIMIT;
    }

    /** The owner may always free his chunk, admins may free every chunk. */
    public static boolean mayUnclaim(UUID owner, UUID player, boolean admin) {
        return admin || owner.equals(player);
    }

    /** Building is allowed in free chunks, for the owner, his friends (M11g) and admins. */
    public static boolean mayBuild(Optional<UUID> owner, UUID player, boolean admin, boolean friendOfOwner) {
        return owner.isEmpty() || admin || friendOfOwner || owner.get().equals(player);
    }
}
