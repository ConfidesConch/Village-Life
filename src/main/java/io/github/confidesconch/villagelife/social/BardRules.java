package io.github.confidesconch.villagelife.social;

import java.util.Random;

/**
 * Pure rules of the bard (M23, owner's choice "jukebox, square by day and tavern in the evening, +5 mood"):
 * where he plays, how his tunes sound when he has no music disc, and how long he rests between songs.
 */
public final class BardRules {
    private BardRules() {}

    /** From this minute of the day the bard plays in the tavern (same as the guests' evening). */
    public static final int EVENING_FROM_MINUTE = 17 * 60;
    /** Residents this close hear the music. */
    public static final double HEAR_DISTANCE = 16.0;
    /** Mood bonus for hearing music, until the next morning; counts once however often. */
    public static final int MUSIC_BONUS = 5;
    /** Rest between two songs. */
    public static final int PAUSE_TICKS = 600;
    /** Length of a tune played without a disc. */
    public static final int TUNE_TICKS = 1200;
    /** Ticks per beat of such a tune. */
    public static final int BEAT_TICKS = 5;
    /** The bard carries at most this many different discs and changes between them. */
    public static final int MAX_DISCS = 3;

    /** A note of the pentatonic scale, as note block steps (0–24, 12 = middle); -1 is a rest. */
    private static final int[] SCALE = {6, 8, 10, 13, 15, 18, 20, 22};

    /** True if the bard plays in the tavern now; otherwise (also without a tavern) on the square. */
    public static boolean playsInTavern(int minuteOfDay, boolean hasTavern) {
        return hasTavern && minuteOfDay >= EVENING_FROM_MINUTE;
    }

    /**
     * A tune without a disc: one note block step (0–24) or -1 (rest) per beat. A short phrase of 16 beats
     * is made up from the seed and repeated with small changes, so it sounds like a melody, not noise.
     */
    public static int[] tune(long seed, int beats) {
        Random random = new Random(seed);
        int[] phrase = new int[16];
        int step = random.nextInt(SCALE.length);
        for (int i = 0; i < phrase.length; i++) {
            if (i % 4 == 3 && random.nextInt(3) == 0) {
                phrase[i] = -1;
                continue;
            }
            step = Math.max(0, Math.min(SCALE.length - 1, step + random.nextInt(5) - 2));
            phrase[i] = SCALE[step];
        }
        phrase[phrase.length - 1] = SCALE[0]; // every phrase comes home
        int[] tune = new int[beats];
        for (int i = 0; i < beats; i++) {
            int note = phrase[i % phrase.length];
            boolean variation = (i / phrase.length) % 2 == 1 && i % 8 == 2 && note >= 0;
            tune[i] = variation ? Math.min(24, note + 2) : note;
        }
        return tune;
    }

    /** Pitch of a note block step, as Minecraft plays it (0.5 to 2.0). */
    public static float pitch(int note) {
        return (float) Math.pow(2.0, (note - 12) / 12.0);
    }
}
