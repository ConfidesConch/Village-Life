package io.github.confidesconch.villagelife.waypoint;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * A named, coloured point a player marked on the map (M11e). Belongs to one player and one
 * dimension; {@code color} is an index into {@code core.util.MarkerColors}. Shared waypoints (M11g)
 * are also shown to the owner's friends.
 */
public record Waypoint(int id, String name, int color, String dimension, int x, int y, int z, boolean visible,
                       boolean shared) {

    public static final Codec<Waypoint> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("id").forGetter(Waypoint::id),
            Codec.STRING.fieldOf("name").forGetter(Waypoint::name),
            Codec.INT.optionalFieldOf("color", 0).forGetter(Waypoint::color),
            Codec.STRING.fieldOf("dimension").forGetter(Waypoint::dimension),
            Codec.INT.fieldOf("x").forGetter(Waypoint::x),
            Codec.INT.fieldOf("y").forGetter(Waypoint::y),
            Codec.INT.fieldOf("z").forGetter(Waypoint::z),
            Codec.BOOL.optionalFieldOf("visible", true).forGetter(Waypoint::visible),
            // optional: waypoints saved before M11g are private
            Codec.BOOL.optionalFieldOf("shared", false).forGetter(Waypoint::shared)
    ).apply(instance, Waypoint::new));

    public Waypoint withDetails(String newName, int newColor, boolean newShared) {
        return new Waypoint(id, newName, newColor, dimension, x, y, z, visible, newShared);
    }

    public Waypoint withVisible(boolean newVisible) {
        return new Waypoint(id, name, color, dimension, x, y, z, newVisible, shared);
    }
}
