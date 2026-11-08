package io.github.confidesconch.villagelife.resident.ai;

/**
 * Notices when a resident on its way does not get on (M21a, owner's choice "1a", Minecraft-free and unit
 * tested): {@link Result#STUCK} after three seconds with hardly any movement – look for another way –
 * and {@link Result#RESCUE} after 30 seconds without getting closer to the target – put it a bit further
 * along its way (if nobody watches).
 */
public final class StuckTracker {
    /** How the walk is going. */
    public enum Result { OK, STUCK, RESCUE }

    public static final long CHECK_TICKS = 60;
    public static final double MIN_MOVE = 0.6;
    public static final long RESCUE_TICKS = 600;
    /** Getting this much closer to the target counts as progress. */
    public static final double PROGRESS = 2.0;

    private long checkAt;
    private double lastX;
    private double lastY;
    private double lastZ;
    private long progressAt;
    private double bestDistance;

    /** A new walk starts (or the resident was put somewhere else). */
    public void reset(long now, double x, double y, double z, double distance) {
        checkAt = now + CHECK_TICKS;
        lastX = x;
        lastY = y;
        lastZ = z;
        progressAt = now;
        bestDistance = distance;
    }

    /** Gives the rescue another 5 seconds (somebody was watching). */
    public void postponeRescue(long now) {
        progressAt = now - RESCUE_TICKS + 100;
    }

    /** Called regularly while the resident walks; {@code distance}: to its target. */
    public Result update(long now, double x, double y, double z, double distance) {
        if (distance < bestDistance - PROGRESS) {
            bestDistance = distance;
            progressAt = now;
        }
        if (now - progressAt >= RESCUE_TICKS) {
            return Result.RESCUE;
        }
        if (now < checkAt) {
            return Result.OK;
        }
        double dx = x - lastX;
        double dy = y - lastY;
        double dz = z - lastZ;
        boolean moved = dx * dx + dy * dy + dz * dz >= MIN_MOVE * MIN_MOVE;
        checkAt = now + CHECK_TICKS;
        lastX = x;
        lastY = y;
        lastZ = z;
        return moved ? Result.OK : Result.STUCK;
    }
}
