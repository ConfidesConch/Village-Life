package io.github.confidesconch.villagelife.market.treasury;

import java.util.List;

import io.github.confidesconch.villagelife.market.MarketService;
import io.github.confidesconch.villagelife.market.treasury.TreasuryStore.Entry;
import io.github.confidesconch.villagelife.market.treasury.TreasuryStore.Ledger;
import io.github.confidesconch.villagelife.village.Village;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Items;

/**
 * The village treasury (M15c, owner's choice "a": owner and managers only): records every business
 * event of the market, the caravans and the contracts, and the emeralds in storage per day. The market
 * page of the book shows it on an extra double page.
 */
public final class Treasury {
    private Treasury() {}

    public static final String PLAYER_BUY = "player_buy";
    public static final String PLAYER_SELL = "player_sell";
    public static final String CARAVAN_SELL = "caravan_sell";
    public static final String CARAVAN_BUY = "caravan_buy";
    public static final String CONTRACT_OUT = "contract_out";
    public static final String CONTRACT_IN = "contract_in";
    public static final String PAYMENT_OUT = "payment_out";
    public static final String PAYMENT_IN = "payment_in";
    public static final String RETURNED = "returned";
    private static final long DAY_TICKS = 24000;

    /**
     * Records a business event: {@code count} × {@code item}, {@code emeralds} = change of the village's
     * emeralds (positive = earned). Also updates today's emerald count.
     */
    public static void log(ServerLevel level, Village village, String kind, String item, int count, int emeralds) {
        MinecraftServer server = level.getServer();
        TreasuryStore store = TreasuryStore.get(server);
        Ledger ledger = store.of(village.id());
        Entry entry = new Entry(server.overworld().getGameTime(), kind, item, count, emeralds);
        store.put(village.id(), new Ledger(TreasuryHistory.append(ledger.entries(), entry), ledger.days()));
        snapshot(level, village);
    }

    /** Notes today's emeralds in storage, if the storage is loaded. */
    public static void snapshot(ServerLevel level, Village village) {
        if (!MarketService.isStorageLoaded(level, village)) {
            return;
        }
        int emeralds = MarketService.count(level, village, stack -> stack.is(Items.EMERALD));
        TreasuryStore store = TreasuryStore.get(level.getServer());
        Ledger ledger = store.of(village.id());
        long day = level.getServer().overworld().getGameTime() / DAY_TICKS;
        store.put(village.id(), new Ledger(ledger.entries(), TreasuryHistory.record(ledger.days(), day, emeralds)));
    }

    /** The treasury part of the book's market section: emeralds per day and the last events. */
    public static CompoundTag section(ServerLevel level, Village village) {
        snapshot(level, village);
        MinecraftServer server = level.getServer();
        long now = server.overworld().getGameTime();
        Ledger ledger = TreasuryStore.get(server).of(village.id());
        CompoundTag out = new CompoundTag();
        out.putIntArray("days", TreasuryHistory.series(ledger.days(), now / DAY_TICKS));
        ListTag entries = new ListTag();
        List<Entry> list = ledger.entries();
        for (Entry entry : list) {
            CompoundTag e = new CompoundTag();
            e.putString("kind", entry.kind());
            e.putString("item", entry.item());
            e.putInt("count", entry.count());
            e.putInt("emeralds", entry.emeralds());
            e.putLong("ago", Math.max(0, now - entry.gameTime()));
            entries.add(e);
        }
        out.put("entries", entries);
        return out;
    }
}
