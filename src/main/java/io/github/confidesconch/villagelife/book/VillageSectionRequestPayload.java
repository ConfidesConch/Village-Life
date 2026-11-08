package io.github.confidesconch.villagelife.book;

import java.util.List;

import io.github.confidesconch.villagelife.VillageLife;
import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Client → server (M21b2): the village window asks for the data of the areas it shows right now
 * ({@code sections}, at most 16 keys of {@link BookSections}) of village {@code villageId}. Checked on the
 * server (may the player see the village, are the keys known); the answer is a {@link VillageSectionDataPayload}.
 */
public record VillageSectionRequestPayload(String villageId, List<String> sections) implements CustomPacketPayload {
    public static final int MAX_SECTIONS = 16;
    public static final Type<VillageSectionRequestPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(VillageLife.MOD_ID, "village_section_request"));

    public static final StreamCodec<ByteBuf, VillageSectionRequestPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(64), VillageSectionRequestPayload::villageId,
            ByteBufCodecs.stringUtf8(32).apply(ByteBufCodecs.list(MAX_SECTIONS)), VillageSectionRequestPayload::sections,
            VillageSectionRequestPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
