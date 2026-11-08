package io.github.confidesconch.villagelife.profession.work;

/**
 * What a herder (shepherd M13c, breeder M13d) does next with the animals of one kind in its work area
 * (Minecraft-free, unit tested). The limit counts adult animals; babies never get slaughtered, but
 * they count when deciding whether to breed, so the herd does not overshoot the limit once they grow up.
 */
public final class HerdRules {
    private HerdRules() {}

    public enum Task { CULL, BREED, NONE }

    /**
     * @param adults       grown-up animals in the area
     * @param young        babies in the area
     * @param readyToBreed adults that can breed right now (not in love, no breeding cooldown)
     * @param limit        adult animals of one kind the village wants per area
     * @param hasFeed      true if the herder has (or can get) feed
     */
    public static Task next(int adults, int young, int readyToBreed, int limit, boolean hasFeed) {
        if (adults > limit) {
            return Task.CULL;
        }
        // one pair makes one baby
        if (hasFeed && readyToBreed >= 2 && adults + young + 1 <= limit) {
            return Task.BREED;
        }
        return Task.NONE;
    }
}
