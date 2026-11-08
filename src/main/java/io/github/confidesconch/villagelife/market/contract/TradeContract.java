package io.github.confidesconch.villagelife.market.contract;

import java.util.Optional;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.UUIDUtil;

/**
 * One trade contract between two villages (M15d). {@code giver} delivers the good, {@code receiver}
 * pays. While {@link Status#OFFER}, {@code awaiting} is the village that has to answer (accept,
 * decline or counter). {@code endsAt} is set after a cancellation; {@code nextDelivery} from M15d2 on.
 * M15d3: {@code debt} = payment items the receiver still owes; {@code deliveryMisses} /
 * {@code paymentMisses} = failed deliveries / payments in a row (3 → cancelled automatically).
 */
public record TradeContract(int id, UUID giver, UUID receiver, ContractTerms terms, Status status,
                            Optional<UUID> awaiting, long endsAt, long nextDelivery,
                            int debt, int deliveryMisses, int paymentMisses) {

    public enum Status {
        OFFER, ACTIVE, ENDING;

        static final Codec<Status> CODEC = Codec.STRING.xmap(
                name -> switch (name) {
                    case "active" -> ACTIVE;
                    case "ending" -> ENDING;
                    default -> OFFER;
                },
                status -> status.name().toLowerCase(java.util.Locale.ROOT));
    }

    static final Codec<ContractTerms> TERMS_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("good").forGetter(ContractTerms::good),
            Codec.INT.fieldOf("percent").forGetter(ContractTerms::percent),
            Codec.INT.fieldOf("max").forGetter(ContractTerms::max),
            Codec.INT.fieldOf("days").forGetter(ContractTerms::days),
            Codec.STRING.fieldOf("payment").forGetter(ContractTerms::payment),
            Codec.INT.fieldOf("payment_count").forGetter(ContractTerms::paymentCount)
    ).apply(instance, ContractTerms::new));

    public static final Codec<TradeContract> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("id").forGetter(TradeContract::id),
            UUIDUtil.CODEC.fieldOf("giver").forGetter(TradeContract::giver),
            UUIDUtil.CODEC.fieldOf("receiver").forGetter(TradeContract::receiver),
            TERMS_CODEC.fieldOf("terms").forGetter(TradeContract::terms),
            Status.CODEC.fieldOf("status").forGetter(TradeContract::status),
            UUIDUtil.CODEC.optionalFieldOf("awaiting").forGetter(TradeContract::awaiting),
            Codec.LONG.optionalFieldOf("ends_at", 0L).forGetter(TradeContract::endsAt),
            Codec.LONG.optionalFieldOf("next_delivery", 0L).forGetter(TradeContract::nextDelivery),
            // optional: contracts saved before M15d3
            Codec.INT.optionalFieldOf("debt", 0).forGetter(TradeContract::debt),
            Codec.INT.optionalFieldOf("delivery_misses", 0).forGetter(TradeContract::deliveryMisses),
            Codec.INT.optionalFieldOf("payment_misses", 0).forGetter(TradeContract::paymentMisses)
    ).apply(instance, TradeContract::new));

    /** A new offer. */
    static TradeContract offer(int id, UUID giver, UUID receiver, ContractTerms terms, UUID answerBy) {
        return new TradeContract(id, giver, receiver, terms, Status.OFFER, Optional.of(answerBy), 0, 0, 0, 0, 0);
    }

    public boolean involves(UUID village) {
        return giver.equals(village) || receiver.equals(village);
    }

    /** The other village of the contract, seen from {@code village}. */
    public UUID partnerOf(UUID village) {
        return giver.equals(village) ? receiver : giver;
    }

    public boolean isTurnOf(UUID village) {
        return status == Status.OFFER && awaiting.map(village::equals).orElse(false);
    }

    /** Active, or cancelled but still within its notice period: deliveries go on. */
    public boolean isRunning(long now) {
        return status == Status.ACTIVE || (status == Status.ENDING && now < endsAt);
    }

    TradeContract accepted(long firstDelivery) {
        return new TradeContract(id, giver, receiver, terms, Status.ACTIVE, Optional.empty(), 0, firstDelivery, 0, 0, 0);
    }

    TradeContract countered(UUID newGiver, UUID newReceiver, ContractTerms newTerms, UUID answerBy) {
        return offer(id, newGiver, newReceiver, newTerms, answerBy);
    }

    TradeContract withNextDelivery(long gameTime) {
        return new TradeContract(id, giver, receiver, terms, status, awaiting, endsAt, gameTime, debt, deliveryMisses, paymentMisses);
    }

    TradeContract ending(long at) {
        return new TradeContract(id, giver, receiver, terms, Status.ENDING, Optional.empty(), at, nextDelivery, debt,
                deliveryMisses, paymentMisses);
    }

    TradeContract withDeliveryMisses(int misses) {
        return new TradeContract(id, giver, receiver, terms, status, awaiting, endsAt, nextDelivery, debt, misses, paymentMisses);
    }

    TradeContract withPayment(int newDebt, int misses) {
        return new TradeContract(id, giver, receiver, terms, status, awaiting, endsAt, nextDelivery, newDebt, deliveryMisses, misses);
    }
}
