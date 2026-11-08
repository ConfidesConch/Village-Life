package io.github.confidesconch.villagelife.book;

import io.github.confidesconch.villagelife.VillageLife;
import io.netty.buffer.ByteBuf;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server → client (M21b2): fresh data of some areas of the open village window – only those sections
 * (plus the village id and name); the client puts them into its snapshot and keeps the rest.
 */
public record VillageSectionDataPayload(CompoundTag data) implements CustomPacketPayload {
    public static final Type<VillageSectionDataPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(VillageLife.MOD_ID, "village_section_data"));

    public static final StreamCodec<ByteBuf, VillageSectionDataPayload> STREAM_CODEC =
            ByteBufCodecs.TRUSTED_COMPOUND_TAG.map(VillageSectionDataPayload::new, VillageSectionDataPayload::data);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
