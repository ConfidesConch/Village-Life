package io.github.confidesconch.villagelife.profession.work;

import java.util.Optional;

import io.github.confidesconch.villagelife.building.AreaService;
import io.github.confidesconch.villagelife.building.Building;
import io.github.confidesconch.villagelife.building.BuildingType;
import io.github.confidesconch.villagelife.village.Village;

/**
 * Where a profession works outside its workplace (M28b, owner's wish: only by sign, no zones drawn on the
 * map any more): herders on the marked pastures, the miner in the nearest marked quarry or tunnel (M28d).
 */
public final class WorkAreas {
    private WorkAreas() {}

    /**
     * The marked area a worker of {@code profession} at {@code workplace} uses (nearest one), if any; the
     * miner takes the nearest quarry or tunnel (M28d).
     */
    public static Optional<Building> of(Village village, Building workplace, String profession) {
        BuildingType type = typeFor(profession);
        if (type == null || workplace == null) {
            return Optional.empty();
        }
        Optional<Building> area = AreaService.nearest(village, type, workplace.center());
        if (type != BuildingType.QUARRY) {
            return area;
        }
        Optional<Building> tunnel = AreaService.nearest(village, BuildingType.TUNNEL, workplace.center());
        if (area.isEmpty() || tunnel.isEmpty()) {
            return area.isPresent() ? area : tunnel;
        }
        return tunnel.get().center().distSqr(workplace.center()) < area.get().center().distSqr(workplace.center()) ? tunnel : area;
    }

    /** Pasture for shepherds and breeders, quarry for miners, otherwise null. */
    public static BuildingType typeFor(String profession) {
        if (ShepherdGoal.PROFESSION.equals(profession) || BreederGoal.PROFESSION.equals(profession)) {
            return BuildingType.PASTURE;
        }
        return MinerGoal.PROFESSION.equals(profession) ? BuildingType.QUARRY : null;
    }
}
