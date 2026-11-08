package io.github.confidesconch.villagelife.book;

import io.github.confidesconch.villagelife.VillageLife;
import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Client → server: "open the village book" (book key), optionally for village {@code villageId}
 * (clicked on the character page, M11h). The server decides what the player may see. {@code window}
 * (M21b1): open the new village window instead of the book.
 */
public record OpenVillageBookPayload(String villageId, boolean window) implements CustomPacketPayload {
    public static final OpenVillageBookPayload INSTANCE = new OpenVillageBookPayload("", false);
    /** The book key since M21b1: the new village window. */
    public static final OpenVillageBookPayload WINDOW = new OpenVillageBookPayload("", true);
    public static final Type<OpenVillageBookPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(VillageLife.MOD_ID, "open_village_book"));
    public static final StreamCodec<ByteBuf, OpenVillageBookPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(64), OpenVillageBookPayload::villageId,
            ByteBufCodecs.BOOL, OpenVillageBookPayload::window,
            OpenVillageBookPayload::new);

    /** The book for village {@code villageId} (character page). */
    public OpenVillageBookPayload(String villageId) {
        this(villageId, false);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
