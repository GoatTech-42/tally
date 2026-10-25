package dev.doughbay.fabric.automation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OrderShapeTest {
    @Test void configuredCompetitivePriceChangesLotEligibilityWithoutChangingItsCeiling() {
        assertEquals(10300, OrderShape.topOfBook(10000, 3));
        assertEquals(OrderShape.Mode.STACKS, OrderShape.mode(OrderShape.topOfBook(10000, 1), 10200, 10200));
        assertEquals(OrderShape.Mode.NONE, OrderShape.mode(OrderShape.topOfBook(10000, 3), 10200, 10200));
        assertEquals(6, OrderShape.topOfBook(5, 3));
        assertEquals(0, OrderShape.topOfBook(Long.MAX_VALUE, 3));
        assertEquals(0, OrderShape.topOfBook(100, Double.NaN));
    }

    @Test void twelveStacksMeans768BlocksAndSevenMeans448() {
        assertEquals(768,OrderShape.stackOrderCount(12,64,45_885,40_000_000,12));
        assertEquals(448,OrderShape.stackOrderCount(7,64,45_885,40_000_000,12));
        assertEquals(0,OrderShape.stackOrderCount(12,64,45_885,350_000,12));
        assertEquals(128,OrderShape.stackOrderCount(12,64,45_885,6_000_000,12));
    }

    @Test void quantityRespectsListingRoomExactCountsAndRealStackSizes() {
        assertEquals(192,OrderShape.stackOrderCount(12,64,100,1_000_000,3));
        assertEquals(960,OrderShape.stackOrderCount(30,64,100,1_000_000,30));
        assertEquals(192,OrderShape.stackOrderCount(12,16,100,1_000_000,12));
        assertEquals(12,OrderShape.stackOrderCount(12,1,100,1_000_000,12));
        assertEquals(0,OrderShape.stackOrderCount(12,64,Long.MAX_VALUE,Long.MAX_VALUE,12));
        assertEquals(0,OrderShape.stackOrderCount(12,64,100,1_000_000,0));
    }

    @Test void sampledSingleDiamondBlockCannotBecomeASevenItemStackOrder() {
        // Actual incident: an X1 price bucket yielded "7 of up to 12 stacks", but ordered 7 blocks.
        assertEquals(64, OrderShape.resaleLot(1,64,true,false));
        assertEquals(0, OrderShape.resaleLot(1,64,false,false));
        assertEquals(0, OrderShape.resaleLot(16,64,false,false));
    }

    @Test void purchaseCapCannotSilentlyEnableSingles() {
        org.junit.jupiter.api.Assertions.assertFalse(OrderShape.mayUseSingles(64,false));
        org.junit.jupiter.api.Assertions.assertTrue(OrderShape.mayUseSingles(64,true));
        org.junit.jupiter.api.Assertions.assertTrue(OrderShape.mayUseSingles(1,false));
    }

    @Test void realStackSizesAndExplicitSinglesRemainDistinct() {
        assertEquals(16, OrderShape.resaleLot(1,16,true,false));
        assertEquals(1, OrderShape.resaleLot(1,1,true,false));
        assertEquals(1, OrderShape.resaleLot(1,64,false,true));
        assertEquals(64, OrderShape.resaleLot(1,64,true,true));
    }

    @Test
    void anOrderGoesOneStepAboveTheBestStandingOne() {
        assertEquals(13_130, OrderShape.topOfBook(13_000));
        assertEquals(6, OrderShape.topOfBook(5));
        assertEquals(0, OrderShape.topOfBook(0));
    }

    @Test
    void wholeStacksOnlyWhereAStackPays() {
        // Golden apples: orders at 21,700, a stack resells for enough to bid 23,300.
        assertEquals(OrderShape.Mode.STACKS, OrderShape.mode(21_700, 30_000, 23_300));
        // Emerald blocks: orders at 13,130, a stack only supports 11,500, singles 30,000.
        assertEquals(OrderShape.Mode.SINGLES, OrderShape.mode(13_130, 30_000, 11_500));
        assertEquals(OrderShape.Mode.SINGLES, OrderShape.mode(13_130, 30_000, 0));
        assertEquals(OrderShape.Mode.NONE, OrderShape.mode(31_000, 30_000, 11_500));
        assertEquals(OrderShape.Mode.NONE, OrderShape.mode(0, 30_000, 11_500));
    }

    @Test
    void anOrderLargerThanTheSingleSlotsIsHeldToWhatAStackFetches() {
        assertEquals(30_000, OrderShape.ceilingUnit(12, 12, 30_000, 11_500));
        assertEquals(11_500, OrderShape.ceilingUnit(13, 12, 30_000, 11_500));
        assertEquals(11_500, OrderShape.ceilingUnit(64, 12, 30_000, 11_500));
        // No stack price known: unknown, not the single price.
        assertEquals(0, OrderShape.ceilingUnit(64, 12, 30_000, 0));
        // Not an item sold as singles: the stack price is the only one.
        assertEquals(1_900, OrderShape.ceilingUnit(64, 12, 0, 1_900));
        assertEquals(1_900, OrderShape.ceilingUnit(12, 0, 30_000, 1_900));
    }

    @Test
    void anOrderIsCutToWholeStacksTheMoneyCovers() {
        assertEquals(3, OrderShape.fitStacks(5, 64, 20_000, 4_000_000));
        assertEquals(5, OrderShape.fitStacks(5, 64, 20_000, 40_000_000));
        // One stack is never cut further here; the caller refuses it if it is still too dear.
        assertEquals(1, OrderShape.fitStacks(5, 64, 20_000, 100_000));
        assertEquals(1, OrderShape.fitStacks(1, 64, 20_000, 0));
    }
}
