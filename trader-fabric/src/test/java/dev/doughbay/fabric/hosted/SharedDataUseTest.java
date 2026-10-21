package dev.doughbay.fabric.hosted;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SharedDataUseTest {
    private ObjectNode performance(){var p=DashboardProtocol.JSON.createObjectNode().put("session",UUID.randomUUID().toString()).put("startedAt",1000).put("observedAt",2000);p.putObject("discoverySavings").put("fallbackReads",0);return p;}
    @Test void availableHintsAreNotUseAndSourceSubsetsCanOverlap(){
        var use=new SharedDataUse();
        var hint=new CommunityDiscoveryFeed.Hint("minecraft:stone|64",1500,3,Set.of("follow_bought","priceLevels","orderLevels"),100,90);
        assertEquals(0,use.view().path("hintsConsumed").path("cloudAggregate").asLong());
        use.hint(hint);use.hint(null);var v=use.view();
        assertEquals(1,v.path("hintsConsumed").path("cloudAggregate").asLong());
        assertEquals(1,v.path("hintsConsumed").path("followTrade").asLong());
        assertEquals(1,v.path("hintsConsumed").path("priceLevel").asLong());
        assertFalse(v.toString().contains("minecraft:"));assertFalse(v.toString().contains("saved"));
    }
    @Test void snapshotIsStrictBoundedAndNeedsMatchingFallbackCount(){
        var use=new SharedDataUse();use.add("cloudAccepted","marketBatch",2);var p=performance();
        var snapshot=SharedDataUse.snapshot(p,use.view(),2000);assertNotNull(snapshot);assertEquals(9,snapshot.size());
        assertEquals(p.path("session"),snapshot.path("session"));assertEquals(2,snapshot.path("cloudAccepted").path("marketBatch").asLong());
        use.add("fallbacks","tooOld",1);assertNull(SharedDataUse.snapshot(p,use.view(),2000));
        ((ObjectNode)p.path("discoverySavings")).put("fallbackReads",1);assertNotNull(SharedDataUse.snapshot(p,use.view(),2000));
        var invalid=use.view();((ObjectNode)invalid.path("cloudAccepted")).put("market",1.5);assertNull(SharedDataUse.validated(invalid));
        invalid=use.view();((ObjectNode)invalid.path("cloudAccepted")).put("market",-1);assertNull(SharedDataUse.validated(invalid));
        invalid=use.view();((ObjectNode)invalid.path("cloudAccepted")).put("secret",123);assertNull(SharedDataUse.validated(invalid));
        assertThrows(IllegalArgumentException.class,()->use.add("cloudAccepted","unknown",1));
    }
    @Test void consentNegotiationAndFreshSessionAreRequired(){
        var frame=DashboardProtocol.JSON.createObjectNode();var p=performance();var counters=SharedDataUse.empty();
        SharedDataUse.attach(frame,p,counters,false,true,2000);assertFalse(frame.has("dataUse"));
        SharedDataUse.attach(frame,p,counters,true,false,2000);assertFalse(frame.has("dataUse"));
        SharedDataUse.attach(frame,p,counters,true,true,2000);assertTrue(frame.has("dataUse"));
        assertFalse(SharedDataUse.negotiated(DashboardProtocol.JSON.createObjectNode().put("dataUseSchema","1")));
        assertFalse(SharedDataUse.negotiated(DashboardProtocol.JSON.createObjectNode().put("dataUseSchema",4294967297L)));
        assertTrue(SharedDataUse.negotiated(DashboardProtocol.JSON.createObjectNode().put("dataUseSchema",1)));
        assertNull(SharedDataUse.snapshot(p,counters,40000));assertNull(SharedDataUse.snapshot(p.put("session","bad"),counters,2000));
    }
    @Test void hiveDeduplicatesAndMissingOldClientsAreNotZeroUse(){
        var rows=DashboardProtocol.JSON.createArrayNode();var value=SharedDataUse.empty();((ObjectNode)value.path("cloudAccepted")).put("marketBatch",7);
        rows.addObject().put("id","one").put("available",false);
        rows.addObject().put("id","one").put("available",true).putObject("community").set("dataUse",value);
        rows.addObject().put("id","one").put("available",true).putObject("community").set("dataUse",value);
        rows.addObject().put("id","old").put("available",true);
        var total=SharedDataUse.hive(rows);assertEquals(2,total.path("knownAccounts").asInt());assertEquals(1,total.path("reportingAccounts").asInt());
        assertEquals(7,total.path("cloudAccepted").path("marketBatch").asLong());assertEquals(1,total.path("missingAccounts").asInt());
        assertTrue(String.join(" ",SharedDataUse.lines(total)).contains("not scans avoided or time saved"));
    }
    @Test void fallbackMappingKeepsSafetyAndTransportDistinct(){
        assertEquals("guardNotReady",SharedDataUse.fallback("guard_or_exposure_hold"));
        assertEquals("guardNotReady",SharedDataUse.fallback("own_orders_need_refresh"));
        assertEquals("tooOld",SharedDataUse.fallback("stale_read"));assertEquals("selfRead",SharedDataUse.fallback("self_read"));
        assertEquals("incompleteCoverage",SharedDataUse.fallback("requested_markets_not_covered"));
        assertEquals("requestFailed",SharedDataUse.fallback("snapshot_timeout"));assertEquals("snapshotRejected",SharedDataUse.fallback("invalid_marker_flags"));
    }
    @Test void clockOnlyUpdatesDoNotIncreaseTelemetryCadence(){
        var a=DashboardProtocol.JSON.createObjectNode().put("at",2000);SharedDataUse.attach(a,performance(),SharedDataUse.empty(),true,true,2000);
        var b=a.deepCopy().put("at",3000);((ObjectNode)b.path("dataUse")).put("observedAt",3000);
        assertEquals(SwarmSavingsWire.comparison(a),SwarmSavingsWire.comparison(b));
        ((ObjectNode)b.path("dataUse").path("cloudAccepted")).put("marketBatch",1);assertNotEquals(SwarmSavingsWire.comparison(a),SwarmSavingsWire.comparison(b));
    }
    @Test void ipcProjectionDropsExtraFieldsRatherThanLeakingThem(){
        var input=DashboardProtocol.JSON.createObjectNode();input.set("dataUse",SharedDataUse.empty());input.put("credential","private");
        var clean=DashboardHive.communitySummary(input);assertTrue(clean.has("dataUse"));assertFalse(clean.has("credential"));
        ((ObjectNode)input.path("dataUse")).put("privatePath","private");assertFalse(DashboardHive.communitySummary(input).has("dataUse"));
    }
}
