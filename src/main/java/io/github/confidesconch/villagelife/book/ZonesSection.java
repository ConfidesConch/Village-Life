package io.github.confidesconch.villagelife.book;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import io.github.confidesconch.villagelife.core.config.SyncedConfig;
import io.github.confidesconch.villagelife.village.Village;
import io.github.confidesconch.villagelife.village.VillageManager;
import io.github.confidesconch.villagelife.village.VillageZone;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** The village's zones for the map and the "Markers" chapter (M11e); only owner and managers may change them. */
final class ZonesSection {
    private ZonesSection() {}

    static void write(ServerPlayer player, Village village, CompoundTag out) {
        out.putBoolean("can_manage", village.mayManage(player.getUUID()));
        out.putInt("limit", SyncedConfig.MAX_ZONES_PER_VILLAGE.get());
        ListTag list = new ListTag();
        for (VillageZone zone : village.zones()) {
            CompoundTag tag = new CompoundTag();
            tag.putInt("id", zone.id());
            tag.putString("name", zone.name());
            tag.putInt("color", zone.color());
            tag.putInt("min_x", zone.minX());
            tag.putInt("min_z", zone.minZ());
            tag.putInt("max_x", zone.maxX());
            tag.putInt("max_z", zone.maxZ());
            list.add(tag);
        }
        out.put("list", list);
    }

    static void onEdit(EditVillageZonePayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        UUID id;
        try {
            id = UUID.fromString(payload.villageId());
        } catch (IllegalArgumentException invalid) {
            return;
        }
        VillageManager manager = VillageManager.get(player.level());
        manager.byId(id).filter(v -> v.mayManage(player.getUUID())).ifPresent(village -> {
            List<VillageZone> zones = village.zones();
            Optional<List<VillageZone>> changed = switch (payload.action()) {
                case "add" -> VillageZone.add(zones, village.area(), payload.name(), payload.color(),
                        payload.x1(), payload.z1(), payload.x2(), payload.z2(), SyncedConfig.MAX_ZONES_PER_VILLAGE.get());
                case "edit" -> VillageZone.edit(zones, payload.id(), payload.name(), payload.color());
                case "delete" -> VillageZone.remove(zones, payload.id());
                default -> Optional.empty();
            };
            changed.ifPresent(list -> manager.update(village.withZones(list)));
            VillageBookService.open(player, manager.byId(id).orElse(village), null, null);
        });
    }
}
