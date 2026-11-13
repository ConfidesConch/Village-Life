package io.github.confidesconch.villagelife.social;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import io.github.confidesconch.villagelife.resident.ResidentEntity;
import io.github.confidesconch.villagelife.resident.ResidentRecord;
import io.github.confidesconch.villagelife.resident.ResidentService;
import io.github.confidesconch.villagelife.social.RelationshipRules.Stage;
import io.github.confidesconch.villagelife.social.RelationshipStore.Relation;
import io.github.confidesconch.villagelife.village.Village;
import io.github.confidesconch.villagelife.village.NotificationTopic;
import io.github.confidesconch.villagelife.village.VillageManager;
import io.github.confidesconch.villagelife.village.VillageService;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Relationships in the village (M17, owner's choice "1a 2a 3a"): every 5 s residents standing close
 * together may talk – shown by symbols over their heads (♪/✦ talk, ♥ friends, angry cloud quarrel), no
 * chat text. Talks bring them closer; grumpy residents sometimes quarrel. Friends nearby lift the mood,
 * a quarrel in the last day lowers it.
 */
public final class SocialService {
    private SocialService() {}

    static final int UPDATE_INTERVAL_TICKS = 100;
    private static final double TALK_DISTANCE_SQR = 4.0 * 4.0;
    private static final double FRIEND_DISTANCE_SQR = 8.0 * 8.0;
    private static final double TALK_CHANCE = 0.3;
    private static final int SHOWN_RELATIONS = 5;

    /** When each resident last quarrelled; only for the mood, so it may be forgotten on restart. */
    private static final Map<UUID, Long> LAST_QUARREL = new HashMap<>();
    /** Until when a resident is "sociable" after an evening in the tavern (M17b); not saved. */
    private static final Map<UUID, Long> SOCIABLE_UNTIL = new HashMap<>();
    /** Until when a resident enjoys the bard's music (M23); not saved. */
    private static final Map<UUID, Long> MUSIC_UNTIL = new HashMap<>();

    /** Forgets everything kept in memory for the stopped server (bug review 08.11.2026). */
    public static void clear() {
        LAST_QUARREL.clear();
        SOCIABLE_UNTIL.clear();
        MUSIC_UNTIL.clear();
        TavernPlaces.clear(); // M21e
    }

    /** Called by the tavern visit: the resident is sociable until the next morning. */
    static void markSociable(UUID resident, long untilGameTime) {
        SOCIABLE_UNTIL.put(resident, untilGameTime);
    }

    /** Called by the bard (M23): the resident heard music and is in a good mood until the next morning. */
    static void markMusic(UUID resident, long untilGameTime) {
        MUSIC_UNTIL.put(resident, untilGameTime);
    }

    static boolean heardMusic(UUID resident, long gameTime) {
        return MUSIC_UNTIL.getOrDefault(resident, 0L) > gameTime;
    }

    static boolean isSociable(UUID resident, long gameTime) {
        return SOCIABLE_UNTIL.getOrDefault(resident, 0L) > gameTime;
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        for (ServerLevel level : event.getServer().getAllLevels()) {
            VillageManager manager = VillageManager.get(level);
            if (manager.all().isEmpty()) {
                continue;
            }
            long now = level.getGameTime();
            for (Village village : List.copyOf(manager.all())) {
                if (Math.floorMod(now + village.id().hashCode(), UPDATE_INTERVAL_TICKS) == 0) {
                    update(level, village, now);
                }
            }
            if (now % 24000 == 12000) { // once a day: forget residents who left
                Set<UUID> residents = new HashSet<>();
                manager.all().forEach(v -> v.residents().forEach(r -> residents.add(r.id())));
                RelationshipStore.get(level).retain(residents);
                clearLostPartners(manager);
                // these lists are shared by all dimensions (bug review 13.11.2026): keep everybody's residents
                Set<UUID> everywhere = new HashSet<>();
                for (ServerLevel other : event.getServer().getAllLevels()) {
                    VillageManager.get(other).all().forEach(v -> v.residents().forEach(r -> everywhere.add(r.id())));
                }
                LAST_QUARREL.keySet().retainAll(everywhere);
                SOCIABLE_UNTIL.keySet().retainAll(everywhere);
                MUSIC_UNTIL.keySet().retainAll(everywhere);
            }
        }
    }

    private static void update(ServerLevel level, Village village, long now) {
        List<ResidentEntity> residents = ResidentService.loadedResidents(level, village).stream()
                .filter(r -> !r.isSleeping()).toList();
        RelationshipStore store = RelationshipStore.get(level);
        Set<UUID> talked = new HashSet<>();
        for (int i = 0; i < residents.size(); i++) {
            ResidentEntity a = residents.get(i);
            for (int j = i + 1; j < residents.size() && !talked.contains(a.getUUID()); j++) {
                ResidentEntity b = residents.get(j);
                if (!talked.contains(b.getUUID()) && a.distanceToSqr(b) < TALK_DISTANCE_SQR
                        && level.getRandom().nextDouble() < TALK_CHANCE) {
                    talk(level, store, village, a, b, now);
                    talked.add(a.getUUID());
                    talked.add(b.getUUID());
                }
            }
        }
        for (ResidentEntity resident : residents) {
            int friends = 0;
            for (ResidentEntity other : residents) {
                if (other != resident && resident.distanceToSqr(other) < FRIEND_DISTANCE_SQR
                        && RelationshipRules.stage(store.points(resident.getUUID(), other.getUUID())).isFriend()) {
                    friends++;
                }
            }
            Long quarrel = LAST_QUARREL.get(resident.getUUID());
            boolean recent = quarrel != null && now - quarrel < RelationshipRules.QUARREL_MEMORY_TICKS;
            resident.setSocialMood(RelationshipRules.socialMood(friends, recent, isSociable(resident.getUUID(), now),
                    heardMusic(resident.getUUID(), now)));
        }
    }

    private static void talk(ServerLevel level, RelationshipStore store, Village village, ResidentEntity a, ResidentEntity b,
                             long now) {
        boolean quarrel = RelationshipRules.isQuarrel(a.needsSatisfaction(), b.needsSatisfaction(), level.getRandom().nextDouble());
        int points = RelationshipRules.after(store.points(a.getUUID(), b.getUUID()), quarrel);
        store.set(village.id(), a.getUUID(), b.getUUID(), points);
        if (!quarrel) {
            maybeCouple(level, village.id(), a, b, points, now);
        }
        a.getLookControl().setLookAt(b, 30.0F, 30.0F);
        b.getLookControl().setLookAt(a, 30.0F, 30.0F);
        if (quarrel) {
            LAST_QUARREL.put(a.getUUID(), now);
            LAST_QUARREL.put(b.getUUID(), now);
        }
        SimpleParticleType symbol = quarrel ? ParticleTypes.ANGRY_VILLAGER
                : RelationshipRules.stage(points).isFriend() ? ParticleTypes.HEART
                : level.getRandom().nextBoolean() ? ParticleTypes.NOTE : ParticleTypes.HAPPY_VILLAGER;
        for (ResidentEntity speaker : List.of(a, b)) {
            level.sendParticles(symbol, speaker.getX(), speaker.getY() + speaker.getBbHeight() + 0.4, speaker.getZ(),
                    quarrel ? 2 : 3, 0.25, 0.15, 0.25, 0.0);
        }
    }

    /** Two single adult best friends may become a couple after a happy talk (M18, "1a"). */
    private static void maybeCouple(ServerLevel level, UUID villageId, ResidentEntity a, ResidentEntity b, int points, long now) {
        VillageManager manager = VillageManager.get(level);
        Village village = manager.byId(villageId).orElse(null);
        ResidentRecord ra = village == null ? null : village.residentById(a.getUUID()).orElse(null);
        ResidentRecord rb = village == null ? null : village.residentById(b.getUUID()).orElse(null);
        if (ra == null || rb == null) {
            return;
        }
        boolean singleAdults = ra.partner().isEmpty() && rb.partner().isEmpty() && !ra.isChild(now) && !rb.isChild(now);
        if (!RelationshipRules.becomesCouple(points, singleAdults, level.getRandom().nextDouble())) {
            return;
        }
        manager.update(village.withResident(ra.withPartner(java.util.Optional.of(rb.id())))
                .withResident(rb.withPartner(java.util.Optional.of(ra.id()))));
        for (ResidentEntity partner : List.of(a, b)) {
            level.sendParticles(ParticleTypes.HEART, partner.getX(), partner.getY() + partner.getBbHeight() + 0.4, partner.getZ(),
                    10, 0.5, 0.4, 0.5, 0.0);
        }
        VillageService.tellManagers(level.getServer(), village, NotificationTopic.FAMILY, Component.translatable("villagelife.family.couple",
                ra.fullName(), rb.fullName()).withStyle(ChatFormatting.LIGHT_PURPLE));
    }

    /** Debug ({@code /villagelife debug couple}): the first two single adults become best friends and a couple. */
    public static boolean debugCouple(ServerLevel level, Village village) {
        long now = level.getGameTime();
        List<ResidentRecord> singles = village.residents().stream()
                .filter(r -> r.partner().isEmpty() && !r.isChild(now)).limit(2).toList();
        if (singles.size() < 2) {
            return false;
        }
        ResidentRecord a = singles.get(0);
        ResidentRecord b = singles.get(1);
        RelationshipStore.get(level).set(village.id(), a.id(), b.id(), 70);
        VillageManager.get(level).update(village.withResident(a.withPartner(java.util.Optional.of(b.id())))
                .withResident(b.withPartner(java.util.Optional.of(a.id()))));
        VillageService.tellManagers(level.getServer(), village, NotificationTopic.FAMILY, Component.translatable("villagelife.family.couple",
                a.fullName(), b.fullName()).withStyle(ChatFormatting.LIGHT_PURPLE));
        return true;
    }

    /** Partners who left the village (moved away, died) are single again. */
    private static void clearLostPartners(VillageManager manager) {
        for (Village village : List.copyOf(manager.all())) {
            Village updated = village;
            for (ResidentRecord record : village.residents()) {
                if (record.partner().isPresent() && village.residentById(record.partner().get()).isEmpty()) {
                    updated = updated.withResident(record.withPartner(java.util.Optional.empty()));
                }
            }
            if (updated != village) {
                manager.update(updated);
            }
        }
    }

    // ---- book section --------------------------------------------------------------------------

    /**
     * Section "relations": for each resident its closest relationships (and dislikes), and how many
     * friendships and dislikes the village has.
     */
    public static void write(ServerPlayer player, Village village, CompoundTag out) {
        RelationshipStore store = RelationshipStore.get(player.level());
        List<Relation> all = store.ofVillage(village.id());
        int friendships = 0;
        int dislikes = 0;
        Map<UUID, List<Relation>> byResident = new HashMap<>();
        for (Relation relation : all) {
            Stage stage = RelationshipRules.stage(relation.points());
            friendships += stage.isFriend() ? 1 : 0;
            dislikes += stage == Stage.DISLIKE ? 1 : 0;
            byResident.computeIfAbsent(relation.a(), k -> new java.util.ArrayList<>()).add(relation);
            byResident.computeIfAbsent(relation.b(), k -> new java.util.ArrayList<>()).add(relation);
        }
        out.putInt("friendships", friendships);
        out.putInt("dislikes", dislikes);
        CompoundTag residents = new CompoundTag();
        for (ResidentRecord record : village.residents()) {
            List<Relation> relations = byResident.getOrDefault(record.id(), List.of()).stream()
                    .sorted(Comparator.comparingInt((Relation r) -> -Math.abs(r.points())))
                    .limit(SHOWN_RELATIONS).toList();
            ListTag list = new ListTag();
            for (Relation relation : relations) {
                UUID other = relation.other(record.id());
                CompoundTag r = new CompoundTag();
                r.putString("id", other.toString());
                r.putString("name", village.residentById(other).map(ResidentRecord::fullName).orElse("?"));
                r.putInt("points", relation.points());
                r.putString("stage", RelationshipRules.stage(relation.points()).name().toLowerCase(java.util.Locale.ROOT));
                list.add(r);
            }
            residents.put(record.id().toString(), list);
        }
        out.put("residents", residents);
    }
}
