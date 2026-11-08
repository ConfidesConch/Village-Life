package io.github.confidesconch.villagelife.village;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * The values of a village's laws (M19b, owner's choice "1a 2a 3a 4a"): rest day every {@code restInterval}
 * days, curfew from {@code curfewHour} o'clock, long evenings ending work {@code longEveningHours} earlier.
 * {@link #with} validates every change, because the values come from a client.
 */
public record LawSet(boolean restDay, int restInterval, boolean curfew, int curfewHour,
                     boolean longEvenings, int longEveningHours) {

    public static final int MIN_REST_INTERVAL = 3;
    public static final int MAX_REST_INTERVAL = 14;
    public static final int MIN_CURFEW_HOUR = 18;
    public static final int MAX_CURFEW_HOUR = 23;
    public static final int MIN_LONG_EVENING_HOURS = 1;
    public static final int MAX_LONG_EVENING_HOURS = 3;

    /** From the start: a rest day every 7th day; curfew (20:00) and long evenings (2 h) switched off. */
    public static final LawSet DEFAULT = new LawSet(true, 7, false, 20, false, 2);

    public static final Codec<LawSet> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BOOL.optionalFieldOf("rest_day", DEFAULT.restDay).forGetter(LawSet::restDay),
            Codec.intRange(MIN_REST_INTERVAL, MAX_REST_INTERVAL).optionalFieldOf("rest_interval", DEFAULT.restInterval)
                    .forGetter(LawSet::restInterval),
            Codec.BOOL.optionalFieldOf("curfew", DEFAULT.curfew).forGetter(LawSet::curfew),
            Codec.intRange(MIN_CURFEW_HOUR, MAX_CURFEW_HOUR).optionalFieldOf("curfew_hour", DEFAULT.curfewHour)
                    .forGetter(LawSet::curfewHour),
            Codec.BOOL.optionalFieldOf("long_evenings", DEFAULT.longEvenings).forGetter(LawSet::longEvenings),
            Codec.intRange(MIN_LONG_EVENING_HOURS, MAX_LONG_EVENING_HOURS).optionalFieldOf("long_evening_hours",
                    DEFAULT.longEveningHours).forGetter(LawSet::longEveningHours)
    ).apply(instance, LawSet::new));

    /**
     * The laws with one value changed, or empty if the key is unknown or the value invalid. Keys: rest_day,
     * rest_interval, curfew, curfew_hour, long_evenings, long_evening_hours.
     */
    public Optional<LawSet> with(String key, String value) {
        try {
            return Optional.ofNullable(switch (key) {
                case "rest_day" -> new LawSet(bool(value), restInterval, curfew, curfewHour, longEvenings, longEveningHours);
                case "rest_interval" -> new LawSet(restDay, range(value, MIN_REST_INTERVAL, MAX_REST_INTERVAL), curfew,
                        curfewHour, longEvenings, longEveningHours);
                case "curfew" -> new LawSet(restDay, restInterval, bool(value), curfewHour, longEvenings, longEveningHours);
                case "curfew_hour" -> new LawSet(restDay, restInterval, curfew, range(value, MIN_CURFEW_HOUR, MAX_CURFEW_HOUR),
                        longEvenings, longEveningHours);
                case "long_evenings" -> new LawSet(restDay, restInterval, curfew, curfewHour, bool(value), longEveningHours);
                case "long_evening_hours" -> new LawSet(restDay, restInterval, curfew, curfewHour, longEvenings,
                        range(value, MIN_LONG_EVENING_HOURS, MAX_LONG_EVENING_HOURS));
                default -> null;
            });
        } catch (IllegalArgumentException invalid) {
            return Optional.empty();
        }
    }

    private static boolean bool(String value) {
        return switch (value) {
            case "true" -> true;
            case "false" -> false;
            default -> throw new IllegalArgumentException(value);
        };
    }

    private static int range(String value, int min, int max) {
        int number = Integer.parseInt(value); // NumberFormatException is an IllegalArgumentException
        if (number < min || number > max) {
            throw new IllegalArgumentException(value);
        }
        return number;
    }
}
