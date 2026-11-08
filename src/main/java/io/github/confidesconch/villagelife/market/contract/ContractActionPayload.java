package io.github.confidesconch.villagelife.market.contract;

import io.github.confidesconch.villagelife.VillageLife;
import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Client → server (M15d1): an action on the contracts page of village {@code villageId}:
 * {@code offer} (to {@code partner}), {@code accept}, {@code decline}, {@code counter} or {@code cancel}
 * (on contract {@code contractId}). {@code weGive}: the acting village delivers the good. Everything is
 * checked on the server (rights, turn, limits).
 */
public record ContractActionPayload(String villageId, String action, int contractId, String partner, boolean weGive,
                                    ContractTerms terms) implements CustomPacketPayload {
    public static final Type<ContractActionPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(VillageLife.MOD_ID, "contract_action"));

    private static final StreamCodec<ByteBuf, ContractTerms> TERMS_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(128), ContractTerms::good,
            ByteBufCodecs.VAR_INT, ContractTerms::percent,
            ByteBufCodecs.VAR_INT, ContractTerms::max,
            ByteBufCodecs.VAR_INT, ContractTerms::days,
            ByteBufCodecs.stringUtf8(128), ContractTerms::payment,
            ByteBufCodecs.VAR_INT, ContractTerms::paymentCount,
            ContractTerms::new);

    public static final StreamCodec<ByteBuf, ContractActionPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(64), ContractActionPayload::villageId,
            ByteBufCodecs.stringUtf8(16), ContractActionPayload::action,
            ByteBufCodecs.VAR_INT, ContractActionPayload::contractId,
            ByteBufCodecs.stringUtf8(64), ContractActionPayload::partner,
            ByteBufCodecs.BOOL, ContractActionPayload::weGive,
            TERMS_CODEC, ContractActionPayload::terms,
            ContractActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
