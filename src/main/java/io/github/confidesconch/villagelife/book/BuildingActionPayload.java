package io.github.confidesconch.villagelife.book;

import io.github.confidesconch.villagelife.VillageLife;
import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Client → server (M11i): something on a building's page in the book. {@code action} is {@code view}
 * (show the page incl. container contents), {@code rescan}, or {@code type:house|storage|workplace}.
 * Checked on the server: the player must see the village, changes need owner or manager.
 */
public record BuildingActionPayload(String villageId, String buildingId, String action) implements CustomPacketPayload {
    public static final Type<BuildingActionPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(VillageLife.MOD_ID, "building_action"));

    public static final StreamCodec<ByteBuf, BuildingActionPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(64), BuildingActionPayload::villageId,
            ByteBufCodecs.stringUtf8(64), BuildingActionPayload::buildingId,
            ByteBufCodecs.stringUtf8(32), BuildingActionPayload::action,
            BuildingActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
