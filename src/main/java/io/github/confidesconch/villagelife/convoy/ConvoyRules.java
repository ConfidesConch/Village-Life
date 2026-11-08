package io.github.confidesconch.villagelife.convoy;

/**
 * How dangerous the road between two villages is (M20c, owner's choice "1b 2b 3a 4a", Minecraft-free, unit
 * tested). Long roads are more dangerous; the guards of both villages and the escorts sent with a
 * convoy make them safer. A raid takes 25–75 % of the goods – less with more escorts.
 */
public final class ConvoyRules {
    private ConvoyRules() {}

    public static final int MAX_ESCORTS = 3;
    private static final double MIN_CHANCE = 0.02;
    private static final double MAX_CHANCE = 0.8;

    /** Chance that a convoy is raided on its way. {@code defenderPower}: guards of both villages. */
    public static double raidChance(double distance, double defenderPower, int escorts) {
        double risk = 0.1 + Math.min(distance, 1500.0) / 1000.0 * 0.4;
        double protection = Math.min(defenderPower, 10.0) * 0.02 + escorts * 0.12;
        return Math.max(MIN_CHANCE, Math.min(MAX_CHANCE, risk - protection));
    }

    /** Safety of the road in percent, as shown in the book. */
    public static int safetyPercent(double raidChance) {
        return (int) Math.round(100 * (1 - raidChance));
    }

    /** "safe" (85 % and more), "medium" (60 % and more) or "dangerous". */
    public static String safetyLevel(int percent) {
        return percent >= 85 ? "safe" : percent >= 60 ? "medium" : "dangerous";
    }

    /** Share of the goods the bandits take: 75 % without help, down to 25 %. */
    public static double lossShare(int escorts, double defenderPower) {
        return Math.max(0.25, Math.min(0.75, 0.75 - escorts * 0.15 - Math.min(defenderPower, 10.0) * 0.02));
    }

    /** Items lost from {@code count}: at least one if anything is there. */
    public static int lost(int count, double share) {
        return count <= 0 ? 0 : Math.max(1, Math.min(count, (int) Math.round(count * share)));
    }

    /** Chance that escorts fight a raid off far from any player (no loss): 25 % per escort, at most 75 %. */
    public static double defendChance(int escorts) {
        return Math.min(0.75, escorts * 0.25);
    }

    /** Bandits of a raid on a convoy someone can see. */
    public static int bandits(int escorts) {
        return 2 + escorts;
    }

    /** Where on its way (0–1) a raid happens: somewhere in the middle part of the road. */
    public static double raidPoint(double random) {
        return 0.2 + random * 0.6;
    }
}
