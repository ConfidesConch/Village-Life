package io.github.confidesconch.villagelife.book;

import java.util.UUID;

import io.github.confidesconch.villagelife.claim.ClaimRules;
import io.github.confidesconch.villagelife.claim.ClaimService;
import io.github.confidesconch.villagelife.core.config.SyncedConfig;
import io.github.confidesconch.villagelife.friend.FriendService;
import io.github.confidesconch.villagelife.resident.ResidentService;
import io.github.confidesconch.villagelife.village.Village;
import io.github.confidesconch.villagelife.village.VillageManager;
import io.github.confidesconch.villagelife.waypoint.WaypointStore;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Data of the "Character" chapter (M11h), independent of the open village: the player's numbers
 * (claims, waypoints, friends, residents) and all villages he owns or manages, in every dimension.
 */
final class CharacterSection {
    private CharacterSection() {}

    static void write(ServerPlayer player, CompoundTag out) {
        MinecraftServer server = player.level().getServer();
        UUID me = player.getUUID();
        ClaimRules.Usage claims = ClaimService.usageOf(player.level(), me);
        out.putInt("claims_general", claims.generalUsed());
        out.putInt("claims_general_limit", claims.limitOutside());
        out.putInt("claims_village", Math.min(claims.inVillage(), claims.limitVillage()));
        out.putInt("claims_village_limit", claims.limitVillage());
        out.putInt("waypoints", WaypointStore.get(server).of(me).size());
        out.putInt("waypoints_limit", SyncedConfig.MAX_WAYPOINTS_PER_PLAYER.get());
        out.putInt("friends", FriendService.friendsOf(server, me).size());
        out.putInt("friends_limit", SyncedConfig.MAX_FRIENDS.get());
        ListTag villages = new ListTag();
        int residents = 0;
        for (ServerLevel level : server.getAllLevels()) {
            for (Village village : VillageManager.get(level).all()) {
                if (!village.mayManage(me)) {
                    continue;
                }
                CompoundTag v = new CompoundTag();
                v.putString("id", village.id().toString());
                v.putString("name", village.customName().orElse(""));
                v.putString("owner_name", village.ownerName());
                v.putBoolean("owner", village.owner().equals(me));
                v.putInt("residents", village.residents().size());
                v.putInt("satisfaction", ResidentService.averageSatisfaction(level, village).orElse(-1));
                v.putString("dimension", level.dimension().identifier().toString());
                v.putBoolean("here", level == player.level());
                v.putInt("x", village.center().getX());
                v.putInt("z", village.center().getZ());
                villages.add(v);
                residents += village.residents().size();
            }
        }
        out.put("villages", villages);
        out.putInt("residents", residents);
    }
}
