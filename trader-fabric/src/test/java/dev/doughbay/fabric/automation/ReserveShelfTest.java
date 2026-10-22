package dev.doughbay.fabric.automation;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ReserveShelfTest {
    @TempDir Path temp;
    static final String ITEM = ReserveStockTest.ITEM;
    static final int BATCH = ReserveStockTest.BATCH;

    @Test void salesRefillOnlyVacantShelfSpaceAndRespectBothSlotLimits() {
        assertEquals(64,ReserveStock.collectUnits(11*64,BATCH,64,40));
        assertEquals(3*64,ReserveStock.collectUnits(5*64,BATCH,64,3));
        assertEquals(0,ReserveStock.collectUnits(12*64,BATCH,64,40));
        assertEquals(0,ReserveStock.collectUnits(13*64,BATCH,64,40));
        assertEquals(0,ReserveStock.collectUnits(0,BATCH,64,0));
        assertEquals(0,ReserveStock.collectUnits(-1,BATCH,64,40));
        assertEquals(11*64,ReserveStock.collectUnits(1,BATCH,64,40));
        assertEquals(16,ReserveStock.collectUnits(11*16,12*16,16,40));
    }

    @Test void partiallyCollectedIdenticalOrdersKeepTheRightOrdinalAndCost() {
        var first=ReserveStockTest.row(BATCH);
        var partial=ReserveStockTest.row(32);
        var fullStack=ReserveStockTest.row(64);
        var rows=List.of(first,partial,fullStack);
        var credits=Map.of(ReserveStock.key(first),BATCH);
        assertEquals(2,ReserveStock.collectOrdinal(ITEM,100,BATCH,rows,credits,64));
        var afterOne=Map.of(ReserveStock.key(first),BATCH+32);
        assertEquals(2,ReserveStock.collectOrdinal(ITEM,100,BATCH,rows,afterOne,64));
        var pair=List.of(first,first);
        assertEquals(1,ReserveStock.collectOrdinal(ITEM,100,BATCH,pair,Map.of(ReserveStock.key(first),BATCH+64),64));
        assertEquals(BATCH-64,ReserveStock.book(ITEM,pair,Map.of(ReserveStock.key(first),BATCH+64)).units());
        var capital=dev.doughbay.fabric.CapitalBreakdown.orders(1,pair,Map.of(ReserveStock.key(first),BATCH+64));
        assertEquals((BATCH-64)*100,capital.awaitingCollection());
    }

    @Test void twoPurchasesThenDrainBothShelfAndReserveBeforeAnotherPairSurvivesRestart() throws Exception {
        Path path=temp.resolve("cycles.txt");
        var cycles=new ReserveCycles(); cycles.load(path);
        var empty=new ReserveStock.Book(0,0,true);
        var one=new ReserveStock.Book(1,BATCH,true);
        assertTrue(cycles.mayPlace(ITEM,empty,0,BATCH));
        cycles.reservePurchase(ITEM);
        assertTrue(cycles.mayPlace(ITEM,one,0,BATCH));
        cycles.reservePurchase(ITEM);
        assertFalse(cycles.mayPlace(ITEM,one,64,BATCH));
        var restart=new ReserveCycles(); restart.load(path);
        assertFalse(restart.mayPlace(ITEM,empty,64,BATCH));
        assertFalse(restart.mayPlace(ITEM,one,0,BATCH));
        assertTrue(restart.mayPlace(ITEM,empty,0,BATCH));
        restart.reservePurchase(ITEM);
        assertTrue(restart.mayPlace(ITEM,empty,BATCH,BATCH)); // First order already collected; still may buy second.
        restart.reservePurchase(ITEM);
        assertFalse(restart.mayPlace(ITEM,empty,BATCH,BATCH));
    }

    @Test void upgradeAdoptsExistingExposureAndCorruptOrUnavailableStorageBlocksNewBids() throws Exception {
        var cycles=new ReserveCycles(); cycles.load(temp.resolve("adopt.txt"));
        assertFalse(cycles.mayPlace(ITEM,new ReserveStock.Book(1,BATCH,true),0,BATCH));
        assertFalse(cycles.mayPlace(ITEM,new ReserveStock.Book(0,0,false),0,BATCH));
        Path bad=temp.resolve("bad.txt"); Files.writeString(bad,"broken");
        var unreadable=new ReserveCycles();
        assertThrows(java.io.IOException.class,()->unreadable.load(bad));
        assertFalse(unreadable.mayPlace(ITEM,new ReserveStock.Book(0,0,true),0,BATCH));
        var absent=new ReserveCycles(); absent.load(null);
        assertThrows(java.io.IOException.class,()->absent.reservePurchase(ITEM));
        assertFalse(absent.mayPlace(ITEM,new ReserveStock.Book(0,0,true),0,BATCH));
    }
}
