package io.github.confidesconch.villagelife.book;

import io.github.confidesconch.villagelife.VillageLife;
import io.netty.buffer.ByteBuf;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server → client: everything the village book shows, as one snapshot. Each chapter has its own
 * section in {@code data} (see {@link BookSections}), so new chapters never change this payload.
 */
public record VillageBookPayload(CompoundTag data) implements CustomPacketPayload {
    public static final Type<VillageBookPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(VillageLife.MOD_ID, "village_book"));

    public static final StreamCodec<ByteBuf, VillageBookPayload> STREAM_CODEC =
            ByteBufCodecs.TRUSTED_COMPOUND_TAG.map(VillageBookPayload::new, VillageBookPayload::data);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
