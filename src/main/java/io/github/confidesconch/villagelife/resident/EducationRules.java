package io.github.confidesconch.villagelife.resident;

/**
 * Pure rules of school and education (M18c, owner's choice "2a 3a"): children go to school in the
 * morning of their work time; every hour in school gives {@link #POINTS_PER_HOUR} education points (so
 * five school mornings make 100 %). Educated adults work up to {@link #MAX_BONUS} faster.
 */
public final class EducationRules {
    private EducationRules() {}

    public static final int MAX = 100;
    public static final int POINTS_PER_HOUR = 5;
    public static final long HOUR_TICKS = 1000;
    /** +20 % work speed at full education. */
    public static final double MAX_BONUS = 0.2;
    /** School ends at noon. */
    public static final int SCHOOL_UNTIL_MINUTE = 12 * 60;

    /** Work speed factor of an adult with this education: 1.0 … 1.2. */
    public static double speedFactor(int education) {
        int clamped = Math.max(0, Math.min(MAX, education));
        return 1.0 + MAX_BONUS * clamped / MAX;
    }

    /** Whether a child is in school now: during the work time it would have as an adult, before noon. */
    public static boolean isSchoolTime(boolean child, boolean workTime, int minuteOfDay) {
        return child && workTime && minuteOfDay < SCHOOL_UNTIL_MINUTE;
    }

    /** Education after one more hour in school. */
    public static int afterLesson(int education) {
        return Math.min(MAX, education + POINTS_PER_HOUR);
    }
}
