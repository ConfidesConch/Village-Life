package io.github.confidesconch.villagelife.claim;

import io.github.confidesconch.villagelife.VillageLife;
import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Client → server (M11f): {@code sync} (send me the claims of my dimension), {@code claim} or
 * {@code unclaim} chunk x/z, or {@code claim_village} (all free chunks of village {@code villageId}).
 * Checked on the server ({@link ClaimService}).
 */
public record ClaimActionPayload(String action, int chunkX, int chunkZ, String villageId) implements CustomPacketPayload {
    public static final Type<ClaimActionPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(VillageLife.MOD_ID, "claim_action"));

    public static final StreamCodec<ByteBuf, ClaimActionPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(16), ClaimActionPayload::action,
            ByteBufCodecs.VAR_INT, ClaimActionPayload::chunkX,
            ByteBufCodecs.VAR_INT, ClaimActionPayload::chunkZ,
            ByteBufCodecs.stringUtf8(64), ClaimActionPayload::villageId,
            ClaimActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
