package dev.doughbay.fabric.automation;

import dev.doughbay.fabric.AutomatedExecutionDriver.OwnOrderRow;
import dev.doughbay.fabric.CapitalBreakdown;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ReserveStockTest {
    static final String ITEM="minecraft:stone";
    static final int BATCH=12*64;
    static OwnOrderRow row(int delivered) { return new OwnOrderRow(ITEM,ITEM,BATCH,100,delivered,1000); }

    @Test void initialTwoOrdersThenOneWaitingBehindSellingBatch() {
        var empty=ReserveStock.book(ITEM,List.of(),Map.of());
        assertTrue(ReserveStock.mayPlace(empty,0,BATCH));
        var one=ReserveStock.book(ITEM,List.of(row(0)),Map.of());
        assertTrue(ReserveStock.mayPlace(one,0,BATCH));
        var two=ReserveStock.book(ITEM,List.of(row(0),row(0)),Map.of());
        assertFalse(ReserveStock.mayPlace(two,0,BATCH));
        assertFalse(ReserveStock.mayPlace(one,BATCH,BATCH));
        assertTrue(ReserveStock.mayPlace(empty,BATCH,BATCH));
        // One sold stack leaves exactly one stack of shelf space.
        assertEquals(64,ReserveStock.collectUnits(11*64,BATCH,64,90));
        assertEquals(0,ReserveStock.collectUnits(BATCH,BATCH,64,90));
        assertEquals(BATCH,ReserveStock.collectUnits(0,BATCH,64,90));
    }

    @Test void identicalOldCompletedOrdersDoNotCreateAnotherReserveOrCollectTheWrongRow() {
        var full=row(BATCH);
        var rows=List.of(full,full,row(0));
        var collected=Map.of(ReserveStock.key(full),BATCH);
        var book=ReserveStock.book(ITEM,rows,collected);
        assertEquals(2,book.batches());
        assertEquals(2L*BATCH,book.units());
        assertEquals(1,ReserveStock.collectOrdinal(ITEM,100,BATCH,rows,collected));
        assertFalse(ReserveStock.mayPlace(book,0,BATCH));
        // Once the second lot is collected, only the next unfilled reserve counts.
        var next=Map.of(ReserveStock.key(full),2*BATCH);
        assertEquals(1,ReserveStock.book(ITEM,rows,next).batches());
        assertEquals(-1,ReserveStock.collectOrdinal(ITEM,100,BATCH,rows,next));
    }

    @Test void deliveredReserveStillConsumesMoneyAndNeverLosesCostAtFillTime() {
        var open=CapitalBreakdown.orders(1,List.of(row(0)),Map.of());
        var filled=CapitalBreakdown.orders(1,List.of(row(BATCH)),Map.of());
        assertEquals(76800,open.reserved()+open.awaitingCollection());
        assertEquals(76800,filled.reserved()+filled.awaitingCollection());
        assertEquals(0,filled.reserved());
        var collected=Map.of(ReserveStock.key(row(BATCH)),BATCH);
        var taken=CapitalBreakdown.orders(2,List.of(row(BATCH)),collected);
        assertEquals(0,taken.reserved()+taken.awaitingCollection());
    }

    @Test void fullTwelveStackBatchesMustFitExistingMoneyDepthAndExactCounts() {
        assertEquals(BATCH,ReserveStock.batchUnits(12,64,100,76800,1536,768));
        assertEquals(0,ReserveStock.batchUnits(12,64,100,76799,1536,768));
        assertEquals(0,ReserveStock.batchUnits(12,64,100,76800,1535,768));
        assertEquals(0,ReserveStock.batchUnits(16,64,100,1000000,-1,0));
        assertEquals(0,ReserveStock.batchUnits(12,1,100,76800,-1,0));
        assertEquals(192,ReserveStock.batchUnits(12,16,100,76800,-1,0));
    }

    @Test void restartRebuildsCoverageFromSavedCollectionCreditsAndRemainingStock() {
        var rows=List.of(row(BATCH),row(BATCH));
        var saved=Map.of(ReserveStock.key(row(BATCH)),BATCH);
        assertEquals(1,ReserveStock.book(ITEM,rows,saved).batches());
        assertFalse(ReserveStock.mayPlace(ReserveStock.book(ITEM,rows,saved),BATCH,BATCH));
        assertEquals(11*64,ReserveStock.collectUnits(64,BATCH,64,90));
        assertEquals(BATCH,ReserveStock.collectUnits(0,BATCH,64,90));
    }

    @Test void unknownCounterOrInvalidRowsFailClosedAndOtherMarketsStayIndependent() {
        assertFalse(ReserveStock.book(ITEM,List.of(row(1)),Map.of(ReserveStock.key(row(1)),2)).valid());
        assertFalse(ReserveStock.book(ITEM,List.of(row(1)),Map.of(ReserveStock.key(row(1)),-1)).valid());
        assertFalse(ReserveStock.book(ITEM,List.of(new OwnOrderRow(ITEM,ITEM,10,100,11,0)),Map.of()).valid());
        assertTrue(ReserveStock.mayPlace(ReserveStock.book("minecraft:dirt",List.of(row(0),row(0)),Map.of()),0,BATCH));
    }

    @Test void cancelledOrderWithGoodsCountsAsReserveButEmptyCancelledRowDoesNot() {
        var empty=new OwnOrderRow(ITEM,ITEM,BATCH,100,0,0,true);
        var goods=new OwnOrderRow(ITEM,ITEM,BATCH,100,64,0,true);
        assertEquals(0,ReserveStock.book(ITEM,List.of(empty),Map.of()).batches());
        assertEquals(1,ReserveStock.book(ITEM,List.of(goods),Map.of()).batches());
        assertEquals(64,ReserveStock.book(ITEM,List.of(goods),Map.of()).units());
    }
    @Test void productionShelfIncludesOrdinaryRecoveredStockAndRepricingWithoutDoubleCounting() throws Exception {
        var c=new AutomationSessionController(new dev.doughbay.fabric.AutomatedExecutionDriver());
        var listed=new dev.doughbay.core.model.Position(1,"REAL",ITEM,dev.doughbay.core.model.StackBucket.of(64),64,
                6400,7000,1,2,0,0,Double.NaN,dev.doughbay.core.model.PositionStatus.LISTED);
        var unlisted=listed.withStatus(dev.doughbay.core.model.PositionStatus.PURCHASED);
        var extra=new dev.doughbay.core.model.Position(2,"REAL",ITEM,dev.doughbay.core.model.StackBucket.of(64),64,
                6400,7000,1,0,0,0,Double.NaN,dev.doughbay.core.model.PositionStatus.PURCHASED);
        var open=AutomationSessionController.class.getDeclaredField("openListings");open.setAccessible(true);
        ((java.util.List<dev.doughbay.core.model.Position>)open.get(c)).add(listed);
        var queue=AutomationSessionController.class.getDeclaredField("recoveredQueue");queue.setAccessible(true);
        ((java.util.Collection<dev.doughbay.core.model.Position>)queue.get(c)).add(extra);
        var repricing=AutomationSessionController.class.getDeclaredField("repricing");repricing.setAccessible(true);
        repricing.set(c,unlisted);
        var held=AutomationSessionController.class.getDeclaredMethod("reserveHeldUnits",String.class);held.setAccessible(true);
        assertEquals(128L,held.invoke(c,ITEM));
        assertEquals(10*64,ReserveStock.collectUnits((long)held.invoke(c,ITEM),BATCH,64,90));
        assertEquals(0L,held.invoke(c,"minecraft:dirt"));
    }

    @Test void productionExposureIncludesWaitingReserveOnlyWhenModeIsEnabled() throws Exception {
        double before=dev.doughbay.fabric.Tuning.get("orders.reserve_stock");
        try {
            var c=new AutomationSessionController(new dev.doughbay.fabric.AutomatedExecutionDriver());
            var orders=AutomationSessionController.class.getDeclaredField("ownOrders");orders.setAccessible(true);
            orders.set(c,List.of(row(BATCH)));
            var read=AutomationSessionController.class.getDeclaredField("ownOrdersReadAt");read.setAccessible(true);read.set(c,1L);
            var funds=AutomationSessionController.class.getDeclaredMethod("bidsOut");funds.setAccessible(true);
            dev.doughbay.fabric.Tuning.set("orders.reserve_stock",0);
            assertEquals(0L,funds.invoke(c));
            dev.doughbay.fabric.Tuning.set("orders.reserve_stock",1);
            assertEquals(76800L,funds.invoke(c));
        } finally { dev.doughbay.fabric.Tuning.set("orders.reserve_stock",before); }
    }
    @Test void enablingReserveDoesNotStrandAnOlderDurableRetailLot() throws Exception {
        double reserve=dev.doughbay.fabric.Tuning.get("orders.reserve_stock");
        double singles=dev.doughbay.fabric.Tuning.get("list.singles");
        try {
            dev.doughbay.fabric.Tuning.set("orders.reserve_stock",1);
            dev.doughbay.fabric.Tuning.set("list.singles",1);
            var c=new AutomationSessionController(new dev.doughbay.fabric.AutomatedExecutionDriver());
            var p=new dev.doughbay.core.model.Position(1,"REAL",ITEM,dev.doughbay.core.model.StackBucket.of(64),64,
                    6400,7000,1,0,0,0,Double.NaN,dev.doughbay.core.model.PositionStatus.PURCHASED);
            var lots=AutomationSessionController.class.getDeclaredField("retailLots");lots.setAccessible(true);
            ((java.util.Map<Long,dev.doughbay.storage.RetailLot>)lots.get(c)).put(1L,new dev.doughbay.storage.RetailLot(1,1,120));
            var legacy=AutomationSessionController.class.getDeclaredMethod("existingSinglesEnabled",dev.doughbay.core.model.Position.class);legacy.setAccessible(true);
            var fresh=AutomationSessionController.class.getDeclaredMethod("singlesListingEnabled");fresh.setAccessible(true);
            assertEquals(true,legacy.invoke(c,p));
            assertEquals(false,fresh.invoke(c));
            dev.doughbay.fabric.Tuning.set("list.singles",0);
            assertEquals(false,legacy.invoke(c,p));
        } finally {
            dev.doughbay.fabric.Tuning.set("orders.reserve_stock",reserve);
            dev.doughbay.fabric.Tuning.set("list.singles",singles);
        }
    }
}
