package io.github.confidesconch.villagelife.book;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.github.confidesconch.villagelife.profession.JobAllocator.Workplace;
import io.github.confidesconch.villagelife.profession.JobPriorities;
import io.github.confidesconch.villagelife.profession.JobService;
import io.github.confidesconch.villagelife.profession.Professions;
import io.github.confidesconch.villagelife.resident.schedule.LawRules;
import io.github.confidesconch.villagelife.village.LawSet;
import io.github.confidesconch.villagelife.village.NotificationSettings;
import io.github.confidesconch.villagelife.village.NotificationTopic;
import io.github.confidesconch.villagelife.village.Village;
import io.github.confidesconch.villagelife.village.VillageLaws;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/**
 * Data and changes of the "Management" chapter (M19a): the job priorities of the village (owner and
 * managers sort them), the reader's own message topics and the village laws (M19b).
 */
final class ManagementSection {
    private ManagementSection() {}

    static void write(ServerPlayer player, Village village, CompoundTag out) {
        out.putBoolean("can_manage", village.mayManage(player.getUUID()));
        Map<String, int[]> counts = new HashMap<>(); // profession -> {workplaces, workers}
        for (Workplace workplace : JobService.places(player.level(), village)) { // M20b2: places, not buildings
            counts.computeIfAbsent(workplace.profession(), k -> new int[2])[0]++;
        }
        village.residents().forEach(r -> r.job().ifPresent(job -> counts.computeIfAbsent(job.profession(), k -> new int[2])[1]++));
        ListTag priorities = new ListTag();
        for (String id : JobService.ranking(village)) {
            CompoundTag p = new CompoundTag();
            p.putString("id", id);
            p.putBoolean("essential", Professions.get(id).map(pr -> pr.essential()).orElse(false));
            int[] c = counts.getOrDefault(id, new int[2]);
            p.putInt("places", c[0]);
            p.putInt("workers", c[1]);
            priorities.add(p);
        }
        out.put("priorities", priorities);
        NotificationSettings settings = NotificationSettings.get(player.level().getServer());
        CompoundTag notify = new CompoundTag();
        for (NotificationTopic topic : NotificationTopic.values()) {
            notify.putBoolean(topic.id(), settings.wants(player.getUUID(), topic));
        }
        out.put("notify", notify);
        writeLaws(player, village, out);
    }

    /** M19b: the laws as planned (in force or from tomorrow), whether a change is waiting, the next rest day. */
    private static void writeLaws(ServerPlayer player, Village village, CompoundTag out) {
        long today = LawRules.day(player.level().getOverworldClockTime());
        VillageLaws laws = village.settings().laws();
        LawSet planned = laws.planned();
        CompoundTag l = new CompoundTag();
        l.putBoolean("rest_day", planned.restDay());
        l.putInt("rest_interval", planned.restInterval());
        l.putBoolean("curfew", planned.curfew());
        l.putInt("curfew_hour", planned.curfewHour());
        l.putBoolean("long_evenings", planned.longEvenings());
        l.putInt("long_evening_hours", planned.longEveningHours());
        l.putBoolean("pending", laws.isPending(today));
        l.putInt("rest_in", LawRules.daysUntilRest(laws.effective(today), today));
        l.putInt("work_end", village.settings().workEnd());
        out.put("laws", l);
    }

    /** Value {@code <law key>:<value>}; the change applies from the next morning (owner's choice "3a"). */
    static Village withLaw(ServerPlayer player, Village village, String value) {
        int colon = value.indexOf(':');
        if (colon < 0) {
            return null;
        }
        long today = LawRules.day(player.level().getOverworldClockTime());
        return village.settings().laws().propose(value.substring(0, colon), value.substring(colon + 1), today)
                .map(laws -> village.withSettings(village.settings().withLaws(laws))).orElse(null);
    }

    /** Value {@code <profession id>|up} or {@code |down}; unknown professions are ignored. */
    static Village withPriority(Village village, String value) {
        int bar = value.lastIndexOf('|');
        if (bar < 0 || Identifier.tryParse(value.substring(0, bar)) == null) {
            return null;
        }
        String direction = value.substring(bar + 1);
        if (!direction.equals("up") && !direction.equals("down")) {
            return null;
        }
        List<String> ranking = JobService.ranking(village);
        List<String> moved = JobPriorities.move(ranking, value.substring(0, bar), direction.equals("up") ? -1 : 1);
        return moved == ranking ? null : village.withJobPriorities(moved);
    }

    /** Value {@code <topic>:true|false}; changes only the reader's own settings. */
    static void setNotification(ServerPlayer player, String value) {
        int colon = value.lastIndexOf(':');
        if (colon < 0) {
            return;
        }
        String on = value.substring(colon + 1);
        if (!on.equals("true") && !on.equals("false")) {
            return;
        }
        NotificationTopic.byId(value.substring(0, colon)).ifPresent(topic ->
                NotificationSettings.get(player.level().getServer()).set(player.getUUID(), topic, Boolean.parseBoolean(on)));
    }
}
