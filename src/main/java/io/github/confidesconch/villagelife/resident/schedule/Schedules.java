package io.github.confidesconch.villagelife.resident.schedule;

import java.util.Map;

import io.github.confidesconch.villagelife.VillageLife;

import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;

/**
 * Loads daily schedules from data packs ({@code data/<namespace>/schedule/<name>.json}),
 * reloaded with {@code /reload}. All residents use {@code villagelife:default} for now;
 * professions (M8) can name their own schedule. Broken files are skipped with a warning.
 */
public final class Schedules extends SimpleJsonResourceReloadListener<DailySchedule> {

    public static final Identifier DEFAULT_ID = Identifier.fromNamespaceAndPath(VillageLife.MOD_ID, "default");

    private static volatile Map<Identifier, DailySchedule> current = Map.of();

    private Schedules() {
        super(DailySchedule.CODEC, FileToIdConverter.json("schedule"));
    }

    public static void onAddReloadListeners(AddServerReloadListenersEvent event) {
        event.addListener(Identifier.fromNamespaceAndPath(VillageLife.MOD_ID, "schedules"), new Schedules());
    }

    /** The schedule with this id, else the default schedule, else the built-in plan. */
    public static DailySchedule get(Identifier id) {
        Map<Identifier, DailySchedule> map = current;
        DailySchedule schedule = map.get(id);
        if (schedule == null) {
            schedule = map.getOrDefault(DEFAULT_ID, DailySchedule.DEFAULT);
        }
        return schedule;
    }

    public static DailySchedule getDefault() {
        return get(DEFAULT_ID);
    }

    @Override
    protected void apply(Map<Identifier, DailySchedule> loaded, ResourceManager manager, ProfilerFiller profiler) {
        current = Map.copyOf(loaded);
        if (!loaded.containsKey(DEFAULT_ID)) {
            VillageLife.LOGGER.warn("No data for schedule '{}', using the built-in plan", DEFAULT_ID);
        }
        VillageLife.LOGGER.info("Loaded {} daily schedule(s): {}", loaded.size(), loaded.keySet());
    }
}
