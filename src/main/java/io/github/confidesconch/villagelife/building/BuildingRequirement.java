package io.github.confidesconch.villagelife.building;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Minimum requirements a building type has to meet (Minecraft-free, unit tested).
 * Loaded from {@code data/<namespace>/building_type/<type>.json}; missing fields default to 0.
 */
public record BuildingRequirement(int minVolume, int minDoors, int minBeds, int minContainers, int minWorkstations) {

    private static final Codec<Integer> NON_NEGATIVE = Codec.intRange(0, Integer.MAX_VALUE);

    public static final Codec<BuildingRequirement> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            NON_NEGATIVE.optionalFieldOf("min_volume", 1).forGetter(BuildingRequirement::minVolume),
            NON_NEGATIVE.optionalFieldOf("min_doors", 0).forGetter(BuildingRequirement::minDoors),
            NON_NEGATIVE.optionalFieldOf("min_beds", 0).forGetter(BuildingRequirement::minBeds),
            NON_NEGATIVE.optionalFieldOf("min_containers", 0).forGetter(BuildingRequirement::minContainers),
            NON_NEGATIVE.optionalFieldOf("min_workstations", 0).forGetter(BuildingRequirement::minWorkstations)
    ).apply(instance, BuildingRequirement::new));

    /** What the scan found inside a room. */
    public record Counts(int volume, int doors, int beds, int containers, int workstations) {}

    /** Why a room is not a valid building; {@code value} is shown in the message (e.g. the missing minimum). */
    public record Problem(String reasonKey, int value) {
        public static final Codec<Problem> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("reason").forGetter(Problem::reasonKey),
                Codec.INT.optionalFieldOf("value", 0).forGetter(Problem::value)
        ).apply(instance, Problem::new));

        /** Kept for readability at call sites that talk about a missing minimum. */
        public int required() {
            return value;
        }
    }

    /** Empty if all requirements are met, otherwise the first unmet requirement. */
    public Optional<Problem> check(Counts counts) {
        if (counts.volume() < minVolume) {
            return Optional.of(new Problem("villagelife.building.problem.too_small", minVolume));
        }
        if (counts.doors() < minDoors) {
            return Optional.of(new Problem("villagelife.building.problem.no_door", minDoors));
        }
        if (counts.beds() < minBeds) {
            return Optional.of(new Problem("villagelife.building.problem.no_bed", minBeds));
        }
        if (counts.containers() < minContainers) {
            return Optional.of(new Problem("villagelife.building.problem.no_container", minContainers));
        }
        if (counts.workstations() < minWorkstations) {
            return Optional.of(new Problem("villagelife.building.problem.no_workstation", minWorkstations));
        }
        return Optional.empty();
    }
}
