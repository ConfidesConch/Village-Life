package io.github.confidesconch.villagelife.command;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import io.github.confidesconch.villagelife.building.BuildingSelfTest;
import io.github.confidesconch.villagelife.core.util.ModProfiler;
import io.github.confidesconch.villagelife.core.util.RollingStats;
import io.github.confidesconch.villagelife.defense.RaidService;
import io.github.confidesconch.villagelife.family.FamilyService;
import io.github.confidesconch.villagelife.growth.GrowthService;
import io.github.confidesconch.villagelife.market.CaravanService;
import io.github.confidesconch.villagelife.market.contract.ContractService;
import io.github.confidesconch.villagelife.resident.ResidentEntity;
import io.github.confidesconch.villagelife.social.SocialService;
import io.github.confidesconch.villagelife.resident.ResidentService;
import io.github.confidesconch.villagelife.resident.schedule.Activity;
import io.github.confidesconch.villagelife.resident.housing.HousingService;
import io.github.confidesconch.villagelife.resident.schedule.ScheduleClock;
import io.github.confidesconch.villagelife.village.Village;
import io.github.confidesconch.villagelife.village.VillageManager;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.phys.AABB;

/** {@code /villagelife debug ...} – developer tools, operators only. */
final class DebugCommands {
    private DebugCommands() {}

