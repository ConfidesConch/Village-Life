package io.github.confidesconch.villagelife.village;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class VillageManagersTest {
    private static final UUID OWNER = UUID.randomUUID();
    private static final UUID FRIEND = UUID.randomUUID();
    private static final UUID STRANGER = UUID.randomUUID();

    @Test
    void ownerAndManagersMayManage() {
        assertTrue(VillageManagers.mayManage(OWNER, List.of(), OWNER));
        assertTrue(VillageManagers.mayManage(OWNER, List.of(FRIEND), FRIEND));
        assertFalse(VillageManagers.mayManage(OWNER, List.of(FRIEND), STRANGER));
    }

    @Test
    void onlyFriendsBecomeManagers() {
        List<UUID> managers = VillageManagers.set(List.of(), OWNER, FRIEND, true, true).orElseThrow();
        assertEquals(List.of(FRIEND), managers);
        assertTrue(VillageManagers.set(List.of(), OWNER, STRANGER, true, false).isEmpty());
        assertTrue(VillageManagers.set(List.of(), OWNER, OWNER, true, true).isEmpty());
        assertTrue(VillageManagers.set(managers, OWNER, FRIEND, true, true).isEmpty()); // no change
    }

    @Test
    void managersCanBeRemovedEvenIfNoLongerFriends() {
        assertEquals(List.of(), VillageManagers.set(List.of(FRIEND), OWNER, FRIEND, false, false).orElseThrow());
    }
}
