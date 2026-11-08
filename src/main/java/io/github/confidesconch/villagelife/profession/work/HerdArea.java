package io.github.confidesconch.villagelife.profession.work;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import io.github.confidesconch.villagelife.resident.ResidentEntity;
import io.github.confidesconch.villagelife.village.VillageZone;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

/**
 * The work area of a herder (M13c/M13d) as seen in the world: the zone's rectangle from 8 blocks below
 * to 8 blocks above the workplace's marker. Finds the animals inside (and marks them as belonging to
 * the workplace), escaped ones of the herd around it, and loose items.
 */
record HerdArea(VillageZone zone, int markerY, String owner) {
    /** How far above and below the workplace's marker animals still count as "in the area". */
    private static final int HEIGHT = 8;
    /** Escaped animals are looked for this far around the work area. */
    private static final int ESCAPE_SEARCH = 32;
    /** Saved on every animal seen in the work area: the id of the workplace it belongs to. */
    private static final String HERD_TAG = "villagelife_herd";

    /** Animals of one kind in the area. */
    static final class Herd<T extends Animal> {
        final List<T> adults = new ArrayList<>();
        final List<T> ready = new ArrayList<>();
        int young;
    }

    AABB box() {
        return new AABB(zone.minX(), markerY - HEIGHT, zone.minZ(), zone.maxX() + 1, markerY + HEIGHT, zone.maxZ() + 1);
    }

    boolean contains(Entity entity) {
        return entity.getBlockX() >= zone.minX() && entity.getBlockX() <= zone.maxX()
                && entity.getBlockZ() >= zone.minZ() && entity.getBlockZ() <= zone.maxZ();
    }

    /** Ground position in the middle of the area (where escaped animals are led to). */
    BlockPos middle(ServerLevel level) {
        int x = (zone.minX() + zone.maxX()) / 2;
        int z = (zone.minZ() + zone.maxZ()) / 2;
        return new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
    }

    /** The animals in the area by kind; each one is marked as belonging to the workplace. */
    <T extends Animal> Map<EntityType<?>, Herd<T>> herds(ServerLevel level, Class<T> type, Predicate<T> filter) {
        Map<EntityType<?>, Herd<T>> herds = new LinkedHashMap<>();
        for (T animal : level.getEntitiesOfClass(type, box(), a -> a.isAlive() && filter.test(a))) {
            leashedByPlayer(animal); // drops a leftover resident leash
            animal.getPersistentData().putString(HERD_TAG, owner);
            Herd<T> herd = herds.computeIfAbsent(animal.getType(), kind -> new Herd<>());
            if (animal.isBaby()) {
                herd.young++;
            } else {
                herd.adults.add(animal);
                if (animal.getAge() == 0 && animal.canFallInLove()) {
                    herd.ready.add(animal);
                }
            }
        }
        return herds;
    }

    /** An animal of this workplace's herd that got out of the area and is not on a leash, or null. */
    <T extends Animal> T escaped(ServerLevel level, Class<T> type, Predicate<T> filter) {
        List<T> escaped = level.getEntitiesOfClass(type, box().inflate(ESCAPE_SEARCH, 0, ESCAPE_SEARCH),
                animal -> animal.isAlive() && filter.test(animal) && !leashedByPlayer(animal) && !contains(animal)
                        && owner.equals(animal.getPersistentData().getStringOr(HERD_TAG, "")));
        return escaped.isEmpty() ? null : escaped.getFirst();
    }

    /**
     * True if a player (or anything but a resident) holds the animal on a leash: then it is not "escaped".
     * Leashes from residents are left over from an earlier version (M13d) and are removed here.
     */
    private static boolean leashedByPlayer(Animal animal) {
        if (animal.isLeashed() && animal.getLeashHolder() instanceof ResidentEntity) {
            animal.removeLeash();
        }
        return animal.isLeashed();
    }

    /** An item lying in the area that matches, or null. */
    ItemEntity loose(ServerLevel level, Predicate<ItemEntity> wanted) {
        List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, box(), item -> item.isAlive() && wanted.test(item));
        return items.isEmpty() ? null : items.getFirst();
    }
}
