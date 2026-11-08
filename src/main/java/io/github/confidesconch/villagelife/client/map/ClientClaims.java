package io.github.confidesconch.villagelife.client.map;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import io.github.confidesconch.villagelife.claim.Claim;
import io.github.confidesconch.villagelife.claim.ClaimActionPayload;
import io.github.confidesconch.villagelife.claim.ClaimSyncPayload;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Claimed chunks of the player's current dimension on the client (M11f): asked for after joining
 * and after every dimension change, replaced whenever a claim changes. Map and minimap read from here.
 */
public final class ClientClaims {
    private ClientClaims() {}

    /** Owner of a claimed chunk. */
    public record Entry(UUID owner, String name) {}

    private static Map<Long, Entry> claims = Map.of();
    private static ClientLevel requestedFor;
    private static int outside;
    private static int inVillage;
    private static int limitOutside;
    private static int limitVillage;
    private static boolean admin;

    /** Call every client tick: asks for the claims once per level (dimension). */
    public static void tick(Minecraft minecraft) {
        if (minecraft.player == null || minecraft.level == null || minecraft.getConnection() == null) {
            requestedFor = null;
            claims = Map.of();
            return;
        }
        if (requestedFor != minecraft.level) {
            requestedFor = minecraft.level;
            claims = Map.of();
            send("sync", 0, 0, "");
        }
    }

    public static void onSync(ClaimSyncPayload payload, IPayloadContext context) {
        CompoundTag data = payload.data();
        ListTag list = data.getListOrEmpty("list");
        Map<Long, Entry> loaded = new HashMap<>();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompoundOrEmpty(i);
            try {
                loaded.put(Claim.key(t.getIntOr("x", 0), t.getIntOr("z", 0)),
                        new Entry(UUID.fromString(t.getStringOr("owner", "")), t.getStringOr("name", "?")));
            } catch (IllegalArgumentException invalid) {
                // skip a broken entry
            }
        }
        claims = Map.copyOf(loaded);
        outside = data.getIntOr("outside", 0);
        inVillage = data.getIntOr("in_village", 0);
        limitOutside = data.getIntOr("limit_outside", 0);
        limitVillage = data.getIntOr("limit_village", 0);
        admin = data.getBooleanOr("admin", false);
    }

    public static Entry at(int chunkX, int chunkZ) {
        return claims.get(Claim.key(chunkX, chunkZ));
    }

    public static boolean isMine(Entry entry) {
        Minecraft minecraft = Minecraft.getInstance();
        return entry != null && minecraft.player != null && entry.owner().equals(minecraft.player.getUUID());
    }

    public static int outside() {
        return outside;
    }

    public static int inVillage() {
        return inVillage;
    }

    public static int limitOutside() {
        return limitOutside;
    }

    public static int limitVillage() {
        return limitVillage;
    }

    public static boolean admin() {
        return admin;
    }

    public static void send(String action, int chunkX, int chunkZ, String villageId) {
        ClientPacketDistributor.sendToServer(new ClaimActionPayload(action, chunkX, chunkZ, villageId));
    }
}
