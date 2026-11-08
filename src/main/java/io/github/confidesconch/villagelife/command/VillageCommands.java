package io.github.confidesconch.villagelife.command;

import java.util.Collection;
import java.util.Optional;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import io.github.confidesconch.villagelife.building.BuildingType;
import io.github.confidesconch.villagelife.profession.JobService;
import io.github.confidesconch.villagelife.resident.ResidentService;
import io.github.confidesconch.villagelife.resident.needs.NeedsRules;
import io.github.confidesconch.villagelife.resident.housing.HousingService;
import io.github.confidesconch.villagelife.village.Village;
import io.github.confidesconch.villagelife.village.VillageManager;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/** {@code /villagelife village info|list}. */
final class VillageCommands {
    private VillageCommands() {}

    private static final long TICKS_PER_DAY = 24000L;

    static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("village")
                .then(Commands.literal("info").executes(context -> info(context.getSource())))
                .then(Commands.literal("list").executes(context -> list(context.getSource())));
    }

    private static int info(CommandSourceStack source) {
        BlockPos pos = BlockPos.containing(source.getPosition());
        Optional<Village> found = VillageManager.get(source.getLevel()).findAt(pos);
        if (found.isEmpty()) {
            source.sendFailure(Component.translatable("villagelife.village.none_here"));
            return 0;
        }
        Village village = found.get();
        long day = village.createdGameTime() / TICKS_PER_DAY + 1;
        source.sendSuccess(() -> Component.translatable("villagelife.village.info.header", village.displayName())
                .withStyle(ChatFormatting.GOLD), false);
        source.sendSuccess(() -> Component.translatable("villagelife.village.info.owner", village.ownerName()), false);
        source.sendSuccess(() -> Component.translatable("villagelife.village.info.center", village.center().toShortString()), false);
        source.sendSuccess(() -> Component.translatable("villagelife.village.info.radius", village.radius()), false);
        source.sendSuccess(() -> Component.translatable("villagelife.village.info.founded", day), false);
        source.sendSuccess(() -> Component.translatable("villagelife.village.info.buildings", village.buildings().size(),
                count(village, BuildingType.HOUSE), count(village, BuildingType.STORAGE), count(village, BuildingType.WORKPLACE),
                village.buildings().stream().filter(b -> !b.isValid()).count()), false);
        source.sendSuccess(() -> Component.translatable("villagelife.village.info.residents", village.residents().size(),
                village.residents().stream().map(r -> r.fullName()).collect(java.util.stream.Collectors.joining(", "))), false);
        int[] beds = HousingService.bedCounts(village);
        source.sendSuccess(() -> Component.translatable("villagelife.village.info.beds", beds[0], beds[1],
                village.residents().stream().filter(r -> r.bed().isEmpty()).count()), false);
        java.util.OptionalInt mood = ResidentService.averageSatisfaction(source.getLevel(), village);
        if (mood.isPresent()) {
            long hungry = ResidentService.loadedResidents(source.getLevel(), village).stream()
                    .filter(r -> NeedsRules.wantsToEat(r.needs().food())).count();
            source.sendSuccess(() -> Component.translatable("villagelife.village.info.mood", mood.getAsInt(),
                    Component.translatable("villagelife.mood." + NeedsRules.mood(mood.getAsInt()).name().toLowerCase(java.util.Locale.ROOT)),
                    hungry), false);
        }
        int[] jobs = JobService.workplaceCounts(source.getLevel(), village);
        source.sendSuccess(() -> Component.translatable("villagelife.village.info.jobs", jobs[0], jobs[1],
                village.residents().stream().filter(r -> r.job().isEmpty()).count()), false);
        return 1;
    }

    private static long count(Village village, BuildingType type) {
        return village.buildings().stream().filter(b -> b.type() == type && b.isValid()).count();
    }

    private static int list(CommandSourceStack source) {
        Collection<Village> villages = VillageManager.get(source.getLevel()).all();
        source.sendSuccess(() -> Component.translatable("villagelife.village.list.header", villages.size())
                .withStyle(ChatFormatting.GOLD), false);
        for (Village village : villages) {
            source.sendSuccess(() -> Component.translatable("villagelife.village.list.entry",
                    village.displayName(), village.center().toShortString()), false);
        }
        return villages.size();
    }
}
