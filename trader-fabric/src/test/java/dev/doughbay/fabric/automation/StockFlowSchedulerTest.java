package dev.doughbay.fabric.automation;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StockFlowSchedulerTest {
    @Test void settlementAndPageCleanupDoNotConsumeReplenishmentTurns() {
        var scheduler = new StockFlowScheduler();
        for (int tick = 0; tick < 200; tick++) assertTrue(scheduler.deskFirst(true,20,90,tick));
        for (int action = 0; action < 4; action++) {
            assertTrue(scheduler.deskFirst(true,20,90,1000+action));
            scheduler.deskActionStarted();
        }
        assertFalse(scheduler.deskFirst(true,20,90,2000));
    }
    @Test void halfFullBookKeepsTwoActionRotationIncludingPromisedSlots() {
        var scheduler = new StockFlowScheduler();
        assertTrue(scheduler.deskFirst(true,45,90,0));
        scheduler.deskActionStarted(); assertTrue(scheduler.deskFirst(true,45,90,1));
        scheduler.deskActionStarted(); assertFalse(scheduler.deskFirst(true,45,90,2));
        assertTrue(scheduler.deskFirst(true,44,90,3));
    }
    @Test void dueRepriceGetsATurnAfterOneMinuteEvenIfDeskHasOnlyBeenWaiting() {
        var scheduler = new StockFlowScheduler();
        assertTrue(scheduler.deskFirst(true,0,90,1000));
        assertTrue(scheduler.deskFirst(true,0,90,60999));
        assertFalse(scheduler.deskFirst(true,0,90,61000));
        scheduler.repriceStarted();
        assertTrue(scheduler.deskFirst(true,0,90,61001));
    }
    @Test void clearedRepriceDoesNotLeaveOldDeadlineOrTurnDebt() {
        var scheduler = new StockFlowScheduler();
        scheduler.deskFirst(true,0,90,0);
        for(int i=0;i<4;i++)scheduler.deskActionStarted();
        assertFalse(scheduler.deskFirst(true,0,90,10));
        assertTrue(scheduler.deskFirst(false,0,90,100000));
        assertTrue(scheduler.deskFirst(true,0,90,100001));
    }
    @Test void fullOrUnknownCapacityDoesNotIncreaseReplenishmentPriority() {
        for(int capacity:new int[]{0,90}) {
            var scheduler = new StockFlowScheduler();
            scheduler.deskFirst(true,90,capacity,0);
            scheduler.deskActionStarted();scheduler.deskActionStarted();
            assertFalse(scheduler.deskFirst(true,90,capacity,1));
        }
    }
}
