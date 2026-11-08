package io.github.confidesconch.villagelife.book;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import io.github.confidesconch.villagelife.convoy.ConvoyStore;
import io.github.confidesconch.villagelife.defense.AlarmService;
import io.github.confidesconch.villagelife.defense.DefenseRules;
import io.github.confidesconch.villagelife.defense.RaidService;
import io.github.confidesconch.villagelife.profession.Profession;
import io.github.confidesconch.villagelife.profession.Professions;
import io.github.confidesconch.villagelife.resident.ResidentEntity;
import io.github.confidesconch.villagelife.resident.ResidentRecord;
import io.github.confidesconch.villagelife.resident.ResidentService;
import io.github.confidesconch.villagelife.resident.ai.ResidentHooks;
import io.github.confidesconch.villagelife.village.EventLog;
import io.github.confidesconch.villagelife.village.NotificationTopic;
import io.github.confidesconch.villagelife.village.Village;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Data of the window's "Defense" area (M21b9), read only: attack and alarm, whether attacks are on,
 * how strong the defenders are and what an attack would bring, every defender with its state (on duty,
 * night watch, knocked out, escorting a convoy) and the last fights from the event log.
 */
final class DefenseSection {
    private DefenseSection() {}

    private static final int MAX_FIGHTS = 12;

    static void write(ServerPlayer player, Village village, CompoundTag out) {
        ServerLevel level = player.level();
        out.putBoolean("can_manage", village.mayManage(player.getUUID()));
        out.putBoolean("under_attack", RaidService.isUnderAttack(village.id()));
        out.putBoolean("manual_alarm", AlarmService.isManual(village.id()));
        out.putBoolean("attacks", village.settings().attacks());
        out.putBoolean("attacks_possible", village.stage() >= 1); // bandits come from stage "village"
        double power = RaidService.defenderPower(village);
        int bandits = DefenseRules.banditCount(power);
        int banditLevel = DefenseRules.banditLevel(power);
        out.putDouble("power", power);
        out.putInt("bandits", bandits);
        out.putInt("bandit_level", banditLevel);
        out.putInt("crossbows", DefenseRules.crossbowBandits(bandits, banditLevel));

        Map<UUID, ResidentEntity> loaded = new HashMap<>();
        ResidentService.loadedResidents(level, village).forEach(r -> loaded.put(r.getUUID(), r));
        ConvoyStore convoys = ConvoyStore.get(level.getServer());
        long now = level.getGameTime();
        ListTag defenders = new ListTag();
        for (ResidentRecord record : village.residents()) {
            String profession = record.job().map(job -> job.profession()).orElse("");
            if (!Professions.get(profession).map(Profession::defender).orElse(false)) {
                continue;
            }
            CompoundTag d = new CompoundTag();
            d.putString("id", record.id().toString());
            d.putString("name", record.fullName());
            d.putString("job", record.job().get().translationKey());
            d.putString("kind", profession.equals(DefenseRules.ARCHER) ? "archer" : profession.equals(DefenseRules.CAPTAIN) ? "captain" : "guard");
            ResidentEntity entity = loaded.get(record.id());
            if (convoys.isAway(record.id())) {
                d.putString("state", "away");
            } else if (entity == null) {
                d.putString("state", "unloaded");
            } else if (entity.isKnockedOut()) {
                d.putString("state", "knocked_out");
                d.putLong("back_in", Math.max(0, entity.knockedOutUntil() - now));
            } else {
                d.putString("state", ResidentHooks.isNightShift(entity) ? "night" : "day");
                d.putString("activity", entity.brain().activity().translationKey());
            }
            defenders.add(d);
        }
        out.put("defenders", defenders);

        var ops = player.registryAccess().createSerializationContext(NbtOps.INSTANCE);
        ListTag fights = new ListTag();
        for (EventLog.Entry entry : EventLog.get(level.getServer()).of(village.id())) {
            if (fights.size() >= MAX_FIGHTS || !entry.topic().equals(NotificationTopic.DEFENSE.id())) {
                continue;
            }
            CompoundTag f = new CompoundTag();
            f.putLong("day", entry.day());
            ComponentSerialization.CODEC.encodeStart(ops, entry.text()).result().ifPresent(text -> f.put("text", text));
            fights.add(f);
        }
        out.put("fights", fights);
    }
}
