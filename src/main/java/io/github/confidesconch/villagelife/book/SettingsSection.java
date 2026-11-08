package io.github.confidesconch.villagelife.book;

import java.util.UUID;

import io.github.confidesconch.villagelife.defense.AlarmService;
import io.github.confidesconch.villagelife.friend.FriendService;
import io.github.confidesconch.villagelife.village.EventLog;
import io.github.confidesconch.villagelife.village.Village;
import io.github.confidesconch.villagelife.village.VillageManager;
import io.github.confidesconch.villagelife.village.VillageManagers;
import io.github.confidesconch.villagelife.village.VillageSettings;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Data and changes of the "Settings" chapter (M11c). Owner and managers (M11g2) may change the
 * settings; only the owner may rename the village and choose the managers among his friends.
 */
final class SettingsSection {
    private SettingsSection() {}

    static void write(ServerPlayer player, Village village, CompoundTag out) {
        VillageSettings s = village.settings();
        boolean owner = village.owner().equals(player.getUUID());
        out.putBoolean("is_owner", owner);
        out.putBoolean("can_manage", village.mayManage(player.getUUID()));
        out.putString("name", village.customName().orElse(""));
        out.putBoolean("immigration", s.immigration());
        out.putBoolean("auto_jobs", s.autoJobs());
        out.putInt("work_start", s.workStart());
        out.putInt("work_end", s.workEnd());
        out.putBoolean("tools_from_storage", s.toolsFromStorage());
        out.putInt("food_reserve", s.foodReserve());
        out.putBoolean("strangers_may_read", s.strangersMayRead());
        out.putBoolean("attacks", s.attacks()); // M20b
        out.putInt("herd_limit", s.herdLimit());
        out.putInt("mine_depth", s.mineDepth());
        out.putInt("caravan_days", s.caravanDays());
        MinecraftServer server = player.level().getServer();
        ListTag managers = new ListTag();
        for (UUID id : village.managers()) {
            managers.add(person(id, FriendService.nameOf(server, id), true));
        }
        out.put("managers", managers);
        if (owner) { // the owner picks managers among his friends
            ListTag friends = new ListTag();
            for (UUID id : FriendService.friendsOf(server, player.getUUID())) {
                friends.add(person(id, FriendService.nameOf(server, id), village.managers().contains(id)));
            }
            out.put("friends", friends);
        }
    }

    private static CompoundTag person(UUID id, String name, boolean manager) {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", id.toString());
        tag.putString("name", name);
        tag.putBoolean("manager", manager);
        return tag;
    }

    /** Applies a change from the book after checking rights and value; then refreshes the book. */
    static void onUpdate(UpdateVillageSettingsPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        VillageManager manager = VillageManager.get(player.level());
        UUID id;
        try {
            id = UUID.fromString(payload.villageId());
        } catch (IllegalArgumentException invalid) {
            return;
        }
        manager.byId(id).filter(v -> v.mayManage(player.getUUID())).ifPresent(village -> {
            boolean owner = village.owner().equals(player.getUUID());
            Village updated = switch (payload.key()) {
                case "name" -> owner ? village.withCustomName(VillageSettings.cleanName(payload.value())) : null;
                case "manager" -> owner ? withManager(player, village, payload.value()) : null;
                case "priority" -> ManagementSection.withPriority(village, payload.value()); // M19a
                case "law" -> ManagementSection.withLaw(player, village, payload.value()); // M19b
                case "alarm" -> { // M21b3: the window's alarm button, like ringing a bell
                    AlarmService.toggleManual(player.level(), village, player, null);
                    yield null;
                }
                case "hide_problem" -> { // M21b6b
                    ProblemsSection.hide(player, village, payload.value());
                    yield null;
                }
                case "delete_event" -> {
                    try {
                        EventLog.get(player.level().getServer()).delete(village.id(), Long.parseLong(payload.value()));
                    } catch (NumberFormatException invalid) {
                        // ignore: the value came from a client
                    }
                    yield null;
                }
                case "clear_events" -> {
                    EventLog.get(player.level().getServer()).clear(village.id());
                    yield null;
                }
                case "notify" -> { // M19a: only the reader's own messages
                    ManagementSection.setNotification(player, payload.value());
                    yield null;
                }
                default -> village.settings().with(payload.key(), payload.value()).map(village::withSettings).orElse(null);
            };
            if (updated != null) {
                manager.update(updated);
            }
            boolean management = payload.key().equals("priority") || payload.key().equals("notify") || payload.key().equals("law");
            VillageBookService.open(player, manager.byId(id).orElse(village), null, management ? "management" : "settings");
        });
    }

    /** Value "uuid:true" or "uuid:false". */
    private static Village withManager(ServerPlayer player, Village village, String value) {
        int colon = value.lastIndexOf(':');
        if (colon < 0) {
            return null;
        }
        UUID target;
        try {
            target = UUID.fromString(value.substring(0, colon));
        } catch (IllegalArgumentException invalid) {
            return null;
        }
        boolean on = Boolean.parseBoolean(value.substring(colon + 1));
        boolean friend = FriendService.areFriends(player.level().getServer(), village.owner(), target);
        return VillageManagers.set(village.managers(), village.owner(), target, on, friend)
                .map(village::withManagers).orElse(null);
    }

    /** When a friendship ends, the other one is no longer manager of the owner's villages. */
    static void dropFormerFriends(ServerPlayer changed) {
        MinecraftServer server = changed.level().getServer();
        for (ServerLevel level : server.getAllLevels()) {
            VillageManager manager = VillageManager.get(level);
            for (Village village : java.util.List.copyOf(manager.all())) {
                if (village.managers().isEmpty()) {
                    continue;
                }
                var kept = village.managers().stream()
                        .filter(m -> FriendService.areFriends(server, village.owner(), m)).toList();
                if (kept.size() != village.managers().size()) {
                    manager.update(village.withManagers(kept));
                }
            }
        }
    }
}
