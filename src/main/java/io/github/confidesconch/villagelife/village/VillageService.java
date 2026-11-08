package io.github.confidesconch.villagelife.village;

import java.util.Optional;

import io.github.confidesconch.villagelife.VillageLife;
import io.github.confidesconch.villagelife.core.config.SyncedConfig;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Game rules for founding and dissolving villages. Called by the village center block on the server.
 */
public final class VillageService {
    private VillageService() {}

    /** Checks whether a new village may be founded at {@code pos}; tells the player why not. */
    public static boolean canFoundVillageAt(ServerLevel level, BlockPos pos, Player player) {
        int radius = SyncedConfig.VILLAGE_RADIUS.getAsInt();
        Optional<Village> overlapping = VillageManager.get(level).findOverlapping(pos, radius);
        if (overlapping.isPresent()) {
            if (player != null) {
                player.sendSystemMessage(Component.translatable("villagelife.village.too_close",
                        overlapping.get().displayName()).withStyle(ChatFormatting.RED));
            }
            return false;
        }
        return true;
    }

    public static void onCenterPlaced(ServerLevel level, BlockPos pos, LivingEntity placer) {
        if (!(placer instanceof ServerPlayer player)) {
            VillageLife.LOGGER.warn("Village center at {} placed without a player; no village founded", pos);
            return;
        }
        int radius = SyncedConfig.VILLAGE_RADIUS.getAsInt();
        Village village = VillageManager.get(level).create(pos, radius, player.getUUID(),
                player.getName().getString(), level.getGameTime());
        VillageLife.LOGGER.info("Village {} founded at {} by {}", village.id(), pos, village.ownerName());
        io.github.confidesconch.villagelife.api.ApiEvents.villageFounded(level, village.id(), pos, village.owner()); // M22a
        player.sendSystemMessage(Component.translatable("villagelife.village.founded",
                village.displayName(), village.radius()).withStyle(ChatFormatting.GREEN));
    }

    public static void onCenterRemoved(ServerLevel level, BlockPos pos) {
        VillageManager.get(level).removeByCenter(pos).ifPresent(village -> {
            VillageLife.LOGGER.info("Village {} at {} dissolved", village.id(), pos);
            io.github.confidesconch.villagelife.api.ApiEvents.villageDissolved(level, village.id(), pos); // M22a
            EventLog.get(level.getServer()).forget(village.id());
            ServerPlayer owner = level.getServer().getPlayerList().getPlayer(village.owner());
            if (owner != null) {
                owner.sendSystemMessage(Component.translatable("villagelife.village.dissolved",
                        village.displayName()).withStyle(ChatFormatting.YELLOW));
            }
        });
    }

    /**
     * Sends a chat message to the owner and the managers of the village who are online and did not switch
     * this topic off (M19a).
     */
    public static void tellManagers(net.minecraft.server.MinecraftServer server, Village village, NotificationTopic topic,
                                    Component message) {
        EventLog.record(server, village, topic.id(), message); // M21b3: everything told also lands in the log
        java.util.List<java.util.UUID> people = new java.util.ArrayList<>(village.managers());
        people.add(village.owner());
        NotificationSettings settings = NotificationSettings.get(server);
        for (java.util.UUID id : people) {
            net.minecraft.server.level.ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null && settings.wants(id, topic)) {
                player.sendSystemMessage(message);
            }
        }
    }
}
