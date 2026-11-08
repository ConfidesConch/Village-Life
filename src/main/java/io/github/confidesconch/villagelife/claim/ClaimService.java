package io.github.confidesconch.villagelife.claim;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import io.github.confidesconch.villagelife.core.config.SyncedConfig;
import io.github.confidesconch.villagelife.core.registry.ModBlocks;
import io.github.confidesconch.villagelife.core.util.ModProfiler;
import io.github.confidesconch.villagelife.friend.FriendService;
import io.github.confidesconch.villagelife.village.Village;
import io.github.confidesconch.villagelife.village.VillageManager;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server side of the claims (M11f): claiming/freeing chunks from the map, protecting claimed chunks
 * against breaking and placing blocks by other players, and sending the claims to the clients.
 */
public final class ClaimService {
    private ClaimService() {}

    /** Without a village, chunks can only be claimed this close to the player (in chunks). */
    private static final int MAX_DISTANCE_CHUNKS = 64;

    // ---- protection --------------------------------------------------------------------------

    static void onBreak(BreakBlockEvent event) {
        ModProfiler.time(ModProfiler.CLAIMS, () -> checkBreak(event));
    }

    static void onPlace(BlockEvent.EntityPlaceEvent event) {
        ModProfiler.time(ModProfiler.CLAIMS, () -> checkPlace(event));
    }

    private static void checkBreak(BreakBlockEvent event) {
        if (event.getLevel() instanceof ServerLevel level && event.getState().is(ModBlocks.VILLAGE_CENTER.get())
                && !mayBreakCenter(level, event.getPos(), event.getPlayer())) {
            event.setNotifyClient(true);
            event.setCanceled(true);
            return;
        }
        if (event.getLevel() instanceof ServerLevel level && !mayBuild(level, event.getPos(), event.getPlayer())) {
            event.setNotifyClient(true);
            event.setCanceled(true);
        }
    }

