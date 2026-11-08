package io.github.confidesconch.villagelife.friend;

import io.github.confidesconch.villagelife.VillageLife;
import io.netty.buffer.ByteBuf;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Server → client (M11g): the player's friends (with online state), invitations both ways and the players online now (to invite). */
public record FriendSyncPayload(CompoundTag data) implements CustomPacketPayload {
    public static final Type<FriendSyncPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(VillageLife.MOD_ID, "friend_sync"));

    public static final StreamCodec<ByteBuf, FriendSyncPayload> STREAM_CODEC =
            ByteBufCodecs.TRUSTED_COMPOUND_TAG.map(FriendSyncPayload::new, FriendSyncPayload::data);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
