package io.github.confidesconch.villagelife.resident.schedule;

import com.mojang.serialization.Codec;

import net.minecraft.util.StringRepresentable;

/**
 * What a resident is doing on a large scale, taken from its daily schedule (M7).
 * Goals check the current activity before they start.
 */
public enum Activity implements StringRepresentable {
    /** Walk to the own bed and sleep in it; homeless residents stand still. */
    SLEEP("sleep"),
    /** Stay close to the own bed (morning); homeless residents spend free time instead. */
    HOME("home"),
    /** Work at the workplace; until professions exist (M8) residents walk around the village. */
    WORK("work"),
    /** Lunch break: meet at the village center (real food follows with storage, M9/M10). */
    MEAL("meal"),
    /** Free time: walk around the village. */
    LEISURE("leisure");

    public static final Codec<Activity> CODEC = StringRepresentable.fromEnum(Activity::values);

    private final String name;

    Activity(String name) {
        this.name = name;
    }

    public String translationKey() {
        return "villagelife.activity." + name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
