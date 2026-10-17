package dev.doughbay.fabric.hosted;
import dev.doughbay.fabric.OrderBook.Order;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class CommunityMarketGuidanceTest {
    private Order row(String item,long bid,long at,long delivered,List<String> parts){
        return new Order(at,item,item,"",parts,bid,delivered,64,1);
    }
    private CommunityMarketGuidance.Hint hint(long bid){return new CommunityMarketGuidance.Hint("minecraft:diamond",bid,"snapshot_history",false);}
    private CommunityMarketGuidance.Check check(long historic,long live,long ceiling){
        return CommunityMarketGuidance.check(hint(historic),true,live,2000,1000,Set.of(),ceiling,1000,2000,true);
    }
    @Test void livePriceReplacesHistoricalPriceInBothDirections(){
        var lower=check(900,100,500);assertEquals(101,lower.proposedUnit());assertTrue(lower.priceChanged());
        var higher=check(10,200,500);assertEquals(201,higher.proposedUnit());assertTrue(higher.placementStillRequiresValidation());
        assertEquals("outside_current_caps",check(10,600,500).status());
    }
    @Test void filledOrDisappearedOrderNeverUsesOldBid(){
        assertEquals("no_live_order",check(1000,0,2000).status());assertEquals(0,check(1000,0,2000).proposedUnit());
    }
    @Test void failedAndExpiredSearchCannotAuthorizePreview(){
        assertEquals("live_search_failed",CommunityMarketGuidance.check(hint(100),false,100,2000,1000,Set.of(),500,1000,2000,true).status());
        assertEquals("live_quote_expired",CommunityMarketGuidance.check(hint(100),true,100,1000,30000,Set.of(),500,1000,32000,true).status());
    }
    @Test void ownOrdersAndGuardRemainIndependentRequiredChecks(){
        assertEquals("own_orders_need_refresh",CommunityMarketGuidance.check(hint(100),true,100,62000,1000,Set.of(),500,1000,62000,true).status());
        assertEquals("guard_hold",CommunityMarketGuidance.check(hint(100),true,100,2000,1000,Set.of(),500,1000,2000,false).status());
        assertEquals("own_market_requires_existing_order_review",CommunityMarketGuidance.check(hint(100),true,100,2000,1000,Set.of("minecraft:diamond"),500,1000,2000,true).status());
    }
    @Test void failedQuantityAndSpendAssumptionsCannotBecomeExecutionPermission(){
        var checked=CommunityMarketGuidance.check(hint(100),true,100,2000,1000,Set.of(),500,100,2000,true);
        assertEquals("outside_current_caps",checked.status());assertEquals(0,checked.proposedUnit());
        assertEquals(0,check(1,Long.MAX_VALUE,Long.MAX_VALUE).proposedUnit());
    }
    @Test void wholePreviewLotMustFitSpendCapWithoutOverflow(){
        assertEquals("outside_current_caps",CommunityMarketGuidance.check(hint(10),true,100,2000,1000,Set.of(),64,500,6400,2000,true).status());
        assertEquals(101,CommunityMarketGuidance.check(hint(10),true,100,2000,1000,Set.of(),64,500,6464,2000,true).proposedUnit());
        assertEquals(0,CommunityMarketGuidance.check(hint(10),true,100,2000,1000,Set.of(),0,500,Long.MAX_VALUE,2000,true).proposedUnit());
    }
    @Test void onlyAllowedPlainUnfilledFreshHistoryIsUsed(){
        var rows=List.of(row("minecraft:diamond",100,1000,0,List.of()),row("minecraft:stone",100,1000,64,List.of()),
                row("minecraft:gold_ingot",100,1000,0,List.of("Mending")),row("minecraft:iron_ingot",100,1,0,List.of()),
                row("minecraft:beacon",100,1000000,0,List.of()));
        var allowed=Set.of("minecraft:diamond","minecraft:stone","minecraft:gold_ingot","minecraft:iron_ingot");
        assertEquals(List.of("minecraft:diamond"),CommunityMarketGuidance.plan(rows,Set.of(),allowed,Set.of(),Map.of(),901000,3).stream().map(CommunityMarketGuidance.Hint::itemId).toList());
    }
    @Test void followsAddDiscoveryWithoutInventingCurrentPriceOrRemovingOrders(){
        var plan=CommunityMarketGuidance.plan(List.of(row("minecraft:diamond",100,1000,0,List.of())),
                Set.of("minecraft:diamond","minecraft:stone"),Set.of("minecraft:diamond","minecraft:stone"),Set.of(),Map.of(),2000,3);
        assertEquals(2,plan.size());assertEquals(100,plan.stream().filter(h->h.itemId().equals("minecraft:diamond")).findFirst().orElseThrow().historicalBid());
        assertEquals(0,plan.stream().filter(h->h.itemId().equals("minecraft:stone")).findFirst().orElseThrow().historicalBid());
    }
    @Test void guidanceIsBoundedStableAndDoesNotSuppressMarketsWithoutOwnIdentity(){
        var allowed=Set.of("minecraft:diamond","minecraft:stone","minecraft:beacon","minecraft:iron_ingot");
        var plan=CommunityMarketGuidance.plan(List.of(),allowed,allowed,Set.of("minecraft:diamond"),Map.of(),2000,20);
        assertEquals(3,plan.size());assertFalse(plan.getFirst().ownMarket());
        assertEquals(plan,CommunityMarketGuidance.plan(List.of(),new HashSet<>(allowed),allowed,Set.of("minecraft:diamond"),Map.of(),2000,20));
        assertThrows(UnsupportedOperationException.class,()->plan.add(hint(1)));
    }
}
