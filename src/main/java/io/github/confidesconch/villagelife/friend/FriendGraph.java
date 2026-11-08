package io.github.confidesconch.villagelife.friend;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Who is friends with whom, and open invitations (M11g). Friendship is mutual: an invitation must be
 * accepted; if both invite each other, they become friends at once. Free of Minecraft classes (unit tested).
 */
public final class FriendGraph {
    /** Outcome of an action; the server tells the player about everything except the success cases. */
    public enum Result { INVITED, BECAME_FRIENDS, REMOVED, DECLINED, SELF, ALREADY_FRIENDS, ALREADY_INVITED, LIMIT, NO_INVITE, NOT_FRIENDS }

    private final Map<UUID, Set<UUID>> friends = new HashMap<>();
    /** Invited player → players who invited him. */
    private final Map<UUID, Set<UUID>> invites = new HashMap<>();

    public Result invite(UUID from, UUID to, int limit) {
        if (from.equals(to)) {
            return Result.SELF;
        }
        if (areFriends(from, to)) {
            return Result.ALREADY_FRIENDS;
        }
        if (invitesTo(from).contains(to)) { // the other one asked first: accept
            return accept(from, to, limit);
        }
        if (friendsOf(from).size() >= limit) {
            return Result.LIMIT;
        }
        return set(invites, to).add(from) ? Result.INVITED : Result.ALREADY_INVITED;
    }

    public Result accept(UUID me, UUID from, int limit) {
        if (!invitesTo(me).contains(from)) {
            return Result.NO_INVITE;
        }
        if (friendsOf(me).size() >= limit || friendsOf(from).size() >= limit) {
            return Result.LIMIT;
        }
        removeInvite(me, from);
        removeInvite(from, me);
        set(friends, me).add(from);
        set(friends, from).add(me);
        return Result.BECAME_FRIENDS;
    }

    public Result decline(UUID me, UUID from) {
        return removeInvite(me, from) ? Result.DECLINED : Result.NO_INVITE;
    }

    /** Ends a friendship, or withdraws an own invitation. */
    public Result remove(UUID me, UUID other) {
        if (removeInvite(other, me)) {
            return Result.REMOVED;
        }
        if (!areFriends(me, other)) {
            return Result.NOT_FRIENDS;
        }
        set(friends, me).remove(other);
        set(friends, other).remove(me);
        return Result.REMOVED;
    }

    public boolean areFriends(UUID a, UUID b) {
        return friendsOf(a).contains(b);
    }

    public Set<UUID> friendsOf(UUID player) {
        return Set.copyOf(friends.getOrDefault(player, Set.of()));
    }

    /** Players who invited {@code player}. */
    public Set<UUID> invitesTo(UUID player) {
        return Set.copyOf(invites.getOrDefault(player, Set.of()));
    }

    /** Players {@code player} invited and who have not answered yet. */
    public Set<UUID> invitesFrom(UUID player) {
        Set<UUID> result = new LinkedHashSet<>();
        invites.forEach((to, from) -> {
            if (from.contains(player)) {
                result.add(to);
            }
        });
        return result;
    }

    /** All players that appear anywhere (for saving). */
    public Set<UUID> players() {
        Set<UUID> all = new LinkedHashSet<>(friends.keySet());
        all.addAll(invites.keySet());
        return all;
    }

    /** Restores one player's entries while loading. */
    public void load(UUID player, Set<UUID> playerFriends, Set<UUID> invitedBy) {
        playerFriends.forEach(friend -> {
            set(friends, player).add(friend);
            set(friends, friend).add(player);
        });
        invitedBy.forEach(from -> set(invites, player).add(from));
    }

    private boolean removeInvite(UUID to, UUID from) {
        Set<UUID> set = invites.get(to);
        boolean removed = set != null && set.remove(from);
        if (set != null && set.isEmpty()) {
            invites.remove(to);
        }
        return removed;
    }

    private static Set<UUID> set(Map<UUID, Set<UUID>> map, UUID key) {
        return map.computeIfAbsent(key, k -> new LinkedHashSet<>());
    }
}
