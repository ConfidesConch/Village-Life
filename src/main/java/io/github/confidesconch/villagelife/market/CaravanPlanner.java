package io.github.confidesconch.villagelife.market;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure rules of the trading caravan (M15b, owner's choice "1a 2a 3a"): when it comes and what it trades.
 * The caravan first buys the village's surplus (up to {@link #MAX_BUNDLES} bundles), then sells what the
 * village lacks (up to {@link #MAX_BUNDLES} bundles) as long as the village can pay. One bundle per good
 * and round, so a visit brings variety instead of five times the same.
 */
public final class CaravanPlanner {
    private CaravanPlanner() {}

    public static final int TICKS_PER_DAY = 24000;
    /** Bundles the caravan buys at most, and bundles it sells at most, per visit. */
    public static final int MAX_BUNDLES = 5;
    /** It arrives in the morning (day time 1000–6000), so it can stay half a day before dusk. */
    public static final int ARRIVAL_FROM = 1000;
    public static final int ARRIVAL_UNTIL = 6000;
    /** How long it stays (half a day). */
    public static final int STAY_TICKS = 12000;
    /** It trades on arrival at the market, at the latest this long after it appeared. */
    public static final int TRADE_AT_LATEST_TICKS = 3000;

    /** One market good from the village's point of view (at the moment the caravan arrives). */
    public record Good(int forSale, int wanted, int price) {}

    /** One bundle of good {@code index}; {@code villageSells}: the village sells it to the caravan. */
    public record Deal(int index, boolean villageSells) {}

    /** When the next visit is due after one that happened (or the village was first seen) at {@code gameTime}. */
    public static long nextVisit(long gameTime, int days) {
        return gameTime + (long) Math.max(1, days) * TICKS_PER_DAY;
    }

    /** Whether a caravan should set out now: the visit is due and it is morning. */
    public static boolean mayArrive(long gameTime, long dayTime, long nextVisit) {
        long time = Math.floorMod(dayTime, (long) TICKS_PER_DAY);
        return gameTime >= nextVisit && time >= ARRIVAL_FROM && time <= ARRIVAL_UNTIL;
    }

    /** The deals of one visit, in the order they are carried out. */
    public static List<Deal> plan(List<Good> goods, int villageEmeralds) {
        List<Deal> deals = new ArrayList<>();
        int[] left = new int[goods.size()];
        for (int i = 0; i < goods.size(); i++) {
            left[i] = goods.get(i).forSale();
        }
        int emeralds = villageEmeralds;
        int bought = 0;
        boolean any = true;
        while (bought < MAX_BUNDLES && any) {
            any = false;
            for (int i = 0; i < goods.size() && bought < MAX_BUNDLES; i++) {
                if (left[i] > 0) {
                    left[i]--;
                    bought++;
                    any = true;
                    emeralds += goods.get(i).price();
                    deals.add(new Deal(i, true));
                }
            }
        }
        for (int i = 0; i < goods.size(); i++) {
            left[i] = goods.get(i).wanted();
        }
        int sold = 0;
        any = true;
        while (sold < MAX_BUNDLES && any) {
            any = false;
            for (int i = 0; i < goods.size() && sold < MAX_BUNDLES; i++) {
                int price = goods.get(i).price();
                if (left[i] > 0 && emeralds >= price) {
                    left[i]--;
                    sold++;
                    any = true;
                    emeralds -= price;
                    deals.add(new Deal(i, false));
                }
            }
        }
        return deals;
    }
}
