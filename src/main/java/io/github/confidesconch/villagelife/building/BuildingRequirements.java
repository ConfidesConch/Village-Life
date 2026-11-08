package io.github.confidesconch.villagelife.building;

import java.util.EnumMap;
import java.util.Map;

import io.github.confidesconch.villagelife.VillageLife;

import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;

/**
 * Loads the requirements of each {@link BuildingType} from data packs
 * ({@code data/villagelife/building_type/house.json} etc.). Reloaded with {@code /reload}.
 * If a file is missing or broken, the built-in default of the type is used.
 */
public final class BuildingRequirements extends SimpleJsonResourceReloadListener<BuildingRequirement> {

    private static volatile Map<BuildingType, BuildingRequirement> current = defaults();

    private BuildingRequirements() {
        super(BuildingRequirement.CODEC, FileToIdConverter.json("building_type"));
    }

    public static void onAddReloadListeners(AddServerReloadListenersEvent event) {
        event.addListener(Identifier.fromNamespaceAndPath(VillageLife.MOD_ID, "building_types"), new BuildingRequirements());
    }

    public static BuildingRequirement get(BuildingType type) {
        return current.get(type);
    }

    @Override
    protected void apply(Map<Identifier, BuildingRequirement> loaded, ResourceManager manager, ProfilerFiller profiler) {
        Map<BuildingType, BuildingRequirement> result = defaults();
        for (BuildingType type : BuildingType.values()) {
            BuildingRequirement fromData = loaded.get(Identifier.fromNamespaceAndPath(VillageLife.MOD_ID, type.getSerializedName()));
            if (fromData != null) {
                result.put(type, fromData);
            } else {
                VillageLife.LOGGER.warn("No data for building type '{}', using built-in defaults", type.getSerializedName());
            }
        }
        current = result;
        VillageLife.LOGGER.info("Loaded building type requirements: {}", result);
    }

    private static Map<BuildingType, BuildingRequirement> defaults() {
        Map<BuildingType, BuildingRequirement> map = new EnumMap<>(BuildingType.class);
        for (BuildingType type : BuildingType.values()) {
            map.put(type, type.defaultRequirement());
        }
        return map;
    }
}
