package io.github.confidesconch.villagelife.village;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.github.confidesconch.villagelife.core.util.MarkerColors;
import io.github.confidesconch.villagelife.core.util.TextRules;

/**
 * A named, coloured rectangle the owner drew on the village map (M11e), e.g. "Market" or "Fields".
 * Only a marking – it protects nothing. Corners are inclusive block coordinates; {@code color} is an
 * index into {@link MarkerColors}. The static methods are free of Minecraft classes (unit tested).
 */
public record VillageZone(int id, String name, int color, int minX, int minZ, int maxX, int maxZ) {
    public static final int MAX_NAME_LENGTH = 24;

    public static final Codec<VillageZone> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("id").forGetter(VillageZone::id),
            Codec.STRING.fieldOf("name").forGetter(VillageZone::name),
            Codec.INT.optionalFieldOf("color", 0).forGetter(VillageZone::color),
            Codec.INT.fieldOf("min_x").forGetter(VillageZone::minX),
            Codec.INT.fieldOf("min_z").forGetter(VillageZone::minZ),
            Codec.INT.fieldOf("max_x").forGetter(VillageZone::maxX),
            Codec.INT.fieldOf("max_z").forGetter(VillageZone::maxZ)
    ).apply(instance, VillageZone::new));

    /**
     * Adds a zone from two corners in any order, cut to the square around the village area.
     * Empty if the limit is reached or the rectangle lies completely outside the village.
     */
    public static Optional<List<VillageZone>> add(List<VillageZone> zones, VillageArea area, String name, int color,
                                                  int x1, int z1, int x2, int z2, int limit) {
        if (zones.size() >= limit) {
            return Optional.empty();
        }
        int minX = Math.max(Math.min(x1, x2), area.centerX() - area.radius());
        int maxX = Math.min(Math.max(x1, x2), area.centerX() + area.radius());
        int minZ = Math.max(Math.min(z1, z2), area.centerZ() - area.radius());
        int maxZ = Math.min(Math.max(z1, z2), area.centerZ() + area.radius());
        if (minX > maxX || minZ > maxZ) {
            return Optional.empty();
        }
        int id = zones.stream().mapToInt(VillageZone::id).max().orElse(0) + 1;
        List<VillageZone> result = new ArrayList<>(zones);
        result.add(new VillageZone(id, nameOrDefault(name, id), MarkerColors.clamp(color), minX, minZ, maxX, maxZ));
        return Optional.of(List.copyOf(result));
    }

    /** Renames and recolours zone {@code id}. */
    public static Optional<List<VillageZone>> edit(List<VillageZone> zones, int id, String name, int color) {
        if (zones.stream().noneMatch(z -> z.id() == id)) {
            return Optional.empty();
        }
        return Optional.of(zones.stream()
                .map(z -> z.id() == id ? new VillageZone(id, nameOrDefault(name, id), MarkerColors.clamp(color),
                        z.minX(), z.minZ(), z.maxX(), z.maxZ()) : z)
                .toList());
    }

    public static Optional<List<VillageZone>> remove(List<VillageZone> zones, int id) {
        if (zones.stream().noneMatch(z -> z.id() == id)) {
            return Optional.empty();
        }
        return Optional.of(zones.stream().filter(z -> z.id() != id).toList());
    }

    private static String nameOrDefault(String name, int id) {
        String cleaned = TextRules.cleanName(name, MAX_NAME_LENGTH);
        return cleaned.isEmpty() ? "#" + id : cleaned;
    }
}
