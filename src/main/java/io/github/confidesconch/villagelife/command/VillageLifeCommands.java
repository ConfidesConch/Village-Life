package io.github.confidesconch.villagelife.command;

import io.github.confidesconch.villagelife.VillageLife;

import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Root of all {@code /villagelife} commands. Sub-commands of later systems are attached here.
 */
public final class VillageLifeCommands {
    private VillageLifeCommands() {}

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal(VillageLife.MOD_ID)
                .then(Commands.literal("version")
                        .executes(context -> {
                            Component message = Component.translatable("commands.villagelife.version",
                                    modVersion(VillageLife.MOD_ID), modVersion("minecraft"), modVersion("neoforge"));
                            context.getSource().sendSuccess(() -> message, false);
                            return 1;
                        }))
                .then(VillageCommands.build())
                .then(DebugCommands.build()));
    }

    private static String modVersion(String modId) {
        return ModList.get().getModContainerById(modId)
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse("?");
    }
}
