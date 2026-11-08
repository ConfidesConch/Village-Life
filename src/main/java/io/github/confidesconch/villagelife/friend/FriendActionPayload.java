package io.github.confidesconch.villagelife.friend;

import io.github.confidesconch.villagelife.VillageLife;
import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Client → server (M11g): {@code sync}, {@code invite}, {@code accept}, {@code decline} or {@code remove}
 * for player {@code target} (UUID as text). Checked on the server ({@link FriendService}).
 */
public record FriendActionPayload(String action, String target) implements CustomPacketPayload {
    public static final Type<FriendActionPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(VillageLife.MOD_ID, "friend_action"));

    public static final StreamCodec<ByteBuf, FriendActionPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(16), FriendActionPayload::action,
            ByteBufCodecs.stringUtf8(64), FriendActionPayload::target,
            FriendActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
