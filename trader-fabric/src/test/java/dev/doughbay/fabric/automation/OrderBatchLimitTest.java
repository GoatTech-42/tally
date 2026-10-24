package dev.doughbay.fabric.automation;

import dev.doughbay.fabric.AutomatedExecutionDriver.OwnOrderRow;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OrderBatchLimitTest {
    private static final String ITEM="minecraft:stone";
    private static OwnOrderRow row(int delivered) { return new OwnOrderRow(ITEM,ITEM,960,100,delivered,1000); }

    @Test void defaultsPreserveBothModesAndAnExplicitCountControlsBoth() {
        assertEquals(1,OrderBatchLimit.effective(0,false));
        assertEquals(2,OrderBatchLimit.effective(0,true));
        for(int n:new int[]{1,2,16,90}) {
            assertEquals(n,OrderBatchLimit.effective(n,false));
            assertEquals(n,OrderBatchLimit.effective(n,true));
        }
        assertEquals(0,OrderBatchLimit.effective(-1,false));
        assertEquals(0,OrderBatchLimit.effective(91,true));
    }

    @Test void twoSeparateOrdersIncludeFilledUncollectedStockAndIgnoreCollectedHistory() {
        var live=ReserveStock.book(ITEM,List.of(row(0)),Map.of());
        assertFalse(OrderBatchLimit.hasRoom(live,1));
        assertTrue(OrderBatchLimit.hasRoom(live,2));
        var full=ReserveStock.book(ITEM,List.of(row(960),row(0)),Map.of());
        assertFalse(OrderBatchLimit.hasRoom(full,2));
        var collected=ReserveStock.book(ITEM,List.of(row(960),row(0)),Map.of(ReserveStock.key(row(0)),960));
        assertTrue(OrderBatchLimit.hasRoom(collected,2));
        assertFalse(OrderBatchLimit.hasRoom(collected,1),"Lowering the limit retains the remaining order");
    }

    @Test void aSecondOrderDoesNotReceiveASecondCopyOfDepthAndCorruptQuantitiesFailClosed() {
        var one=new ReserveStock.Book(1,960,true);
        assertTrue(OrderBatchLimit.fitsDepth(one,0,960,1920));
        assertFalse(OrderBatchLimit.fitsDepth(one,64,960,1920));
        assertFalse(OrderBatchLimit.fitsDepth(one,0,960,1919));
        assertTrue(OrderBatchLimit.fitsDepth(one,0,960,-1));
        assertFalse(OrderBatchLimit.fitsDepth(one,Long.MAX_VALUE,960,1920));
        assertFalse(OrderBatchLimit.hasRoom(new ReserveStock.Book(0,0,false),2));
        assertFalse(OrderBatchLimit.fitsDepth(new ReserveStock.Book(1,Long.MAX_VALUE,true),0,960,1920));
    }

    @Test void collectionWaitsForSellingShelfAndAccountSlotsWhileStockRemainsOnOrders() {
        assertEquals(64,OrderBatchLimit.collectionUnits(15*64,16*64,64,90));
        assertEquals(128,OrderBatchLimit.collectionUnits(0,16*64,64,2));
        assertEquals(0,OrderBatchLimit.collectionUnits(16*64,16*64,64,90));
        assertEquals(0,OrderBatchLimit.collectionUnits(0,16*64,64,0));
        assertEquals(3,OrderBatchLimit.collectionUnits(2,5,1,90));
    }
}
