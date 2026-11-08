package io.github.confidesconch.villagelife.village;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * The laws of a village (M19b): the ones in force and, after a change, the new ones that apply from the
 * next morning ({@code nextFromDay}, owner's choice "3a"). Days count from midnight
 * ({@code LawRules.day} in {@code resident.schedule}). Nothing has to be switched over: {@link #effective}
 * simply picks the right set for a day.
 */
public record VillageLaws(LawSet current, Optional<LawSet> next, long nextFromDay) {

    public static final VillageLaws DEFAULT = new VillageLaws(LawSet.DEFAULT, Optional.empty(), 0);

    public static final Codec<VillageLaws> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            LawSet.CODEC.optionalFieldOf("current", LawSet.DEFAULT).forGetter(VillageLaws::current),
            LawSet.CODEC.optionalFieldOf("next").forGetter(VillageLaws::next),
            Codec.LONG.optionalFieldOf("next_from_day", 0L).forGetter(VillageLaws::nextFromDay)
    ).apply(instance, VillageLaws::new));

    /** The laws in force on {@code day}. */
    public LawSet effective(long day) {
        return next.isPresent() && day >= nextFromDay ? next.get() : current;
    }

    /** The laws as the owner set them last (in force now or from tomorrow) – shown in the book. */
    public LawSet planned() {
        return next.orElse(current);
    }

    /** True if changed laws are waiting for the next morning. */
    public boolean isPending(long day) {
        return next.isPresent() && day < nextFromDay;
    }

    /**
     * The laws after the owner changed one value on {@code today}; the change applies from tomorrow.
     * Empty if the key or value is invalid.
     */
    public Optional<VillageLaws> propose(String key, String value, long today) {
        LawSet inForce = effective(today);
        return planned().with(key, value).map(changed -> changed.equals(inForce)
                ? new VillageLaws(inForce, Optional.empty(), 0) // changed back: nothing waits any more
                : new VillageLaws(inForce, Optional.of(changed), today + 1));
    }
}
