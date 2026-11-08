package io.github.confidesconch.villagelife.village;

/**
 * Pure rules of village growth (M16, owner's choice "1a 2a 3a 4a"): three stages – settlement (0),
 * village (1), town (2). A village rises automatically once it meets the next stage's requirements and
 * never falls back. Higher stages only bring advantages (more residents, larger radius, more contracts).
 */
public final class VillageStages {
    private VillageStages() {}

    public static final int SETTLEMENT = 0;
    public static final int VILLAGE = 1;
    public static final int TOWN = 2;
    public static final int MAX_STAGE = TOWN;
    /** Blocks the radius grows with each stage (if no other village is in the way). */
    public static final int RADIUS_GROWTH = 16;

    /** What a village needs to reach a stage. {@code satisfaction} in percent; {@code guards}: defenders (M20a). */
    public record Requirements(int residents, int satisfaction, int professions, boolean storage, boolean market, int guards) {}

    /** What a village has now. {@code satisfaction} is -1 if unknown (no resident loaded). */
    public record Status(int residents, int satisfaction, int professions, boolean storage, boolean market, int guards) {}

    /** Requirements to reach {@code stage} (1 or 2). */
    public static Requirements requirementsFor(int stage) {
        return stage >= TOWN
                ? new Requirements(30, 60, 8, true, true, 4)
                : new Requirements(10, 50, 3, true, false, 2); // M20a: a village needs 2 defenders
    }

    public static boolean meets(Requirements r, Status s) {
        return s.residents() >= r.residents()
                && s.satisfaction() >= 0 && s.satisfaction() >= r.satisfaction()
                && s.professions() >= r.professions()
                && (!r.storage() || s.storage())
                && (!r.market() || s.market())
                && s.guards() >= r.guards();
    }

    /** The stage after {@code stage} if the village meets its requirements, else {@code stage}. Never lower. */
    public static int next(int stage, Status status) {
        if (stage >= MAX_STAGE) {
            return stage;
        }
        return meets(requirementsFor(stage + 1), status) ? stage + 1 : stage;
    }

    /** Most residents a village of this stage may have (the server config can lower it further). */
    public static int maxResidents(int stage) {
        return switch (stage) {
            case SETTLEMENT -> 20;
            case VILLAGE -> 50;
            default -> 150;
        };
    }

    /** Trade contracts (offers included) a village of this stage may have. */
    public static int maxContracts(int stage) {
        return switch (stage) {
            case SETTLEMENT -> 8;
            case VILLAGE -> 12;
            default -> 16;
        };
    }
}
