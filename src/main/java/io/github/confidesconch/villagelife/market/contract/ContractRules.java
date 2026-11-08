package io.github.confidesconch.villagelife.market.contract;

/**
 * Pure rules of trade contracts between villages (M15d, owner's choice "1a 2a 3a 4a"): the giving
 * village delivers a share of its surplus of one good (at most {@code max} per delivery) every few
 * days; the receiving village pays {@code paymentCount} of the payment good for a full delivery, less
 * for a smaller one (proportionally).
 */
public final class ContractRules {
    private ContractRules() {}

    public static final int PERCENT_MIN = 10;
    public static final int PERCENT_MAX = 100;
    public static final int PERCENT_STEP = 10;
    public static final int MAX_MIN = 8;
    public static final int MAX_MAX = 256;
    public static final int MAX_STEP = 8;
    public static final int DAYS_MIN = 1;
    public static final int DAYS_MAX = 14;
    public static final int PAYMENT_MIN = 1;
    public static final int PAYMENT_MAX = 64;
    /** Contracts (offers included) one village may have at once. */
    public static final int CONTRACTS_PER_VILLAGE = 8;
    public static final long DAY_TICKS = 24000;
    /** Notice period after a cancellation: one Minecraft day. */
    public static final long NOTICE_TICKS = DAY_TICKS;

    /** Failed deliveries (or payments) in a row after which a contract is cancelled automatically (M15d3). */
    public static final int MISSES_TO_CANCEL = 3;

    /** The payment due now: the share for this delivery plus what is still owed (M15d3, "1a"). */
    public static int dueWithDebt(int payment, int debt) {
        return Math.max(0, payment) + Math.max(0, debt);
    }

    /** Whether so many failures in a row end the contract (M15d3, "2a"). */
    public static boolean cancelsAfter(int misses) {
        return misses >= MISSES_TO_CANCEL;
    }

    /** Travel time: one hour (1000 ticks) per 100 blocks, at least one hour, at most one day. */
    public static long travelTicks(double distance) {
        long ticks = (long) (distance * 10);
        return Math.max(1000, Math.min(DAY_TICKS, ticks));
    }

    /** Whether the terms are within the limits and the good is not paid with itself. */
    public static boolean isValid(ContractTerms terms) {
        return !terms.good().isEmpty() && !terms.payment().isEmpty() && !terms.good().equals(terms.payment())
                && terms.percent() >= PERCENT_MIN && terms.percent() <= PERCENT_MAX && terms.percent() % PERCENT_STEP == 0
                && terms.max() >= MAX_MIN && terms.max() <= MAX_MAX
                && terms.days() >= DAYS_MIN && terms.days() <= DAYS_MAX
                && terms.paymentCount() >= PAYMENT_MIN && terms.paymentCount() <= PAYMENT_MAX;
    }

    /** How much one delivery brings: the share of the surplus, at most {@code max}. */
    public static int delivery(int surplus, int percent, int max) {
        if (surplus <= 0) {
            return 0;
        }
        return Math.min(max, surplus * percent / 100);
    }

    /** The payment for {@code delivered} items: full for a full delivery, proportionally less otherwise. */
    public static int payment(int delivered, int max, int paymentCount) {
        if (delivered <= 0 || max <= 0) {
            return 0;
        }
        return (int) ((long) Math.min(delivered, max) * paymentCount / max);
    }
}