    private static void checkPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel() instanceof ServerLevel level && event.getEntity() instanceof Player player
                && !mayBuild(level, event.getPos(), player)) {
            event.setCanceled(true);
        }
    }

    /** Only the owner (or an admin) may break a village center – not even managers (M11g2). */
    private static boolean mayBreakCenter(ServerLevel level, BlockPos pos, Player player) {
        if (player == null || isAdmin(player)) {
            return true;
        }
        Optional<Village> village = VillageManager.get(level).findAt(pos).filter(v -> v.center().equals(pos));
        boolean allowed = village.isEmpty() || village.get().owner().equals(player.getUUID());
        if (!allowed && player instanceof ServerPlayer serverPlayer) {
            serverPlayer.sendOverlayMessage(Component.translatable("villagelife.claim.center_protected", village.get().ownerName()));
        }
        return allowed;
    }

    private static boolean mayBuild(ServerLevel level, BlockPos pos, Player player) {
        if (player == null) {
            return true;
        }
        Optional<Claim> claim = ClaimStore.get(level).at(pos.getX() >> 4, pos.getZ() >> 4);
        boolean friend = claim.isPresent()
                && FriendService.areFriends(level.getServer(), claim.get().owner(), player.getUUID());
        boolean allowed = ClaimRules.mayBuild(claim.map(Claim::owner), player.getUUID(), isAdmin(player), friend);
        if (!allowed && player instanceof ServerPlayer serverPlayer) {
            serverPlayer.sendOverlayMessage(Component.translatable("villagelife.claim.protected", claim.get().ownerName()));
        }
        return allowed;
    }

    static boolean isAdmin(Player player) {
        return player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
    }

    // ---- actions from the map ----------------------------------------------------------------

    static void onAction(ClaimActionPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        ServerLevel level = player.level();
        boolean changed = switch (payload.action()) {
            case "claim" -> report(player, claim(level, player, payload.chunkX(), payload.chunkZ()));
            case "unclaim" -> unclaim(level, player, payload.chunkX(), payload.chunkZ());
            case "claim_village" -> claimVillage(level, player, payload.villageId());
            default -> false; // "sync": just send
        };
        if (changed) {
            level.players().forEach(other -> send(other));
        } else {
            send(player);
        }
    }

    /**
     * Claims a chunk. Inside a village the player manages (owner or manager, M11g2) the chunk is
     * claimed for the village owner and counts against his village limit.
     */
    private static ClaimRules.Result claim(ServerLevel level, ServerPlayer player, int chunkX, int chunkZ) {
        ClaimStore store = ClaimStore.get(level);
        VillageManager villages = VillageManager.get(level);
        Optional<Village> village = villages.findAt(center(chunkX, chunkZ));
        boolean managed = village.map(v -> v.mayManage(player.getUUID())).orElse(false);
        boolean foreign = village.isPresent() && !managed;
        UUID claimant = managed ? village.get().owner() : player.getUUID();
        String claimantName = managed ? village.get().ownerName() : player.getName().getString();
        int distance = Math.max(Math.abs(chunkX - (player.getBlockX() >> 4)), Math.abs(chunkZ - (player.getBlockZ() >> 4)));
        ClaimRules.Result result = ClaimRules.check(store.at(chunkX, chunkZ).map(Claim::owner), claimant,
                managed, foreign, distance, MAX_DISTANCE_CHUNKS, usage(level, claimant), isAdmin(player));
        if (result == ClaimRules.Result.OK) {
            store.put(new Claim(chunkX, chunkZ, claimant, claimantName));
        }
        return result;
    }

    /** Tells the player why a claim failed; true if something changed. */
    private static boolean report(ServerPlayer player, ClaimRules.Result result) {
        if (result != ClaimRules.Result.OK && result != ClaimRules.Result.ALREADY_YOURS) {
            player.sendOverlayMessage(Component.translatable("villagelife.claim.result." + result.name().toLowerCase()));
        }
        return result == ClaimRules.Result.OK;
    }

    private static boolean unclaim(ServerLevel level, ServerPlayer player, int chunkX, int chunkZ) {
        ClaimStore store = ClaimStore.get(level);
        Optional<Claim> claim = store.at(chunkX, chunkZ);
        boolean villageClaim = claim.isPresent() && VillageManager.get(level).findAt(center(chunkX, chunkZ))
                .map(v -> v.mayManage(player.getUUID()) && v.owner().equals(claim.get().owner())).orElse(false);
        if (claim.isPresent() && (villageClaim || ClaimRules.mayUnclaim(claim.get().owner(), player.getUUID(), isAdmin(player)))) {
            store.remove(chunkX, chunkZ);
            return true;
        }
        return false;
    }

    /** Claims the free chunks of the player's village, nearest to the center first, until the limit is reached. */
    private static boolean claimVillage(ServerLevel level, ServerPlayer player, String villageId) {
        UUID id;
        try {
            id = UUID.fromString(villageId);
        } catch (IllegalArgumentException invalid) {
            return false;
        }
        Optional<Village> village = VillageManager.get(level).byId(id).filter(v -> v.mayManage(player.getUUID()));
        if (village.isEmpty()) {
            return false;
        }
        BlockPos center = village.get().center();
        int radiusChunks = (village.get().radius() >> 4) + 1;
        int cx = center.getX() >> 4;
        int cz = center.getZ() >> 4;
        List<int[]> chunks = new java.util.ArrayList<>();
        for (int x = cx - radiusChunks; x <= cx + radiusChunks; x++) {
            for (int z = cz - radiusChunks; z <= cz + radiusChunks; z++) {
                BlockPos mid = center(x, z);
                if (village.get().area().contains(mid.getX(), mid.getZ())) {
                    chunks.add(new int[]{x, z});
                }
            }
        }
        chunks.sort(Comparator.comparingInt(c -> (c[0] - cx) * (c[0] - cx) + (c[1] - cz) * (c[1] - cz)));
        int claimed = 0;
        for (int[] c : chunks) {
            ClaimRules.Result result = claim(level, player, c[0], c[1]);
            if (result == ClaimRules.Result.OK) {
                claimed++;
            } else if (result == ClaimRules.Result.LIMIT) {
                break;
            }
        }
        player.sendOverlayMessage(Component.translatable("villagelife.claim.village_claimed", claimed));
        return claimed > 0;
    }

    private static BlockPos center(int chunkX, int chunkZ) {
        return new BlockPos((chunkX << 4) + 8, 64, (chunkZ << 4) + 8);
    }

    /** The player's claims in this dimension and his limits (e.g. for the character page). */
    public static ClaimRules.Usage usageOf(ServerLevel level, UUID player) {
        return usage(level, player);
    }

    /** The player's claims in this dimension, split into "inside an own village" and "elsewhere". */
    private static ClaimRules.Usage usage(ServerLevel level, UUID player) {
        VillageManager villages = VillageManager.get(level);
        int inVillage = 0;
        int outside = 0;
        for (Claim claim : ClaimStore.get(level).ownedBy(player)) {
            boolean own = villages.findAt(center(claim.chunkX(), claim.chunkZ()))
                    .map(v -> v.owner().equals(player)).orElse(false);
            if (own) {
                inVillage++;
            } else {
                outside++;
            }
        }
        return new ClaimRules.Usage(outside, inVillage, SyncedConfig.CLAIMS_PER_PLAYER.get(),
                SyncedConfig.CLAIMS_IN_OWN_VILLAGE.get());
    }

    // ---- sync --------------------------------------------------------------------------------

    static void send(ServerPlayer player) {
        ServerLevel level = player.level();
        ListTag list = new ListTag();
        for (Claim claim : ClaimStore.get(level).all()) {
            CompoundTag tag = new CompoundTag();
            tag.putInt("x", claim.chunkX());
            tag.putInt("z", claim.chunkZ());
            tag.putString("owner", claim.owner().toString());
            tag.putString("name", claim.ownerName());
            list.add(tag);
        }
        ClaimRules.Usage usage = usage(level, player.getUUID());
        CompoundTag data = new CompoundTag();
        data.put("list", list);
        data.putString("dimension", level.dimension().identifier().toString());
        data.putInt("outside", usage.outside());
        data.putInt("in_village", usage.inVillage());
        data.putInt("limit_outside", usage.limitOutside());
        data.putInt("limit_village", usage.limitVillage());
        data.putBoolean("admin", isAdmin(player));
        PacketDistributor.sendToPlayer(player, new ClaimSyncPayload(data));
    }
}
