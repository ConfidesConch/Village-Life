package io.github.confidesconch.villagelife.building;

import com.mojang.serialization.Codec;

import net.minecraft.util.StringRepresentable;

/**
 * Kinds of buildings a marker can register (M21d: also fenced fields and pastures). The list is fixed (it is stored in the marker's block
 * state); the requirements come from data packs via {@link BuildingRequirements}.
 */
public enum BuildingType implements StringRepresentable {
    HOUSE("house", new BuildingRequirement(12, 1, 1, 0, 0)),
    STORAGE("storage", new BuildingRequirement(8, 1, 0, 1, 0)),
    WORKPLACE("workplace", new BuildingRequirement(8, 1, 0, 0, 1)),
    /** M21d: a fenced field under open sky; "volume" is its area in columns. */
    FIELD("field", new BuildingRequirement(9, 0, 0, 0, 0)),
    /** M21d: a fenced pasture for the herders; "volume" is its area in columns. */
    PASTURE("pasture", new BuildingRequirement(16, 0, 0, 0, 0));

    public static final Codec<BuildingType> CODEC = StringRepresentable.fromEnum(BuildingType::values);

    private final String name;
    private final BuildingRequirement defaultRequirement;

    BuildingType(String name, BuildingRequirement defaultRequirement) {
        this.name = name;
        this.defaultRequirement = defaultRequirement;
    }

    /** True for fenced open areas (field, pasture) instead of rooms (M21d). */
    public boolean isArea() {
        return this == FIELD || this == PASTURE;
    }

    /** Built-in fallback, used when no data pack file exists for this type. */
    public BuildingRequirement defaultRequirement() {
        return defaultRequirement;
    }

    /** Current requirement (data pack value or built-in default). */
    public BuildingRequirement requirement() {
        return BuildingRequirements.get(this);
    }

    public String translationKey() {
        return "villagelife.building_type." + name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
