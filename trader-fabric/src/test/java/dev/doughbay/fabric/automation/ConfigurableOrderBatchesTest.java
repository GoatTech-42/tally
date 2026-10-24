package dev.doughbay.fabric.automation;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ConfigurableOrderBatchesTest {
    @TempDir Path temp;
    static final String ITEM="minecraft:stone";
    static final int BATCH=12*64;
    static ReserveStock.Book book(int batches) { return new ReserveStock.Book(batches,(long)batches*BATCH,true); }

    @Test void configurableLimitCountsEachOutstandingBatchAndPhysicalShelfStock() {
        assertTrue(ReserveStock.mayPlace(book(1),0,BATCH,2));
        assertFalse(ReserveStock.mayPlace(book(2),0,BATCH,2));
        assertTrue(ReserveStock.mayPlace(book(2),0,BATCH,3));
        assertFalse(ReserveStock.mayPlace(book(2),BATCH,BATCH,3));
        assertFalse(ReserveStock.mayPlace(book(2),0,BATCH,1));
        assertTrue(ReserveStock.mayPlace(book(0),0,BATCH,1));
        assertFalse(ReserveStock.mayPlace(book(0),Long.MAX_VALUE,BATCH,90));
        assertFalse(ReserveStock.mayPlace(new ReserveStock.Book(0,Long.MAX_VALUE,true),0,BATCH,90));
    }

    @Test void thirdPurchaseAndLoweredLimitSurviveRestartWithoutDeletingExposure() throws Exception {
        Path path=temp.resolve("cycles.txt");
        var cycles=new ReserveCycles();cycles.load(path);
        for(int i=0;i<3;i++) {
            assertTrue(cycles.mayPlace(ITEM,book(i),0,BATCH,3));
            cycles.reservePurchase(ITEM,3);
        }
        assertFalse(cycles.mayPlace(ITEM,book(3),0,BATCH,3));
        String saved=Files.readString(path);
        var restarted=new ReserveCycles();restarted.load(path);
        assertFalse(restarted.mayPlace(ITEM,book(1),BATCH,BATCH,1));
        assertEquals(saved,Files.readString(path));
        assertThrows(java.io.IOException.class,()->restarted.reservePurchase(ITEM,1));
        assertTrue(restarted.mayPlace(ITEM,book(0),0,BATCH,1));
        restarted.reservePurchase(ITEM,1);
        assertFalse(restarted.mayPlace(ITEM,book(1),0,BATCH,1));
    }

    @Test void firstActivationWithExistingStockAdoptsAnAlreadyFundedCycle() throws Exception {
        Path path=temp.resolve("adopt.txt");var cycles=new ReserveCycles();cycles.load(path);
        assertFalse(cycles.mayPlace(ITEM,book(1),0,BATCH,4));
        assertEquals(ITEM+"=4",Files.readString(path));
        var restart=new ReserveCycles();restart.load(path);
        assertFalse(restart.mayPlace(ITEM,book(1),0,BATCH,4));
        assertTrue(restart.mayPlace(ITEM,book(0),0,BATCH,4));
    }

    @Test void oldTwoBatchRecordRemainsReadableAndInvalidLimitsCannotReserve() throws Exception {
        Path path=temp.resolve("old.txt");Files.writeString(path,ITEM+"=2");
        var cycles=new ReserveCycles();cycles.load(path);
        assertFalse(cycles.mayPlace(ITEM,book(1),0,BATCH,2));
        assertTrue(cycles.mayPlace(ITEM,book(2),0,BATCH,3));
        for(int invalid:new int[]{0,-1,91,Integer.MAX_VALUE}) {
            assertFalse(cycles.mayPlace(ITEM,book(2),0,BATCH,invalid));
            assertThrows(java.io.IOException.class,()->cycles.reservePurchase(ITEM,invalid));
        }
        assertEquals(ITEM+"=2",Files.readString(path));
    }
}
