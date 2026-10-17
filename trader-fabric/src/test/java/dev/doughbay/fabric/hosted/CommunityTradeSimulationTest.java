package dev.doughbay.fabric.hosted;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
class CommunityTradeSimulationTest {
    private CommunityTradeSimulation.Result evaluate(List<CommunityTradeSimulation.Level> rows,long ask,long cap,long room,boolean owned,boolean hold){
        return CommunityTradeSimulation.evaluate(rows,0,64,ask,1000,1200,900,owned,cap,room,100,10,1500,true,hold);
    }
    private List<CommunityTradeSimulation.Level> rows(){return List.of(new CommunityTradeSimulation.Level(100,64));}
    @Test void completePaperCandidateUsesActualPricesQuantityAndLimits(){
        var r=evaluate(rows(),5000,6000,6000,false,false);
        assertEquals("paper_candidate_qualified",r.reason());assertEquals(6400,r.deliveryGross());
        assertEquals(1400,r.netProfit());assertEquals(28,r.roi());assertTrue(r.confirmationRequired());
    }
    @Test void partiallyFilledHighestOrderFallsBackToQualifyingLowerOrder(){
        var r=evaluate(List.of(new CommunityTradeSimulation.Level(200,1),new CommunityTradeSimulation.Level(100,64)),5000,6000,6000,false,false);
        assertEquals(100,r.deliveryUnit());assertEquals("paper_candidate_qualified",r.reason());
        assertEquals("insufficient_live_order_quantity",evaluate(List.of(new CommunityTradeSimulation.Level(200,1)),1,10000,10000,false,false).reason());
    }
    @Test void quantitiesAreNeverSummedAcrossOrders(){
        assertEquals("insufficient_live_order_quantity",evaluate(List.of(new CommunityTradeSimulation.Level(100,32),new CommunityTradeSimulation.Level(100,32)),1,10000,10000,false,false).reason());
    }
    @Test void exactCapsProfitAndRoiStayIndependent(){
        assertEquals("purchase_cap_exceeded",evaluate(rows(),5000,4999,6000,false,false).reason());
        assertEquals("outstanding_cap_exceeded",evaluate(rows(),5000,6000,4999,false,false).reason());
        assertEquals("minimum_profit_not_met",evaluate(rows(),6350,10000,10000,false,false).reason());
        assertEquals("minimum_roi_not_met",evaluate(rows(),6000,10000,10000,false,false).reason());
    }
    @Test void missingAuctionOwnMarketAndRetainedExposureCannotPass(){
        assertEquals("no_matching_live_auction",evaluate(rows(),0,6000,6000,false,false).reason());
        assertEquals("own_market_requires_review",evaluate(rows(),5000,6000,6000,true,false).reason());
        assertEquals("unresolved_exposure_hold",evaluate(rows(),5000,6000,6000,false,true).reason());
    }
    @Test void staleGuardedAndUnparsedEvidenceCannotPass(){
        assertEquals("live_evidence_expired",CommunityTradeSimulation.evaluate(rows(),0,64,5000,1000,32000,31000,false,6000,6000,100,10,32000,true,false).reason());
        assertEquals("order_quantity_parse_uncertain",CommunityTradeSimulation.evaluate(rows(),1,64,5000,1000,1200,900,false,6000,6000,100,10,1500,true,false).reason());
        assertEquals("guard_hold",CommunityTradeSimulation.evaluate(rows(),0,64,5000,1000,1200,900,false,6000,6000,100,10,1500,false,false).reason());
        assertEquals("own_orders_need_refresh",CommunityTradeSimulation.evaluate(rows(),0,64,5000,61000,62000,900,false,6000,6000,100,10,62000,true,false).reason());
    }
    @Test void displayUncertaintySubtractsFromCapacityConservatively(){
        assertEquals(1,CommunityTradeSimulation.remainingLowerBound(1000,900,100));
        assertEquals(100,CommunityTradeSimulation.remainingLowerBound(1000,900,1));
        assertEquals(0,CommunityTradeSimulation.remainingLowerBound(1000,901,100));
        assertEquals(0,CommunityTradeSimulation.remainingLowerBound(1000,-1,1));
    }
    @Test void anonymousAuctionPricesSupportPaperEconomicsButPossibleOwnLotsAreExcluded(){
        assertTrue(CommunityTradeSimulation.usableAuctionLot(64,64,true,false));
        assertFalse(CommunityTradeSimulation.usableAuctionLot(1,64,true,false));
        assertFalse(CommunityTradeSimulation.usableAuctionLot(64,64,false,false));
        assertFalse(CommunityTradeSimulation.usableAuctionLot(64,64,true,true));
    }
    @Test void retainedHoldKeepsEconomicObservationWithoutPermission(){
        var r=evaluate(rows(),5000,6000,6000,false,true);
        assertEquals("unresolved_exposure_hold",r.reason());assertEquals(1400,r.netProfit());assertTrue(r.confirmationRequired());
    }
    @Test void overflowAndInvalidLimitsAreRejected(){
        assertEquals("amount_overflow",evaluate(List.of(new CommunityTradeSimulation.Level(Long.MAX_VALUE,64)),1,Long.MAX_VALUE,Long.MAX_VALUE,false,false).reason());
        assertEquals("invalid_profit_limits",CommunityTradeSimulation.evaluate(rows(),0,64,5000,1000,1200,900,false,6000,6000,Double.NaN,10,1500,true,false).reason());
    }
}
