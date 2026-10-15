package dev.doughbay.fabric.automation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RetailBatchSchedulerTest {
    @Test void continuingRetailBatchGivesOtherMarketsATurnAfterFourListings(){
        var scheduler=new RetailBatchScheduler();
        for(int i=0;i<4;i++){assertFalse(scheduler.yieldToDesk(true,i));scheduler.listed(true);}
        assertTrue(scheduler.yieldToDesk(true,10));
        scheduler.deskActionStarted();assertFalse(scheduler.yieldToDesk(true,11));
    }
    @Test void pageCleanupOrSettlementDoesNotCountAsAnExecutedDeskAction(){
        var scheduler=ready();
        for(int i=0;i<100;i++)assertTrue(scheduler.yieldToDesk(true,i));
        scheduler.deskActionStarted();assertFalse(scheduler.yieldToDesk(true,101));
    }
    @Test void unavailableDeskCannotStrandTheStockAlreadyInHand(){
        var scheduler=ready();assertTrue(scheduler.yieldToDesk(true,1000));
        assertTrue(scheduler.yieldToDesk(true,15999));assertFalse(scheduler.yieldToDesk(true,16000));
        assertFalse(scheduler.yieldToDesk(true,16001));scheduler.listed(true);
        assertFalse(scheduler.yieldToDesk(true,16002));
    }
    @Test void unknownRecoveryStockAndOrdinaryStackListingsKeepTheirExistingPriority(){
        var scheduler=ready();assertFalse(scheduler.yieldToDesk(false,0));
        assertFalse(scheduler.yieldToDesk(true,1));scheduler=ready();scheduler.listed(false);
        assertFalse(scheduler.yieldToDesk(true,2));
    }
    private static RetailBatchScheduler ready(){var s=new RetailBatchScheduler();for(int i=0;i<4;i++)s.listed(true);return s;}
}
