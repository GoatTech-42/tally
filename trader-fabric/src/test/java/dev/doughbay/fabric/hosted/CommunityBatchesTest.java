package dev.doughbay.fabric.hosted;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class CommunityBatchesTest {
    @Test void unknownAndNetworkObservationsNeverShareABatch(){
        var queue=new ArrayDeque<CommunityBatches.Observation>();
        var row=CommunityWire.payment("Payer","Recipient",5,123);
        queue.add(new CommunityBatches.Observation(row,""));
        queue.add(new CommunityBatches.Observation(row,"net-donut"));
        var unknown=CommunityBatches.pack(queue,Set.of("payment"),200).body();
        assertFalse(unknown.has("region"));assertEquals(1,unknown.path("records").size());
        var scoped=CommunityBatches.pack(queue,Set.of("payment"),201).body();
        assertEquals("net-donut",scoped.path("region").asText());assertEquals(1,scoped.path("records").size());
        assertFalse(scoped.path("records").get(0).has("region"));assertTrue(queue.isEmpty());
    }
    @Test void supportRequiresTheExactAdvertisedProtocolAndNeverAcceptsPhysicalShards(){
        var c=DashboardProtocol.JSON.createObjectNode();c.putArray("networkScopes").add("net-donut");
        assertFalse(CommunityBatches.networkAdvertised(c));
        c.put("regionField","region").put("regionQueryParameter","region");assertTrue(CommunityBatches.networkAdvertised(c));
        assertThrows(IllegalArgumentException.class,()->new CommunityBatches.Observation(CommunityWire.payment("Payer","Recipient",5,123),"asia-2"));
    }
    @Test void rowsAreCopiedAndUnsupportedTypesAreDroppedWithoutChangingScope(){
        var queue=new ArrayDeque<CommunityBatches.Observation>();var row=CommunityWire.payment("Payer","Recipient",5,123);
        queue.add(new CommunityBatches.Observation(CommunityWire.event("unsupported",123),""));
        queue.add(new CommunityBatches.Observation(row,"net-donut"));row.put("region","asia-2");
        var packed=CommunityBatches.pack(queue,Set.of("payment"),200);
        assertEquals(1,packed.dropped());assertEquals("net-donut",packed.body().path("region").asText());
        assertFalse(packed.body().path("records").get(0).has("region"));
    }
}
