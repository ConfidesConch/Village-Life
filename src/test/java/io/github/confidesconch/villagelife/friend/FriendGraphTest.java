package io.github.confidesconch.villagelife.friend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.UUID;

import io.github.confidesconch.villagelife.friend.FriendGraph.Result;

import org.junit.jupiter.api.Test;

class FriendGraphTest {
    private static final UUID A = UUID.randomUUID();
    private static final UUID B = UUID.randomUUID();
    private static final UUID C = UUID.randomUUID();

    @Test
    void inviteAndAcceptMakesBothFriends() {
        FriendGraph graph = new FriendGraph();
        assertEquals(Result.INVITED, graph.invite(A, B, 10));
        assertEquals(Result.ALREADY_INVITED, graph.invite(A, B, 10));
        assertFalse(graph.areFriends(A, B));
        assertEquals(Set.of(A), graph.invitesTo(B));
        assertEquals(Set.of(B), graph.invitesFrom(A));
        assertEquals(Result.BECAME_FRIENDS, graph.accept(B, A, 10));
        assertTrue(graph.areFriends(A, B));
        assertTrue(graph.areFriends(B, A));
        assertTrue(graph.invitesTo(B).isEmpty());
        assertEquals(Result.ALREADY_FRIENDS, graph.invite(B, A, 10));
    }

    @Test
    void mutualInvitationsBecomeFriendshipAndSelfIsRefused() {
        FriendGraph graph = new FriendGraph();
        assertEquals(Result.SELF, graph.invite(A, A, 10));
        graph.invite(A, B, 10);
        assertEquals(Result.BECAME_FRIENDS, graph.invite(B, A, 10));
    }

    @Test
    void declineRemoveAndWithdraw() {
        FriendGraph graph = new FriendGraph();
        graph.invite(A, B, 10);
        assertEquals(Result.DECLINED, graph.decline(B, A));
        assertEquals(Result.NO_INVITE, graph.accept(B, A, 10));
        graph.invite(A, C, 10);
        assertEquals(Result.REMOVED, graph.remove(A, C)); // withdraw own invitation
        assertTrue(graph.invitesTo(C).isEmpty());
        graph.invite(A, B, 10);
        graph.accept(B, A, 10);
        assertEquals(Result.REMOVED, graph.remove(B, A));
        assertFalse(graph.areFriends(A, B));
        assertEquals(Result.NOT_FRIENDS, graph.remove(B, A));
    }

    @Test
    void limitIsRespected() {
        FriendGraph graph = new FriendGraph();
        graph.invite(A, B, 1);
        graph.accept(B, A, 1);
        assertEquals(Result.LIMIT, graph.invite(A, C, 1));
        graph.invite(C, A, 1);
        assertEquals(Result.LIMIT, graph.accept(A, C, 1));
    }

    @Test
    void loadRestoresBothDirections() {
        FriendGraph graph = new FriendGraph();
        graph.load(A, Set.of(B), Set.of(C));
        assertTrue(graph.areFriends(B, A));
        assertEquals(Set.of(C), graph.invitesTo(A));
    }
}
