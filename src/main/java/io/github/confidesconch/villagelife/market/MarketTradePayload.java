package io.github.confidesconch.villagelife.market;

import io.github.confidesconch.villagelife.VillageLife;
import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Client → server (M15a): one trade on the market page. {@code villageSells} = the player buys a
 * bundle of {@code item}, otherwise the player sells one. Checked on the server (distance, merchant,
 * stock, emeralds, room).
 */
public record MarketTradePayload(String villageId, String item, boolean villageSells) implements CustomPacketPayload {
    public static final Type<MarketTradePayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(VillageLife.MOD_ID, "market_trade"));

    public static final StreamCodec<ByteBuf, MarketTradePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(64), MarketTradePayload::villageId,
            ByteBufCodecs.stringUtf8(128), MarketTradePayload::item,
            ByteBufCodecs.BOOL, MarketTradePayload::villageSells,
            MarketTradePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
