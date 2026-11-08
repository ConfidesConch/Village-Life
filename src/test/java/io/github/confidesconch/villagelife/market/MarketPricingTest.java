package io.github.confidesconch.villagelife.market;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class MarketPricingTest {

    @Test
    void scarceGoodsCostMore() {
        assertEquals(3, MarketPricing.price(2, 10, 64)); // 2 × 1.5
        assertEquals(2, MarketPricing.price(2, 64, 64));
        assertEquals(1, MarketPricing.price(2, 200, 64)); // 2 × 0.7 = 1.4 → 1
        assertEquals(1, MarketPricing.price(1, 500, 64)); // never below 1
    }

    @Test
    void sellsOnlyTheSurplus() {
        assertEquals(0, MarketPricing.bundlesForSale(60, 64, 6));
        assertEquals(2, MarketPricing.bundlesForSale(77, 64, 6));
    }

    @Test
    void buysUpToTheTarget() {
        assertEquals(0, MarketPricing.bundlesWanted(64, 64, 6));
        assertEquals(1, MarketPricing.bundlesWanted(62, 64, 6));
        assertEquals(11, MarketPricing.bundlesWanted(0, 64, 6));
    }
}
