package io.github.confidesconch.villagelife.book;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import io.github.confidesconch.villagelife.building.Building;
import io.github.confidesconch.villagelife.building.BuildingMarkerBlock;
import io.github.confidesconch.villagelife.building.BuildingService;
import io.github.confidesconch.villagelife.building.BuildingType;
import io.github.confidesconch.villagelife.core.registry.ModBlocks;
import io.github.confidesconch.villagelife.inventory.StorageAccess;
import io.github.confidesconch.villagelife.profession.JobService;
import io.github.confidesconch.villagelife.profession.Profession;
import io.github.confidesconch.villagelife.profession.Professions;
import io.github.confidesconch.villagelife.profession.work.HerdGoal;
import io.github.confidesconch.villagelife.resident.ResidentRecord;
import io.github.confidesconch.villagelife.village.Village;
import io.github.confidesconch.villagelife.village.VillageManager;
import io.github.confidesconch.villagelife.village.VillageZone;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Data of the "Buildings" chapter (M11b) and the building pages (M11i): every registered building
 * with contents and occupants; the contents of its containers only for the building that is opened.
 */
final class BuildingsSection {
    private BuildingsSection() {}

    /** Different item kinds shown per building (counts of the same item are added up). */
    private static final int MAX_CONTENT_ENTRIES = 54;

    static void write(ServerPlayer player, Village village, CompoundTag out) {
        out.putBoolean("can_manage", village.mayManage(player.getUUID()));
        ListTag zones = new ListTag();
        for (VillageZone zone : village.zones()) {
            CompoundTag z = new CompoundTag();
            z.putInt("id", zone.id());
            z.putString("name", zone.name());
            zones.add(z);
        }
        out.put("zones", zones); // choices for the work area (M13c)
        Map<UUID, String> professions = new HashMap<>();
        JobService.workplaces(player.level(), village).forEach(w -> professions.put(w.buildingId(), w.profession()));
        ListTag list = new ListTag();
        for (Building building : village.buildings()) {
            CompoundTag b = new CompoundTag();
            b.putString("id", building.id().toString());
            b.putString("type", building.type().translationKey());
            b.putString("type_id", building.type().getSerializedName());
            b.putString("pos", building.markerPos().toShortString());
            b.putInt("min_x", building.min().getX());
            b.putInt("min_z", building.min().getZ());
            b.putInt("max_x", building.max().getX());
            b.putInt("max_z", building.max().getZ());
            b.putBoolean("valid", building.isValid());
            building.problem().ifPresent(problem -> {
                b.putString("problem", problem.reasonKey());
                b.putInt("problem_value", problem.value());
            });
            b.putInt("volume", building.interiorVolume());
            b.putInt("beds", building.beds().size());
            b.putInt("doors", building.doors().size());
            b.putInt("containers", building.containers().size());
            b.putInt("workstations", building.workstations().size());
            ListTag residents = new ListTag();
            ListTag workers = new ListTag();
            for (ResidentRecord record : village.residents()) {
                if (record.home().filter(building.id()::equals).isPresent()) {
                    residents.add(person(record));
                }
                if (record.job().filter(job -> job.workplace().equals(building.id())).isPresent()) {
                    workers.add(person(record));
                }
            }
            b.put("residents", residents);
            b.put("workers", workers);
            String profession = professions.get(building.id());
            if (building.type() == BuildingType.WORKPLACE && profession != null) {
                b.putInt("places", JobService.capacity(building, profession)); // M20b2
            }
            if (profession != null) {
                b.putString("profession", profession);
                b.putBoolean("needs_area", Professions.get(profession).map(Profession::workArea).orElse(false));
            }
            village.workAreaOf(building.id()).ifPresent(zone -> {
                b.putInt("work_area", zone.id());
                b.putString("work_area_name", zone.name());
            });
            if (village.workAreaOf(building.id()).isEmpty()) { // M21d: herders use the nearest marked pasture
                HerdGoal.autoPasture(village, building, profession)
                        .ifPresent(pasture -> b.putString("auto_area", pasture.markerPos().toShortString()));
            }
            list.add(b);
        }
        out.put("list", list);
    }

    private static CompoundTag person(ResidentRecord record) {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", record.id().toString());
        tag.putString("name", record.fullName());
        return tag;
    }

