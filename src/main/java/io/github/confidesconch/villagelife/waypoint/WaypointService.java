package io.github.confidesconch.villagelife.waypoint;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import io.github.confidesconch.villagelife.core.config.SyncedConfig;
import io.github.confidesconch.villagelife.friend.FriendService;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server side of the waypoints (M11e): applies the player's changes to his own list (checked by
 * {@link WaypointRules} and the config limit) and sends the list back – together with the waypoints
 * his friends share (M11g). Friends get the new list too when a shared waypoint may have changed.
 */
public final class WaypointService {
    private WaypointService() {}

    static void onAction(WaypointActionPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        MinecraftServer server = player.level().getServer();
        WaypointStore store = WaypointStore.get(server);
        List<Waypoint> current = store.of(player.getUUID());
        Optional<List<Waypoint>> changed = switch (payload.action()) {
            case "add" -> WaypointRules.add(current, payload.name(), payload.color(),
                    player.level().dimension().identifier().toString(), payload.x(),
                    Mth.clamp(payload.y(), player.level().getMinY(), player.level().getMaxY()), payload.z(),
                    SyncedConfig.MAX_WAYPOINTS_PER_PLAYER.get());
            case "edit" -> WaypointRules.edit(current, payload.id(), payload.name(), payload.color(), payload.shared());
            case "toggle" -> WaypointRules.toggle(current, payload.id());
            case "delete" -> WaypointRules.remove(current, payload.id());
            default -> Optional.empty(); // "sync" or unknown: only send the list
        };
        changed.ifPresent(list -> store.set(player.getUUID(), list));
        sendTo(player);
        if (changed.isPresent()) {
            for (UUID friendId : FriendService.friendsOf(server, player.getUUID())) {
                ServerPlayer friend = server.getPlayerList().getPlayer(friendId);
                if (friend != null) {
                    sendTo(friend);
                }
            }
        }
    }

    /** Sends the player his own waypoints and those his friends share. */
    public static void sendTo(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        WaypointStore store = WaypointStore.get(server);
        ListTag list = new ListTag();
        store.of(player.getUUID()).forEach(w -> list.add(tag(w, "")));
        for (UUID friendId : FriendService.friendsOf(server, player.getUUID())) {
            String owner = FriendService.nameOf(server, friendId);
            store.of(friendId).stream().filter(Waypoint::shared).forEach(w -> list.add(tag(w, owner)));
        }
        CompoundTag data = new CompoundTag();
        data.put("list", list);
        data.putInt("limit", SyncedConfig.MAX_WAYPOINTS_PER_PLAYER.get());
        PacketDistributor.sendToPlayer(player, new WaypointSyncPayload(data));
    }

    /** {@code owner} is empty for the player's own waypoints, else the friend's name. */
    private static CompoundTag tag(Waypoint w, String owner) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("id", w.id());
        tag.putString("name", w.name());
        tag.putInt("color", w.color());
        tag.putString("dimension", w.dimension());
        tag.putInt("x", w.x());
        tag.putInt("y", w.y());
        tag.putInt("z", w.z());
        tag.putBoolean("visible", w.visible());
        tag.putBoolean("shared", w.shared());
        tag.putString("owner", owner);
        return tag;
    }
}
