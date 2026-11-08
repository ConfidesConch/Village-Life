package io.github.confidesconch.villagelife.book;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import io.github.confidesconch.villagelife.profession.Profession;
import io.github.confidesconch.villagelife.profession.Professions;
import io.github.confidesconch.villagelife.resident.ResidentEntity;
import io.github.confidesconch.villagelife.resident.ResidentRecord;
import io.github.confidesconch.villagelife.resident.ResidentService;
import io.github.confidesconch.villagelife.resident.needs.NeedsRules;
import io.github.confidesconch.villagelife.village.Village;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Data of the "Residents" chapter (M11b): every resident from the village file, plus live values
 * (needs, activity, tool, inventory) for residents that are currently loaded.
 */
final class ResidentsSection {
    private ResidentsSection() {}

    static void write(ServerPlayer player, Village village, CompoundTag out) {
        ServerLevel level = player.level();
        long dayTime = level.getOverworldClockTime();
        Map<UUID, ResidentEntity> loaded = new HashMap<>();
        for (ResidentEntity entity : ResidentService.loadedResidents(level, village)) {
            loaded.put(entity.getUUID(), entity);
        }
        RegistryOps<net.minecraft.nbt.Tag> ops = RegistryOps.create(NbtOps.INSTANCE, level.registryAccess());
        ListTag list = new ListTag();
        for (ResidentRecord record : village.residents()) {
            CompoundTag r = new CompoundTag();
            r.putString("id", record.id().toString());
            r.putString("name", record.fullName());
            r.putLong("joined_day", record.joinedGameTime() / 24000L + 1);
            record.bed().ifPresent(bed -> r.putString("bed", bed.toShortString()));
            record.job().ifPresent(job -> r.putString("job", job.translationKey()));
            r.putBoolean("defender", record.job().flatMap(job -> Professions.get(job.profession()))
                    .map(Profession::defender).orElse(false)); // M21b4: filter "guards" in the window
            record.partner().flatMap(village::residentById) // M18
                    .ifPresent(partner -> r.putString("partner", partner.fullName()));
            long now = level.getGameTime();
            r.putInt("education", record.education()); // M18c
            if (record.isChild(now)) {
                r.putInt("grows_up_in", io.github.confidesconch.villagelife.family.FamilyRules.daysUntilGrownUp(
                        record.grownUpGameTime(), now));
            }
            r.putString("parents", String.join(", ", record.parents().stream()
                    .map(id -> village.residentById(id).map(ResidentRecord::firstName).orElse("?")).toList()));
            r.putString("children", String.join(", ", village.residents().stream()
                    .filter(other -> other.parents().contains(record.id())).map(ResidentRecord::firstName).toList()));
            ResidentEntity entity = loaded.get(record.id());
            if (entity != null) {
                int satisfaction = entity.satisfaction();
                r.putBoolean("loaded", true);
                r.putDouble("food", entity.needs().food());
                r.putBoolean("rested", entity.needs().isRested(dayTime, player.level().getGameTime()));
                r.putBoolean("sleeping", entity.isSleeping());
                r.putInt("satisfaction", satisfaction);
                r.putInt("social", entity.socialMood()); // M17: mood from friends and quarrels
                r.putInt("laws", entity.brain().lawMood()); // M19b: mood from the village laws
                r.putString("mood", NeedsRules.mood(satisfaction).name().toLowerCase(java.util.Locale.ROOT));
                r.putString("activity", entity.brain().activity().translationKey());
                ListTag inventory = new ListTag();
                for (ItemStack stack : entity.carriedItems()) {
                    ItemStack.CODEC.encodeStart(ops, stack).result().ifPresent(inventory::add);
                }
                r.put("inventory", inventory);
            }
            list.add(r);
        }
        out.put("list", list);
    }
}
