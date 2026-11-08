package io.github.confidesconch.villagelife.book;

import io.github.confidesconch.villagelife.VillageLife;
import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Client → server: the owner adds, edits or deletes a zone on the village map (M11e).
 * {@code action} is {@code add} (corners x1/z1 and x2/z2), {@code edit} or {@code delete} (zone {@code id}).
 * Checked on the server: owner, limit, zone inside the village.
 */
public record EditVillageZonePayload(String villageId, String action, int id, String name, int color,
                                     int x1, int z1, int x2, int z2) implements CustomPacketPayload {
    public static final Type<EditVillageZonePayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(VillageLife.MOD_ID, "edit_village_zone"));

    public static final StreamCodec<ByteBuf, EditVillageZonePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(64), EditVillageZonePayload::villageId,
            ByteBufCodecs.stringUtf8(16), EditVillageZonePayload::action,
            ByteBufCodecs.VAR_INT, EditVillageZonePayload::id,
            ByteBufCodecs.stringUtf8(64), EditVillageZonePayload::name,
            ByteBufCodecs.VAR_INT, EditVillageZonePayload::color,
            ByteBufCodecs.VAR_INT, EditVillageZonePayload::x1,
            ByteBufCodecs.VAR_INT, EditVillageZonePayload::z1,
            ByteBufCodecs.VAR_INT, EditVillageZonePayload::x2,
            ByteBufCodecs.VAR_INT, EditVillageZonePayload::z2,
            EditVillageZonePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
