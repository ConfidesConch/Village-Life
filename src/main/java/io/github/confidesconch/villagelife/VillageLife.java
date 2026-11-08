package io.github.confidesconch.villagelife;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import io.github.confidesconch.villagelife.book.BookSystem;
import io.github.confidesconch.villagelife.claim.ClaimSystem;
import io.github.confidesconch.villagelife.friend.FriendSystem;
import io.github.confidesconch.villagelife.family.FamilySystem;
import io.github.confidesconch.villagelife.growth.GrowthSystem;
import io.github.confidesconch.villagelife.market.MarketSystem;
import io.github.confidesconch.villagelife.waypoint.WaypointSystem;
import io.github.confidesconch.villagelife.building.BuildingRequirements;
import io.github.confidesconch.villagelife.building.BuildingRescanner;
import io.github.confidesconch.villagelife.command.VillageLifeCommands;
import io.github.confidesconch.villagelife.profession.ProfessionSystem;
import io.github.confidesconch.villagelife.social.SocialSystem;
import io.github.confidesconch.villagelife.resident.housing.HousingService;
import io.github.confidesconch.villagelife.resident.schedule.Schedules;
import io.github.confidesconch.villagelife.core.config.SyncedConfig;
import io.github.confidesconch.villagelife.core.network.ModNetwork;
import io.github.confidesconch.villagelife.core.util.ModProfiler;
import io.github.confidesconch.villagelife.core.registry.ModRegistries;

import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * Main entry point of Village Life. Loaded on both the physical client and the dedicated server.
 * Only wires systems together; the systems themselves live in their own packages.
 * Must never reference client-only classes (see {@code client} package).
 */
@Mod(VillageLife.MOD_ID)
public final class VillageLife {
    /** Mod id; must match {@code mod_id} in gradle.properties. */
    public static final String MOD_ID = "villagelife";

    /** Shared logger for the whole mod. */
    public static final Logger LOGGER = LogUtils.getLogger();

    public VillageLife(IEventBus modEventBus, ModContainer modContainer) {
        // Mod bus: registration and setup (the book adds its item before the registries are registered)
        BookSystem.init(modEventBus);
        WaypointSystem.init(modEventBus);
        FriendSystem.init(modEventBus);
        ClaimSystem.init(modEventBus);
        MarketSystem.init(modEventBus); // M15a
        GrowthSystem.init(); // M16
        SocialSystem.init(); // M17
        FamilySystem.init(); // M18b
        io.github.confidesconch.villagelife.defense.DefenseSystem.init(); // M20a
        io.github.confidesconch.villagelife.convoy.ConvoySystem.init(modEventBus); // M20c
        ModRegistries.register(modEventBus);
        modEventBus.addListener(ModNetwork::onRegisterPayloads);
        modEventBus.addListener(this::onCommonSetup);

        // Gameplay balance values: per server/world, synced to clients
        modContainer.registerConfig(ModConfig.Type.SYNCED, SyncedConfig.SPEC);

        // Game bus: runtime events
        NeoForge.EVENT_BUS.addListener(VillageLifeCommands::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(BuildingRequirements::onAddReloadListeners);
        NeoForge.EVENT_BUS.addListener(Schedules::onAddReloadListeners);
        ProfessionSystem.init();
        NeoForge.EVENT_BUS.addListener(BuildingRescanner::onBreak);
        NeoForge.EVENT_BUS.addListener(BuildingRescanner::onPlace);
        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class,
                event -> ModProfiler.time(ModProfiler.BUILDINGS, () -> BuildingRescanner.onServerTick(event)));
        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class,
                event -> ModProfiler.time(ModProfiler.HOUSING, () -> HousingService.onServerTick(event)));
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, VillageLife::onTickEnd); // after all other systems
        NeoForge.EVENT_BUS.addListener(this::onServerStarting);
        NeoForge.EVENT_BUS.addListener(VillageLife::onServerStopped);
    }

    /**
     * Forgets what systems keep in memory per server (raids, alarms, caches), so nothing of a world is carried
     * into the next one opened in the same game session (bug review 08.11.2026).
     */
    private static void onServerStopped(ServerStoppedEvent event) {
        BuildingRescanner.clear();
        io.github.confidesconch.villagelife.defense.RaidService.clear();
        io.github.confidesconch.villagelife.defense.AlarmService.clear();
        io.github.confidesconch.villagelife.social.SocialService.clear();
        io.github.confidesconch.villagelife.profession.JobService.clear();
        io.github.confidesconch.villagelife.profession.work.VillageGates.clear();
        io.github.confidesconch.villagelife.book.VillageBookService.clear();
    }

    /** Closes the performance measurement of this tick (M12a). */
    private static void onTickEnd(ServerTickEvent.Post event) {
        ModProfiler.endTick();
    }

    private void onCommonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("Village Life common setup complete");
    }

    private void onServerStarting(ServerStartingEvent event) {
        BuildingRescanner.clear();
        LOGGER.info("Village Life is active on this server (village radius: {})",
                SyncedConfig.VILLAGE_RADIUS.getAsInt());
    }
}
