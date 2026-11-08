package io.github.confidesconch.villagelife.market.contract;

import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.UUIDUtil;

/**
 * Goods on the road between two villages (M15d2): a contract delivery or its payment. It left the
 * sender's storage at {@code departedAt} and arrives at {@code arrivesAt}. {@code count} is what is still
 * to be unloaded (less than {@code original} if the receiver's storage was full, or after a raid). Since M20c
 * it travels as a convoy: {@code escorts} guards of the sender, a raid at {@code raidAt} (0 = none) that has
 * {@code raided} already happened. {@code waiting}: it is there, but the receiver's storage was full – it
 * waits at the gate and is tried again at {@code arrivesAt}.
 */
public record Shipment(int contractId, UUID from, UUID to, String item, int count, int original, boolean payment,
                       long departedAt, long arrivesAt, int escorts, long raidAt, boolean raided, boolean waiting) {

    /** A shipment without convoy data (escorts, raid) – as before M20c. */
    public Shipment(int contractId, UUID from, UUID to, String item, int count, int original, boolean payment,
                    long departedAt, long arrivesAt) {
        this(contractId, from, to, item, count, original, payment, departedAt, arrivesAt, 0, 0L, false, false);
    }

    public static final Codec<Shipment> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("contract").forGetter(Shipment::contractId),
            UUIDUtil.CODEC.fieldOf("from").forGetter(Shipment::from),
            UUIDUtil.CODEC.fieldOf("to").forGetter(Shipment::to),
            Codec.STRING.fieldOf("item").forGetter(Shipment::item),
            Codec.INT.fieldOf("count").forGetter(Shipment::count),
            Codec.INT.fieldOf("original").forGetter(Shipment::original),
            Codec.BOOL.fieldOf("payment").forGetter(Shipment::payment),
            Codec.LONG.fieldOf("departed_at").forGetter(Shipment::departedAt),
            Codec.LONG.fieldOf("arrives_at").forGetter(Shipment::arrivesAt),
            // M20c: convoy data, missing for shipments saved before
            Codec.INT.optionalFieldOf("escorts", 0).forGetter(Shipment::escorts),
            Codec.LONG.optionalFieldOf("raid_at", 0L).forGetter(Shipment::raidAt),
            Codec.BOOL.optionalFieldOf("raided", false).forGetter(Shipment::raided),
            Codec.BOOL.optionalFieldOf("waiting", false).forGetter(Shipment::waiting)
    ).apply(instance, Shipment::new));

    public Shipment withCount(int left) {
        return new Shipment(contractId, from, to, item, left, original, payment, departedAt, arrivesAt, escorts, raidAt, raided, waiting);
    }

    Shipment arrivingAt(long gameTime) {
        return new Shipment(contractId, from, to, item, count, original, payment, departedAt, gameTime, escorts, raidAt, raided, waiting);
    }

    /** The receiver's storage was full: it waits at the gate until {@code gameTime} (M20c: no wagon on the road any more). */
    Shipment waitingUntil(long gameTime) {
        return new Shipment(contractId, from, to, item, count, original, payment, departedAt, gameTime, escorts, raidAt, raided, true);
    }

    /** With its convoy (M20c): escorts, and when it will be raided (0 = not). */
    public Shipment withConvoy(int escortCount, long raidGameTime) {
        return new Shipment(contractId, from, to, item, count, original, payment, departedAt, arrivesAt, escortCount,
                raidGameTime, false, waiting);
    }

    /**
     * After the raid (M20c): {@code lost} items are gone (0 = fought off). The receiver gets – and pays for –
     * only what is left, so {@code original} shrinks too.
     */
    public Shipment afterRaid(int lost) {
        return new Shipment(contractId, from, to, item, Math.max(0, count - lost), Math.max(0, original - lost), payment,
                departedAt, arrivesAt, escorts, raidAt, true, waiting);
    }

    /** A raid is still ahead of it. */
    public boolean raidPending() {
        return raidAt > 0 && !raided;
    }

    /** Where it is on its way, 0 = just left, 1 = there. */
    public double progress(long gameTime) {
        long length = Math.max(1, arrivesAt - departedAt);
        return Math.max(0.0, Math.min(1.0, (gameTime - departedAt) / (double) length));
    }

    /** Same journey (the record may have changed since, e.g. after a raid). */
    public boolean sameTrip(Shipment other) {
        return other != null && contractId == other.contractId && from.equals(other.from) && departedAt == other.departedAt
                && payment == other.payment;
    }
}
