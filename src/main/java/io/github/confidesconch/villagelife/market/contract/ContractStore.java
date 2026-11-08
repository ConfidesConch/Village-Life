package io.github.confidesconch.villagelife.market.contract;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.github.confidesconch.villagelife.VillageLife;
import io.github.confidesconch.villagelife.core.util.LenientCodecs;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * All trade contracts of the server (M15d), saved once in the overworld as
 * {@code data/villagelife/contracts.dat} – contracts may connect villages of different dimensions.
 * Server thread only.
 *
 * <p>Data versions: 1 = contracts (M15d1), 2 = + shipments on the road (M15d2; missing = none),
 * 3 = + debt and missed deliveries/payments per contract (M15d3; missing = 0), 4 = + convoy data per shipment
 * (escorts, raid; M20c; missing = none).
 */
public final class ContractStore extends SavedData {
    public static final int DATA_VERSION = 4;

    private static final Codec<ContractStore> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("data_version", 1).forGetter(store -> DATA_VERSION),
            Codec.INT.optionalFieldOf("next_id", 1).forGetter(store -> store.nextId),
            LenientCodecs.list(TradeContract.CODEC, "contract").optionalFieldOf("contracts", List.of())
                    .forGetter(store -> List.copyOf(store.contracts)),
            LenientCodecs.list(Shipment.CODEC, "shipment").optionalFieldOf("shipments", List.of())
                    .forGetter(store -> List.copyOf(store.shipments))
    ).apply(instance, ContractStore::new));

    public static final SavedDataType<ContractStore> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(VillageLife.MOD_ID, "contracts"), ContractStore::new, CODEC);

    private final List<TradeContract> contracts = new ArrayList<>();
    private final List<Shipment> shipments = new ArrayList<>();
    private int nextId = 1;

    public ContractStore() {}

    private ContractStore(int loadedDataVersion, int nextId, List<TradeContract> loaded, List<Shipment> onTheRoad) {
        this.nextId = nextId;
        contracts.addAll(loaded);
        shipments.addAll(onTheRoad);
        if (loadedDataVersion < DATA_VERSION) {
            setDirty();
        }
    }

    public static ContractStore get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public List<TradeContract> of(UUID village) {
        return contracts.stream().filter(c -> c.involves(village)).toList();
    }

    public List<TradeContract> all() {
        return List.copyOf(contracts);
    }

    public Optional<TradeContract> byId(int id) {
        return contracts.stream().filter(c -> c.id() == id).findFirst();
    }

    public int newId() {
        setDirty();
        return nextId++;
    }

    /** Adds the contract or replaces the one with the same id. */
    public void put(TradeContract contract) {
        contracts.removeIf(c -> c.id() == contract.id());
        contracts.add(contract);
        setDirty();
    }

    public void remove(int id) {
        if (contracts.removeIf(c -> c.id() == id)) {
            setDirty();
        }
    }

    public void removeIf(Predicate<TradeContract> test) {
        if (contracts.removeIf(test)) {
            setDirty();
        }
    }

    // ---- shipments (M15d2) ---------------------------------------------------------------------

    public List<Shipment> shipments() {
        return List.copyOf(shipments);
    }

    public void addShipment(Shipment shipment) {
        shipments.add(shipment);
        setDirty();
    }

    /** Replaces {@code old} by {@code replacement}, or removes it if the replacement is null. */
    public void replaceShipment(Shipment old, Shipment replacement) {
        int index = shipments.indexOf(old);
        if (index < 0) {
            return;
        }
        if (replacement == null) {
            shipments.remove(index);
        } else {
            shipments.set(index, replacement);
        }
        setDirty();
    }

    public void removeShipmentsIf(Predicate<Shipment> test) {
        if (shipments.removeIf(test)) {
            setDirty();
        }
    }
}
