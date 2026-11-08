package io.github.confidesconch.villagelife.village;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Per-village settings the owner can change in the village book (M11c). Stored in the village;
 * missing fields (older saves) get their defaults. {@link #with} validates every change, because the
 * values come from a client.
 */
public record VillageSettings(boolean immigration, boolean autoJobs, int workStart, int workEnd,
                              boolean toolsFromStorage, int foodReserve, boolean strangersMayRead,
                              int herdLimit, int mineDepth, int caravanDays, VillageLaws laws,
                              boolean attacks) {

    public static final int MIN_WORK_START = 4;
    public static final int MAX_WORK_START = 10;
    public static final int MIN_WORK_END = 14;
    public static final int MAX_WORK_END = 20;
    public static final int MAX_FOOD_RESERVE = 64;
    public static final int MAX_NAME_LENGTH = 32;
    /** Adult animals of one kind per work area before herders slaughter (M13c). */
    public static final int MIN_HERD_LIMIT = 2;
    public static final int MAX_HERD_LIMIT = 32;
    /** How deep miners dig below their workplace (M13e). */
    public static final int MIN_MINE_DEPTH = 4;
    public static final int MAX_MINE_DEPTH = 64;
    /** Days between two visits of a trading caravan (M15b). */
    public static final int MIN_CARAVAN_DAYS = 1;
    public static final int MAX_CARAVAN_DAYS = 14;

    public static final VillageSettings DEFAULT = new VillageSettings(true, true, 6, 18, true, 0, true, 8, 16, 3, VillageLaws.DEFAULT, true);

    public static final Codec<VillageSettings> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BOOL.optionalFieldOf("immigration", DEFAULT.immigration).forGetter(VillageSettings::immigration),
            Codec.BOOL.optionalFieldOf("auto_jobs", DEFAULT.autoJobs).forGetter(VillageSettings::autoJobs),
            Codec.intRange(MIN_WORK_START, MAX_WORK_START).optionalFieldOf("work_start", DEFAULT.workStart).forGetter(VillageSettings::workStart),
            Codec.intRange(MIN_WORK_END, MAX_WORK_END).optionalFieldOf("work_end", DEFAULT.workEnd).forGetter(VillageSettings::workEnd),
            Codec.BOOL.optionalFieldOf("tools_from_storage", DEFAULT.toolsFromStorage).forGetter(VillageSettings::toolsFromStorage),
            Codec.intRange(0, MAX_FOOD_RESERVE).optionalFieldOf("food_reserve", DEFAULT.foodReserve).forGetter(VillageSettings::foodReserve),
            Codec.BOOL.optionalFieldOf("strangers_may_read", DEFAULT.strangersMayRead).forGetter(VillageSettings::strangersMayRead),
            Codec.intRange(MIN_HERD_LIMIT, MAX_HERD_LIMIT).optionalFieldOf("herd_limit", DEFAULT.herdLimit).forGetter(VillageSettings::herdLimit),
            Codec.intRange(MIN_MINE_DEPTH, MAX_MINE_DEPTH).optionalFieldOf("mine_depth", DEFAULT.mineDepth).forGetter(VillageSettings::mineDepth),
            Codec.intRange(MIN_CARAVAN_DAYS, MAX_CARAVAN_DAYS).optionalFieldOf("caravan_days", DEFAULT.caravanDays).forGetter(VillageSettings::caravanDays),
            // optional: villages saved before M19b get the default laws (rest day every 7th day)
            VillageLaws.CODEC.optionalFieldOf("laws", VillageLaws.DEFAULT).forGetter(VillageSettings::laws),
            // M20b: bandit attacks at night (from stage "village"); can be switched off
            Codec.BOOL.optionalFieldOf("attacks", DEFAULT.attacks).forGetter(VillageSettings::attacks)
    ).apply(instance, VillageSettings::new));

    /** The settings with other laws (M19b). */
    public VillageSettings withLaws(VillageLaws newLaws) {
        return new VillageSettings(immigration, autoJobs, workStart, workEnd, toolsFromStorage, foodReserve, strangersMayRead,
                herdLimit, mineDepth, caravanDays, newLaws, attacks);
    }

    /** True if the hours are the built-in 6–18, so data pack schedules stay in charge. */
    public boolean usesDefaultHours() {
        return workStart == DEFAULT.workStart && workEnd == DEFAULT.workEnd;
    }

    /**
     * The settings with one value changed, or empty if the key is unknown or the value invalid.
     * Keys: immigration, auto_jobs, work_start, work_end, tools_from_storage, food_reserve, strangers_may_read,
     * herd_limit, mine_depth, caravan_days.
     */
    public Optional<VillageSettings> with(String key, String value) {
        try {
            return Optional.ofNullable(switch (key) {
                case "immigration" -> new VillageSettings(bool(value), autoJobs, workStart, workEnd, toolsFromStorage, foodReserve, strangersMayRead, herdLimit, mineDepth, caravanDays, laws, attacks);
                case "auto_jobs" -> new VillageSettings(immigration, bool(value), workStart, workEnd, toolsFromStorage, foodReserve, strangersMayRead, herdLimit, mineDepth, caravanDays, laws, attacks);
                case "work_start" -> new VillageSettings(immigration, autoJobs, range(value, MIN_WORK_START, MAX_WORK_START), workEnd, toolsFromStorage, foodReserve, strangersMayRead, herdLimit, mineDepth, caravanDays, laws, attacks);
                case "work_end" -> new VillageSettings(immigration, autoJobs, workStart, range(value, MIN_WORK_END, MAX_WORK_END), toolsFromStorage, foodReserve, strangersMayRead, herdLimit, mineDepth, caravanDays, laws, attacks);
                case "tools_from_storage" -> new VillageSettings(immigration, autoJobs, workStart, workEnd, bool(value), foodReserve, strangersMayRead, herdLimit, mineDepth, caravanDays, laws, attacks);
                case "food_reserve" -> new VillageSettings(immigration, autoJobs, workStart, workEnd, toolsFromStorage, range(value, 0, MAX_FOOD_RESERVE), strangersMayRead, herdLimit, mineDepth, caravanDays, laws, attacks);
                case "strangers_may_read" -> new VillageSettings(immigration, autoJobs, workStart, workEnd, toolsFromStorage, foodReserve, bool(value), herdLimit, mineDepth, caravanDays, laws, attacks);
                case "herd_limit" -> new VillageSettings(immigration, autoJobs, workStart, workEnd, toolsFromStorage, foodReserve, strangersMayRead, range(value, MIN_HERD_LIMIT, MAX_HERD_LIMIT), mineDepth, caravanDays, laws, attacks);
                case "mine_depth" -> new VillageSettings(immigration, autoJobs, workStart, workEnd, toolsFromStorage, foodReserve, strangersMayRead, herdLimit, range(value, MIN_MINE_DEPTH, MAX_MINE_DEPTH), caravanDays, laws, attacks);
                case "attacks" -> new VillageSettings(immigration, autoJobs, workStart, workEnd, toolsFromStorage, foodReserve, strangersMayRead, herdLimit, mineDepth, caravanDays, laws, bool(value));
                case "caravan_days" -> new VillageSettings(immigration, autoJobs, workStart, workEnd, toolsFromStorage, foodReserve, strangersMayRead, herdLimit, mineDepth, range(value, MIN_CARAVAN_DAYS, MAX_CARAVAN_DAYS), laws, attacks);
                default -> null;
            });
        } catch (IllegalArgumentException invalid) {
            return Optional.empty();
        }
    }

    /**
     * A village name typed by a player: trimmed, formatting codes removed, at most 32 characters.
     * Empty means "use the default name".
     */
    public static String cleanName(String name) {
        return io.github.confidesconch.villagelife.core.util.TextRules.cleanName(name, MAX_NAME_LENGTH);
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
