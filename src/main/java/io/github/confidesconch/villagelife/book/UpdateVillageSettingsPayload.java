package io.github.confidesconch.villagelife.book;

import io.github.confidesconch.villagelife.VillageLife;
import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Client → server: the owner changed one village setting in the book (M11c). Everything in it comes
 * from the client and is checked on the server (owner, key, value range, name length).
 */
public record UpdateVillageSettingsPayload(String villageId, String key, String value) implements CustomPacketPayload {
    public static final Type<UpdateVillageSettingsPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(VillageLife.MOD_ID, "update_village_settings"));

    public static final StreamCodec<ByteBuf, UpdateVillageSettingsPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(64), UpdateVillageSettingsPayload::villageId,
            ByteBufCodecs.stringUtf8(32), UpdateVillageSettingsPayload::key,
            ByteBufCodecs.stringUtf8(128), UpdateVillageSettingsPayload::value,
            UpdateVillageSettingsPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
