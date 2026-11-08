package io.github.confidesconch.villagelife.friend;

import io.github.confidesconch.villagelife.VillageLife;
import io.netty.buffer.ByteBuf;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Server → client (M11g): where the player's online friends are (sent about once per second). */
public record FriendPositionsPayload(CompoundTag data) implements CustomPacketPayload {
    public static final Type<FriendPositionsPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(VillageLife.MOD_ID, "friend_positions"));

    public static final StreamCodec<ByteBuf, FriendPositionsPayload> STREAM_CODEC =
            ByteBufCodecs.TRUSTED_COMPOUND_TAG.map(FriendPositionsPayload::new, FriendPositionsPayload::data);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
