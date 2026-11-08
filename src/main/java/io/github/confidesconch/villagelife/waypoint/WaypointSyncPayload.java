package io.github.confidesconch.villagelife.waypoint;

import io.github.confidesconch.villagelife.VillageLife;
import io.netty.buffer.ByteBuf;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Server → client: the player's complete waypoint list (M11e), as a {@code list} of compound tags. */
public record WaypointSyncPayload(CompoundTag data) implements CustomPacketPayload {
    public static final Type<WaypointSyncPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(VillageLife.MOD_ID, "waypoint_sync"));

    public static final StreamCodec<ByteBuf, WaypointSyncPayload> STREAM_CODEC =
            ByteBufCodecs.TRUSTED_COMPOUND_TAG.map(WaypointSyncPayload::new, WaypointSyncPayload::data);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
