package io.github.confidesconch.villagelife.profession.work;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import io.github.confidesconch.villagelife.building.AreaService;
import io.github.confidesconch.villagelife.building.Building;
import io.github.confidesconch.villagelife.building.BuildingType;
import io.github.confidesconch.villagelife.building.PastureKind;
import io.github.confidesconch.villagelife.village.Village;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.animal.Animal;

/**
 * Which pastures the herders look after (M28b, owner's choice "set at the pasture sign"): a pasture can be
 * for one kind of animal ({@link PastureKind}); shepherds keep the sheep pastures, breeders every kind of
 * pasture (owner's choice "B"), both also the nearest open pasture. Also where a misplaced animal belongs.
 */
final class HerdPastures {
    private HerdPastures() {}

    /**
     * The herd areas of a herder's workplace (also used by the butcher, M27): every pasture set to an
     * animal this herder keeps (sheep: shepherd; every kind: breeder) plus the nearest pasture for
     * all animals (M21d). M28b: zones drawn on the map no longer count (owner: "only by sign").
     */
    static List<HerdArea> areasOf(ServerLevel level, Village village, Building workplace, String profession) {
        boolean shepherd = ShepherdGoal.PROFESSION.equals(profession);
        List<HerdArea> areas = new ArrayList<>();
        Building open = null;
        double openDistance = Double.MAX_VALUE;
        for (Building pasture : village.buildings()) {
            if (pasture.type() != BuildingType.PASTURE || !pasture.isValid()) {
                continue;
            }
            PastureKind kind = PastureKind.of(level, pasture);
            if (kind == PastureKind.ALL) {
                double distance = pasture.center().distSqr(workplace.center());
                if (distance < openDistance) {
                    open = pasture;
                    openDistance = distance;
                }
            } else if (!shepherd || kind.forShepherd()) { // M28b "B": the breeder looks after every kind
                areas.add(pastureArea(pasture, kind));
            }
        }
        if (open != null) {
            areas.add(pastureArea(open, PastureKind.ALL));
        }
        return areas;
    }

    /** A marked pasture as a herd area; animals are tagged with the pasture, so every herder agrees on them. */
    private static HerdArea pastureArea(Building pasture, PastureKind kind) {
        return new HerdArea(AreaService.asZone(pasture), pasture.center().getY(), pasture.id().toString(), kind);
    }

    /** M28b: the kinds of animal that have a pasture of their own in the village (they leave open pastures). */
    static Set<PastureKind> ownPastures(ServerLevel level, Village village) {
        Set<PastureKind> kinds = EnumSet.noneOf(PastureKind.class);
        if (village != null) {
            for (Building pasture : village.buildings()) {
                if (pasture.type() == BuildingType.PASTURE && pasture.isValid()) {
                    kinds.add(PastureKind.of(level, pasture));
                }
            }
        }
        kinds.remove(PastureKind.ALL);
        return kinds;
    }

    /** M28b: where a misplaced animal belongs – a pasture of its kind (or an open one) – or null. */
    static HerdArea homeFor(ServerLevel level, Village village, Animal animal, HerdArea from) {
        if (village == null) {
            return null;
        }
        HerdArea open = null;
        for (Building pasture : village.buildings()) {
            if (pasture.type() != BuildingType.PASTURE || !pasture.isValid() || pasture.id().toString().equals(from.owner())) {
                continue;
            }
            PastureKind kind = PastureKind.of(level, pasture);
            if (kind != PastureKind.ALL && kind.accepts(animal)) {
                return pastureArea(pasture, kind);
            }
            if (kind == PastureKind.ALL && open == null) {
                open = pastureArea(pasture, kind);
            }
        }
        return open;
    }

}
