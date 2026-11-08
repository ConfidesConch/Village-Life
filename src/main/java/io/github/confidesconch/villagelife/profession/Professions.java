package io.github.confidesconch.villagelife.profession;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.github.confidesconch.villagelife.VillageLife;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;

/**
 * Loads professions from data packs ({@code data/<namespace>/profession/*.json}), reloaded with
 * {@code /reload}. Broken files are skipped with a warning.
 */
public final class Professions extends SimpleJsonResourceReloadListener<Profession> {

    private static volatile Map<Identifier, Profession> current = Map.of();

    private Professions() {
        super(Profession.CODEC, FileToIdConverter.json("profession"));
    }

    public static void onAddReloadListeners(AddServerReloadListenersEvent event) {
        event.addListener(Identifier.fromNamespaceAndPath(VillageLife.MOD_ID, "professions"), new Professions());
    }

    public static Optional<Profession> get(String id) {
        Identifier parsed = Identifier.tryParse(id);
        return parsed == null ? Optional.empty() : Optional.ofNullable(current.get(parsed));
    }

    /** All loaded professions in book order ({@code order}, then id). */
    public static List<Map.Entry<Identifier, Profession>> all() {
        return current.entrySet().stream()
                .sorted(Comparator.comparingInt((Map.Entry<Identifier, Profession> e) -> e.getValue().order())
                        .thenComparing(e -> e.getKey().toString()))
                .toList();
    }

    /** The profession that works at this workstation block, if any (first match by id order). */
    public static Optional<Identifier> forWorkstation(Block block) {
        Identifier blockId = BuiltInRegistries.BLOCK.getKey(block);
        return current.entrySet().stream()
                .filter(entry -> entry.getValue().workstations().contains(blockId))
                .map(Map.Entry::getKey)
                .sorted()
                .findFirst();
    }

    @Override
    protected void apply(Map<Identifier, Profession> loaded, ResourceManager manager, ProfilerFiller profiler) {
        current = Map.copyOf(loaded);
        VillageLife.LOGGER.info("Loaded {} profession(s): {}", loaded.size(), loaded.keySet());
    }
}
