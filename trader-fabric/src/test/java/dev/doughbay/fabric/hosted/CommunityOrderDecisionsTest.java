package dev.doughbay.fabric.hosted;

import dev.doughbay.fabric.OrderBook.Order;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CommunityOrderDecisionsTest {
    private Order order(long price,long remaining) {
        return new Order(1000,"minecraft:diamond","minecraft:diamond","",List.of(),price,64-remaining,64,1);
    }
    @Test void partiallyFilledTopBidCannotCertifyDeliveryEquivalence() {
        var live=List.of(order(100,64),order(90,64));
        var remote=List.of(order(100,1),order(90,64));
        assertEquals(CommunityOrderDecisions.bestBids(live,Map.of(),2000,1000),
                CommunityOrderDecisions.bestBids(remote,Map.of(),2000,1000));
        assertNotEquals(CommunityOrderDecisions.deliveryEnvelope(live),CommunityOrderDecisions.deliveryEnvelope(remote));
    }
    @Test void freshDirectQuotesOverridePublicDifferencesButExpiredQuotesDoNot() {
        var live=List.of(order(100,64));var remote=List.of(order(90,64));
        Map<String,long[]> quote=Map.of("minecraft:diamond",new long[]{110,1000});
        assertEquals(CommunityOrderDecisions.bestBids(live,quote,2000,1000),
                CommunityOrderDecisions.bestBids(remote,quote,2000,1000));
        assertNotEquals(CommunityOrderDecisions.bestBids(live,quote,2001,1000),
                CommunityOrderDecisions.bestBids(remote,quote,2001,1000));
        assertArrayEquals(new long[]{110,1000},quote.get("minecraft:diamond"));
    }
    @Test void bidAndDeliveryConsumersIgnoreEnchantedAndFilledOrders() {
        var enchanted=new Order(1000,"minecraft:diamond","minecraft:diamond#abc","",List.of("Mending"),500,0,64,1);
        var rows=List.of(order(100,64),order(600,0),enchanted);
        assertEquals(Map.of("minecraft:diamond",100L),CommunityOrderDecisions.bestBids(rows,Map.of(),2000,1000));
        assertEquals(CommunityOrderDecisions.deliveryEnvelope(List.of(order(100,64))),CommunityOrderDecisions.deliveryEnvelope(rows));
    }
    @Test void duplicateObservationsDoNotAddArtificialDeliveryCapacity() {
        var first=order(100,32);var second=order(100,16);
        assertEquals(Map.of(100L,32L),CommunityOrderDecisions.deliveryEnvelope(List.of(first,first,second)).get("minecraft:diamond"));
    }
    @Test void snapshotConversionPreservesVariantPriceAndRemainingWithoutExposingMutableBook() {
        var row=DashboardProtocol.JSON.createObjectNode().put("itemKey","minecraft:diamond|64")
                .put("unitPrice",100).put("total",64).put("delivered",12).put("observedAt",1000).put("parts","Mending;Unbreaking III");
        var snapshot=new CommunityOrderShadow.Snapshot(DashboardProtocol.JSON.createObjectNode(),List.of(row),"");
        var result=CommunityOrderDecisions.snapshotRows(snapshot);
        assertEquals("minecraft:diamond",result.getFirst().itemId());
        assertEquals(List.of("Mending","Unbreaking III"),result.getFirst().parts());
        assertEquals(52,result.getFirst().remaining());assertEquals(100,result.getFirst().unitPrice());
        assertThrows(UnsupportedOperationException.class,()->result.add(order(1,1)));
        assertEquals(12,row.path("delivered").asLong());
    }
    private dev.doughbay.core.model.Opportunity candidate(String id,long price,String explanation) {
        return new dev.doughbay.core.model.Opportunity(
                new dev.doughbay.core.model.Listing(id,1000,"","order","minecraft:diamond","minecraft:diamond",64,price,null),
                null,price,price+100,100,10,.1,.95,.9,10,List.of(explanation));
    }
    @Test void explanationsDoNotChangeExecutablePurchaseInputs() {
        var a=List.of(candidate("a",100,"64 remaining"));var b=List.of(candidate("a",100,"32 remaining"));
        assertNotEquals(a,b);
        assertEquals(CommunityOrderDecisions.purchaseInputs(a),CommunityOrderDecisions.purchaseInputs(b));
    }
    @Test void priceChangesRemainRealDifferences() {
        assertNotEquals(CommunityOrderDecisions.purchaseInputs(List.of(candidate("a",100,"same"))),
                CommunityOrderDecisions.purchaseInputs(List.of(candidate("a",101,"same"))));
    }
    @Test void orderAndMultiplicityRemainDistinctFromUnorderedMembership() {
        var a=CommunityOrderDecisions.purchaseInputs(List.of(candidate("a",100,""),candidate("b",200,"")));
        var b=a.reversed();assertNotEquals(a,b);
        assertEquals(CommunityOrderDecisions.purchaseSet(a),CommunityOrderDecisions.purchaseSet(b));
        assertNotEquals(CommunityOrderDecisions.purchaseSet(a),CommunityOrderDecisions.purchaseSet(List.of(a.getFirst(),a.getFirst())));
    }
    @Test void currentReadCollapsesPageShiftsWithoutImportingRollingHistory() {
        var rows=CommunityOrderDecisions.distinctRead(List.of(order(100,32),order(100,16),order(90,64)));
        assertEquals(2,rows.size());assertEquals(16,rows.getFirst().remaining());assertEquals(100,rows.getFirst().unitPrice());
    }
}