    static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("debug")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("buildtests").executes(context -> {
                    CommandSourceStack source = context.getSource();
                    BlockPos start = BlockPos.containing(source.getPosition()).offset(3, 0, 3);
                    List<Component> report = BuildingSelfTest.run(source.getLevel(), start);
                    report.forEach(line -> source.sendSuccess(() -> line, false));
                    return 1;
                }))
                .then(Commands.literal("testvillage").executes(context -> {
                    CommandSourceStack source = context.getSource();
                    int buildings = TestVillage.build(source.getLevel(), source.getPlayerOrException());
                    if (buildings < 0) {
                        return 0;
                    }
                    source.sendSuccess(() -> Component.translatable("villagelife.debug.testvillage.done", buildings), true);
                    return 1;
                }))
                .then(Commands.literal("stresstest") // M21: a city with many residents to measure
                        .then(Commands.argument("count", IntegerArgumentType.integer(10, 200)).executes(context -> {
                            CommandSourceStack source = context.getSource();
                            StressVillage.Result result = StressVillage.build(source.getLevel(), source.getPlayerOrException(),
                                    IntegerArgumentType.getInteger(context, "count"));
                            if (result == null) {
                                return 0;
                            }
                            source.sendSuccess(() -> Component.translatable("villagelife.debug.stresstest.done",
                                    result.residents(), result.places()), true);
                            return 1;
                        })))
                .then(Commands.literal("spawnresident")
                        .executes(context -> spawnResidents(context.getSource(), 1))
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 100))
                                .executes(context -> spawnResidents(context.getSource(),
                                        IntegerArgumentType.getInteger(context, "count")))))
                .then(Commands.literal("immigrate").executes(context -> {
                    CommandSourceStack source = context.getSource();
                    Optional<Village> village = VillageManager.get(source.getLevel())
                            .findAt(BlockPos.containing(source.getPosition()));
                    if (village.isEmpty()) {
                        source.sendFailure(Component.translatable("villagelife.village.none_here"));
                        return 0;
                    }
                    if (!HousingService.update(source.getLevel(), village.get().id(), true)) {
                        source.sendFailure(Component.translatable("villagelife.debug.immigrate.none"));
                        return 0;
                    }
                    return 1;
                }))
                .then(Commands.literal("caravan").executes(context -> {
                    CommandSourceStack source = context.getSource();
                    Optional<Village> village = VillageManager.get(source.getLevel())
                            .findAt(BlockPos.containing(source.getPosition()));
                    if (village.isEmpty()) {
                        source.sendFailure(Component.translatable("villagelife.village.none_here"));
                        return 0;
                    }
                    if (!CaravanService.callNow(source.getLevel(), village.get())) {
                        source.sendFailure(Component.translatable("villagelife.debug.caravan.none"));
                        return 0;
                    }
                    source.sendSuccess(() -> Component.translatable("villagelife.debug.caravan.done"), false);
                    return 1;
                }))
                .then(Commands.literal("contracts").executes(context -> {
                    CommandSourceStack source = context.getSource();
                    Optional<Village> village = VillageManager.get(source.getLevel())
                            .findAt(BlockPos.containing(source.getPosition()));
                    if (village.isEmpty()) {
                        source.sendFailure(Component.translatable("villagelife.village.none_here"));
                        return 0;
                    }
                    int count = ContractService.hurry(source.getServer(), village.get().id());
                    source.sendSuccess(() -> Component.translatable("villagelife.debug.contracts.done", count), false);
                    return 1;
                }))
                .then(Commands.literal("convoy").executes(context -> { // M20c: shipments on the road are raided soon
                    CommandSourceStack source = context.getSource();
                    // in a village: its shipments; anywhere else (e.g. beside the wagon): all shipments on the road
                    UUID village = VillageManager.get(source.getLevel()).findAt(BlockPos.containing(source.getPosition()))
                            .map(Village::id).orElse(null);
                    int count = ContractService.raidSoon(source.getServer(), village);
                    source.sendSuccess(() -> Component.translatable("villagelife.debug.convoy.done", count), false);
                    return 1;
                }))
                .then(Commands.literal("stage").executes(context -> {
                    CommandSourceStack source = context.getSource();
                    Optional<Village> village = VillageManager.get(source.getLevel())
                            .findAt(BlockPos.containing(source.getPosition()));
                    if (village.isEmpty()) {
                        source.sendFailure(Component.translatable("villagelife.village.none_here"));
                        return 0;
                    }
                    if (!GrowthService.forceRise(source.getLevel(), village.get())) {
                        source.sendFailure(Component.translatable("villagelife.debug.stage.top"));
                        return 0;
                    }
                    return 1;
                }))
                .then(Commands.literal("couple").executes(context -> {
                    CommandSourceStack source = context.getSource();
                    Optional<Village> village = VillageManager.get(source.getLevel())
                            .findAt(BlockPos.containing(source.getPosition()));
                    if (village.isEmpty()) {
                        source.sendFailure(Component.translatable("villagelife.village.none_here"));
                        return 0;
                    }
                    if (!SocialService.debugCouple(source.getLevel(), village.get())) {
                        source.sendFailure(Component.translatable("villagelife.debug.couple.none"));
                        return 0;
                    }
                    return 1;
                }))
                .then(Commands.literal("baby").executes(context -> {
                    CommandSourceStack source = context.getSource();
                    Optional<Village> village = VillageManager.get(source.getLevel())
                            .findAt(BlockPos.containing(source.getPosition()));
                    if (village.isEmpty()) {
                        source.sendFailure(Component.translatable("villagelife.village.none_here"));
                        return 0;
                    }
                    if (!FamilyService.debugBirth(source.getLevel(), village.get())) {
                        source.sendFailure(Component.translatable("villagelife.debug.baby.none"));
                        return 0;
                    }
                    return 1;
                }))
                .then(Commands.literal("attack").executes(context -> { // M20b: bandits attack right now
                    CommandSourceStack source = context.getSource();
                    Optional<Village> village = VillageManager.get(source.getLevel())
                            .findAt(BlockPos.containing(source.getPosition()));
                    if (village.isEmpty()) {
                        source.sendFailure(Component.translatable("villagelife.village.none_here"));
                        return 0;
                    }
                    if (RaidService.isUnderAttack(village.get().id()) || !RaidService.start(source.getLevel(), village.get())) {
                        source.sendFailure(Component.translatable("villagelife.debug.attack.failed"));
                        return 0;
                    }
                    return 1;
                }))
                .then(Commands.literal("schedule").executes(context -> showSchedule(context.getSource())))
                .then(Commands.literal("perf").executes(context -> showPerformance(context.getSource())));
    }

    /**
     * Time per system of the mod over the last 5 seconds (M12a), the mod in total and its share of the
     * whole server tick. Target: below 2 ms per tick with 100 residents.
     */
    private static int showPerformance(CommandSourceStack source) {
        RollingStats residents = ModProfiler.stats().get(ModProfiler.RESIDENTS);
        source.sendSuccess(() -> Component.translatable("villagelife.debug.perf.header", residents.lastCount(),
                micros(residents.averageMicrosPerUnit())), false);
        ModProfiler.stats().forEach((system, stats) -> {
            source.sendSuccess(() -> Component.translatable(
                    "villagelife.debug.perf.line", Component.translatable("villagelife.debug.perf.system." + system),
                    micros(stats.averageMicrosPerTick()), micros(stats.maxMicrosPerTick())), false);
            if (system.equals(ModProfiler.RESIDENTS) && ModProfiler.details().containsKey(ModProfiler.PATHS)) {
                RollingStats paths = ModProfiler.details().get(ModProfiler.PATHS);
                RollingStats deferred = ModProfiler.details().get(ModProfiler.PATHS_DEFERRED);
                double deferredPerTick = deferred == null ? 0 : deferred.averageUnitsPerTick();
                source.sendSuccess(() -> Component.translatable("villagelife.debug.perf.paths",
                        micros(paths.averageMicrosPerTick()), micros(paths.maxMicrosPerTick()),
                        String.format(Locale.ROOT, "%.1f", paths.averageUnitsPerTick()),
                        micros(paths.averageMicrosPerUnit()),
                        String.format(Locale.ROOT, "%.1f", deferredPerTick)), false);
                for (String detail : new String[]{ModProfiler.BRAIN, ModProfiler.NAVIGATION}) { // M21
                    RollingStats part = ModProfiler.details().get(detail);
                    if (part != null) {
                        source.sendSuccess(() -> Component.translatable("villagelife.debug.perf.detail",
                                Component.translatable("villagelife.debug.perf.detail." + detail),
                                micros(part.averageMicrosPerTick()), micros(part.maxMicrosPerTick())), false);
                    }
                }
            }
        });
        double totalMicros = ModProfiler.totalMicrosPerTick();
        double serverMicros = source.getServer().getAverageTickTimeNanos() / 1000.0;
        double share = serverMicros <= 0 ? 0 : 100 * totalMicros / serverMicros;
        // M21 (owner's choice "3b"): traffic light – green below 2 ms, yellow up to 5 ms, red above
        String light = totalMicros < 2000 ? "green" : totalMicros < 5000 ? "yellow" : "red";
        ChatFormatting color = switch (light) {
            case "green" -> ChatFormatting.GREEN;
            case "yellow" -> ChatFormatting.YELLOW;
            default -> ChatFormatting.RED;
        };
        source.sendSuccess(() -> Component.translatable("villagelife.debug.perf.total", micros(totalMicros),
                String.format(Locale.ROOT, "%.2f", serverMicros / 1000.0), String.format(Locale.ROOT, "%.0f", share))
                .withStyle(color), false);
        source.sendSuccess(() -> Component.translatable("villagelife.debug.perf.light." + light, residents.lastCount())
                .withStyle(color, ChatFormatting.BOLD), false);
        return 1;
    }

    private static String micros(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    /** Current time of day and how many loaded residents of the village do what. */
    private static int showSchedule(CommandSourceStack source) {
        Optional<Village> village = VillageManager.get(source.getLevel())
                .findAt(BlockPos.containing(source.getPosition()));
        if (village.isEmpty()) {
            source.sendFailure(Component.translatable("villagelife.village.none_here"));
            return 0;
        }
        BlockPos center = village.get().center();
        int radius = village.get().area().radius();
        AABB box = new AABB(center.getX() - radius, source.getLevel().getMinY(), center.getZ() - radius,
                center.getX() + radius + 1, source.getLevel().getMaxY(), center.getZ() + radius + 1);
        Map<Activity, Integer> counts = new EnumMap<>(Activity.class);
        for (ResidentEntity resident : source.getLevel().getEntitiesOfClass(ResidentEntity.class, box)) {
            counts.merge(resident.brain().activity(), 1, Integer::sum);
        }
        MutableComponent summary = Component.empty();
        counts.forEach((activity, count) -> {
            if (!summary.getSiblings().isEmpty()) {
                summary.append(", ");
            }
            summary.append(Component.translatable(activity.translationKey())).append(": " + count);
        });
        String time = ScheduleClock.format(ScheduleClock.minuteOfDay(source.getLevel().getOverworldClockTime()));
        source.sendSuccess(() -> Component.translatable("villagelife.debug.schedule", time,
                counts.isEmpty() ? Component.translatable("villagelife.debug.schedule.none") : summary), false);
        return 1;
    }

    private static int spawnResidents(CommandSourceStack source, int count) {
        BlockPos pos = BlockPos.containing(source.getPosition());
        Optional<Village> village = VillageManager.get(source.getLevel()).findAt(pos);
        if (village.isEmpty()) {
            source.sendFailure(Component.translatable("villagelife.village.none_here"));
            return 0;
        }
        int spawned = 0;
        for (int i = 0; i < count; i++) {
            // always use the current village state, because each spawn adds a resident
            Village current = VillageManager.get(source.getLevel()).byId(village.get().id()).orElseThrow();
            ResidentService.SpawnResult result = ResidentService.spawn(source.getLevel(), pos, current);
            if (result instanceof ResidentService.SpawnResult.Spawned s && count == 1) {
                source.sendSuccess(() -> Component.translatable("villagelife.resident.spawned",
                        s.resident().getFullName(), current.displayName()), false);
            }
            if (result instanceof ResidentService.SpawnResult.VillageFull full) {
                source.sendFailure(Component.translatable("villagelife.resident.village_full", full.max()));
                break;
            }
            spawned++;
        }
        if (count > 1) {
            int total = spawned;
            source.sendSuccess(() -> Component.translatable("villagelife.resident.spawned_many", total), false);
        }
        return spawned;
    }
}
