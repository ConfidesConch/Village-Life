package io.github.confidesconch.villagelife.waypoint;

import io.github.confidesconch.villagelife.VillageLife;
import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Client → server: a change to the player's own waypoints (M11e). {@code action} is one of
 * {@code sync} (just send me my list), {@code add}, {@code edit}, {@code toggle}, {@code delete}.
 * Everything is checked on the server ({@link WaypointService}).
 */
public record WaypointActionPayload(String action, int id, String name, int color, int x, int y, int z, boolean shared)
        implements CustomPacketPayload {
    public static final Type<WaypointActionPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(VillageLife.MOD_ID, "waypoint_action"));

    public static final StreamCodec<ByteBuf, WaypointActionPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(16), WaypointActionPayload::action,
            ByteBufCodecs.VAR_INT, WaypointActionPayload::id,
            ByteBufCodecs.stringUtf8(64), WaypointActionPayload::name,
            ByteBufCodecs.VAR_INT, WaypointActionPayload::color,
            ByteBufCodecs.VAR_INT, WaypointActionPayload::x,
            ByteBufCodecs.VAR_INT, WaypointActionPayload::y,
            ByteBufCodecs.VAR_INT, WaypointActionPayload::z,
            ByteBufCodecs.BOOL, WaypointActionPayload::shared,
            WaypointActionPayload::new);

    /** "Send me my waypoints" (used once after joining). */
    public static WaypointActionPayload sync() {
        return new WaypointActionPayload("sync", 0, "", 0, 0, 0, 0, false);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