    /** Adds the focus and the container contents of one building to a book snapshot. */
    static void addFocus(ServerLevel level, Village village, UUID buildingId, CompoundTag data) {
        Optional<Building> building = village.buildings().stream().filter(b -> b.id().equals(buildingId)).findFirst();
        if (building.isEmpty()) {
            return;
        }
        data.putString("focus_building", buildingId.toString());
        RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, level.registryAccess());
        List<ItemStack> merged = new ArrayList<>();
        int loaded = 0;
        for (BlockPos pos : building.get().containers()) {
            Container container = StorageAccess.containerAt(level, pos);
            if (container == null) {
                continue;
            }
            loaded++;
            for (int i = 0; i < container.getContainerSize(); i++) {
                add(merged, container.getItem(i));
            }
        }
        ListTag items = new ListTag();
        for (ItemStack stack : merged.subList(0, Math.min(merged.size(), MAX_CONTENT_ENTRIES))) {
            ItemStack.CODEC.encodeStart(ops, stack).result().ifPresent(items::add);
        }
        CompoundTag contents = new CompoundTag();
        contents.putString("building", buildingId.toString());
        contents.putInt("loaded_containers", loaded);
        contents.put("items", items);
        CompoundTag buildings = data.getCompoundOrEmpty("buildings");
        buildings.put("contents", contents);
        data.put("buildings", buildings);
    }

    private static void add(List<ItemStack> merged, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        for (ItemStack existing : merged) {
            if (ItemStack.isSameItemSameComponents(existing, stack)) {
                existing.setCount(existing.getCount() + stack.getCount());
                return;
            }
        }
        merged.add(stack.copy());
    }

    /** View, rescan or change the type of a building from its page in the book. */
    static void onAction(BuildingActionPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        UUID villageId;
        UUID buildingId;
        try {
            villageId = UUID.fromString(payload.villageId());
            buildingId = UUID.fromString(payload.buildingId());
        } catch (IllegalArgumentException invalid) {
            return;
        }
        ServerLevel level = player.level();
        VillageManager manager = VillageManager.get(level);
        Optional<Village> village = manager.byId(villageId).filter(v -> VillageBookService.maySee(player, v));
        Optional<Building> building = village.flatMap(v -> v.buildings().stream().filter(b -> b.id().equals(buildingId)).findFirst());
        if (building.isEmpty()) {
            return;
        }
        String action = payload.action();
        if (action.startsWith("area:") && village.get().mayManage(player.getUUID())) {
            setWorkArea(manager, village.get(), buildingId, action.substring(5));
        } else if (!action.equals("view") && village.get().mayManage(player.getUUID())) {
            change(level, player, building.get().markerPos(), action);
        }
        VillageBookService.openBuilding(player, manager.byId(villageId).orElse(village.get()), buildingId);
    }

    /** {@code area:<zone id>} assigns one of the village's zones as work area, {@code area:none} removes it (M13c). */
    private static void setWorkArea(VillageManager manager, Village village, UUID buildingId, String zone) {
        if (zone.equals("none")) {
            manager.update(village.withWorkArea(buildingId, Optional.empty()));
            return;
        }
        try {
            int zoneId = Integer.parseInt(zone);
            if (village.zones().stream().anyMatch(z -> z.id() == zoneId)) {
                manager.update(village.withWorkArea(buildingId, Optional.of(zoneId)));
            }
        } catch (NumberFormatException invalid) {
            // ignore: the value came from a client
        }
    }

    private static void change(ServerLevel level, ServerPlayer player, BlockPos marker, String action) {
        BlockState state = level.isLoaded(marker) ? level.getBlockState(marker) : null;
        if (state == null || !state.is(ModBlocks.BUILDING_MARKER.get())) {
            player.sendOverlayMessage(Component.translatable("villagelife.book.building.not_loaded"));
            return;
        }
        BuildingType type = state.getValue(BuildingMarkerBlock.TYPE);
        if (action.startsWith("type:")) {
            String wanted = action.substring(5);
            for (BuildingType candidate : BuildingType.values()) {
                if (candidate.getSerializedName().equals(wanted)) {
                    type = candidate;
                }
            }
            if (type != state.getValue(BuildingMarkerBlock.TYPE)) {
                level.setBlockAndUpdate(marker, state.setValue(BuildingMarkerBlock.TYPE, type));
            }
        } else if (!action.equals("rescan")) {
            return;
        }
        BuildingService.scanAndRegister(level, marker, type, player);
    }
}
