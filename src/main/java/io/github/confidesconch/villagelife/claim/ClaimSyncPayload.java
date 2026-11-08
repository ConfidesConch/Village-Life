package io.github.confidesconch.villagelife.claim;

import io.github.confidesconch.villagelife.VillageLife;
import io.netty.buffer.ByteBuf;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Server → client (M11f): all claims of the player's dimension plus his own usage and limits. */
public record ClaimSyncPayload(CompoundTag data) implements CustomPacketPayload {
    public static final Type<ClaimSyncPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(VillageLife.MOD_ID, "claim_sync"));

    public static final StreamCodec<ByteBuf, ClaimSyncPayload> STREAM_CODEC =
            ByteBufCodecs.TRUSTED_COMPOUND_TAG.map(ClaimSyncPayload::new, ClaimSyncPayload::data);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
