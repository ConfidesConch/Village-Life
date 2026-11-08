package io.github.confidesconch.villagelife.claim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.UUID;

import io.github.confidesconch.villagelife.claim.ClaimRules.Result;
import io.github.confidesconch.villagelife.claim.ClaimRules.Usage;

import org.junit.jupiter.api.Test;

class ClaimRulesTest {
    private static final UUID ME = UUID.randomUUID();
    private static final UUID OTHER = UUID.randomUUID();
    private static final Usage EMPTY = new Usage(0, 0, 16, 64);

    private static Result check(Optional<UUID> owner, boolean own, boolean foreign, int distance, Usage usage, boolean admin) {
        return ClaimRules.check(owner, ME, own, foreign, distance, 64, usage, admin);
    }

    @Test
    void freeChunkNearbyCanBeClaimed() {
        assertEquals(Result.OK, check(Optional.empty(), false, false, 3, EMPTY, false));
    }

    @Test
    void takenChunksAndForeignVillagesAreRefused() {
        assertEquals(Result.ALREADY_YOURS, check(Optional.of(ME), false, false, 0, EMPTY, false));
        assertEquals(Result.TAKEN, check(Optional.of(OTHER), false, false, 0, EMPTY, true));
        assertEquals(Result.FOREIGN_VILLAGE, check(Optional.empty(), false, true, 0, EMPTY, false));
        assertEquals(Result.OK, check(Optional.empty(), false, true, 0, EMPTY, true)); // admin
    }

    @Test
    void farChunksOnlyInsideTheOwnVillage() {
        assertEquals(Result.TOO_FAR, check(Optional.empty(), false, false, 65, EMPTY, false));
        assertEquals(Result.OK, check(Optional.empty(), true, false, 500, EMPTY, false));
    }

    @Test
    void villageClaimsUseTheVillageLimitFirst() {
        Usage generalFull = new Usage(16, 10, 16, 64);
        assertEquals(Result.LIMIT, check(Optional.empty(), false, false, 0, generalFull, false));
        assertEquals(Result.OK, check(Optional.empty(), true, false, 0, generalFull, false));
        Usage villageFull = new Usage(0, 64, 16, 64);
        assertEquals(Result.OK, check(Optional.empty(), true, false, 0, villageFull, false)); // overflows into general
        assertEquals(0, villageFull.generalUsed());
        Usage bothFull = new Usage(6, 74, 16, 64);
        assertEquals(16, bothFull.generalUsed());
        assertEquals(Result.LIMIT, check(Optional.empty(), true, false, 0, bothFull, false));
    }

    @Test
    void buildingAndFreeingRights() {
        assertTrue(ClaimRules.mayBuild(Optional.empty(), ME, false, false));
        assertTrue(ClaimRules.mayBuild(Optional.of(ME), ME, false, false));
        assertFalse(ClaimRules.mayBuild(Optional.of(OTHER), ME, false, false));
        assertTrue(ClaimRules.mayBuild(Optional.of(OTHER), ME, true, false));
        assertTrue(ClaimRules.mayBuild(Optional.of(OTHER), ME, false, true)); // friend of the owner
        assertTrue(ClaimRules.mayUnclaim(ME, ME, false));
        assertFalse(ClaimRules.mayUnclaim(OTHER, ME, false));
        assertTrue(ClaimRules.mayUnclaim(OTHER, ME, true));
    }

    @Test
    void chunkKeysAreUniqueForNegativeCoordinates() {
        assertNotEquals(Claim.key(-1, 0), Claim.key(0, -1));
        assertNotEquals(Claim.key(1, -1), Claim.key(-1, 1));
        assertEquals(Claim.key(-5, 7), new Claim(-5, 7, ME, "x").key());
    }
}
