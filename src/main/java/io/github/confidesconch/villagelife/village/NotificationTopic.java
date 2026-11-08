package io.github.confidesconch.villagelife.village;

import java.util.Locale;
import java.util.Optional;

/**
 * Topics of the chat messages to owner and managers (M19a). Every player can switch each topic off for
 * himself in the book ({@link NotificationSettings}).
 */
public enum NotificationTopic {
    /** Couples, births, grown-up children. */
    FAMILY,
    /** Village stages and their rewards. */
    GROWTH,
    /** Market, contracts and deliveries. */
    TRADE,
    /** Trading caravans arriving and leaving. */
    CARAVAN,
    /** Buildings that became invalid or valid again. */
    BUILDINGS,
    /** Residents changing jobs because of the priorities. */
    JOBS,
    /** Fights: knocked-out guards (M20a), attacks on the village (M20b). */
    DEFENSE;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<NotificationTopic> byId(String id) {
        for (NotificationTopic topic : values()) {
            if (topic.id().equals(id)) {
                return Optional.of(topic);
            }
        }
        return Optional.empty();
    }
}
