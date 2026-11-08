package io.github.confidesconch.villagelife.convoy;

import io.github.confidesconch.villagelife.VillageLife;
import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Client → server (M20c): village {@code villageId} sends {@code escorts} guards with its shipments of
 * contract {@code contractId}. Checked on the server (rights, contract, 0–3).
 */
public record ConvoyEscortPayload(String villageId, int contractId, int escorts) implements CustomPacketPayload {
    public static final Type<ConvoyEscortPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(VillageLife.MOD_ID, "convoy_escort"));

    public static final StreamCodec<ByteBuf, ConvoyEscortPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(64), ConvoyEscortPayload::villageId,
            ByteBufCodecs.VAR_INT, ConvoyEscortPayload::contractId,
            ByteBufCodecs.VAR_INT, ConvoyEscortPayload::escorts,
            ConvoyEscortPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
