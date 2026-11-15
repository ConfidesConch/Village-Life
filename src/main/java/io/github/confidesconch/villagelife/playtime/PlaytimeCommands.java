package io.github.confidesconch.villagelife.playtime;

import java.util.ArrayList;
import java.util.List;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import io.github.confidesconch.villagelife.playtime.PlaytimeFormat.Entry;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;

/**
 * {@code /playtime} – your own play time – and {@code /playtime top} – the 10 players online with the
 * most play time (M29, owner's wish: his KubeJS script "playtimetracker.js" for 1.21.1 as part of the
 * mod). Uses Minecraft's own statistic {@code minecraft:play_time}, so nothing new is saved and the
 * time played before the mod was installed counts too. Everybody may use it.
 */
public final class PlaytimeCommands {
    private PlaytimeCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("playtime")
                .executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    Component time = format(seconds(player));
                    context.getSource().sendSuccess(() -> Component.translatable("villagelife.playtime.own", time)
                            .withStyle(ChatFormatting.GOLD), false);
                    return 1;
                })
                .then(Commands.literal("top").executes(context -> {
                    CommandSourceStack source = context.getSource();
                    List<Entry> entries = new ArrayList<>();
                    for (ServerPlayer player : source.getServer().getPlayerList().getPlayers()) {
                        entries.add(new Entry(player.getPlainTextName(), seconds(player)));
                    }
                    List<Entry> top = PlaytimeFormat.top(entries);
                    source.sendSuccess(() -> Component.literal("━━━━━━━━━━━━━━━━━━━━").withStyle(ChatFormatting.DARK_GRAY), false);
                    source.sendSuccess(() -> Component.translatable("villagelife.playtime.top")
                            .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
                    for (int i = 0; i < top.size(); i++) {
                        Entry entry = top.get(i);
                        Component line = Component.literal((i + 1) + ". ").withStyle(ChatFormatting.YELLOW)
                                .append(Component.literal(entry.name()).withStyle(ChatFormatting.WHITE))
                                .append(Component.literal(" - ").withStyle(ChatFormatting.GRAY))
                                .append(format(entry.seconds()).copy().withStyle(ChatFormatting.GREEN));
                        source.sendSuccess(() -> line, false);
                    }
                    source.sendSuccess(() -> Component.literal("━━━━━━━━━━━━━━━━━━━━").withStyle(ChatFormatting.DARK_GRAY), false);
                    return top.size();
                }));
    }

    private static long seconds(ServerPlayer player) {
        return PlaytimeFormat.seconds(player.getStats().getValue(Stats.CUSTOM.get(Stats.PLAY_TIME)));
    }

    /** "12h 5min 3s" (units from the language file). */
    private static Component format(long seconds) {
        long[] parts = PlaytimeFormat.split(seconds);
        return Component.translatable("villagelife.playtime.format", parts[0], parts[1], parts[2])
                .withStyle(ChatFormatting.YELLOW);
    }
}
