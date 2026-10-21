package dev.doughbay.fabric.hosted;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SwarmReadSavingsTest {
    private SwarmReadSavings baseline(long duration) {
        var s = new SwarmReadSavings();
        for (int i=0;i<3;i++) s.nativeRead("west-1",200,duration,1000);
        return s;
    }
    @Test void countsOnlyRealReusesAndIncludesFailedLookupOverhead() {
        var s=baseline(10000);
        s.lookup(true,"west-1",200,1000,2000);
        s.lookup(false,"west-1",200,3000,3000);
        var v=s.view();
        assertEquals(1,v.path("actualSkippedReads").asLong());
        assertEquals(1,v.path("fallbackReads").asLong());
        assertEquals(50,v.path("skipPercent").asDouble());
        assertEquals(6000,v.path("estimatedNetSavedMs").asLong());
        assertEquals(10,v.path("estimatedMatchedReadSpeedRatio").asDouble());
        assertFalse(v.path("causalSpeedupProven").asBoolean());
    }
    @Test void slowSharedLookupShowsNegativeSavingsAndSubOneRatio() {
        var s=baseline(1000);s.lookup(true,"west-1",200,2000,2000);
        assertEquals(-1000,s.view().path("estimatedNetSavedMs").asLong());
        assertEquals(.5,s.view().path("estimatedMatchedReadSpeedRatio").asDouble());
    }
    @Test void neverInventsBaselineForOtherRegionsOrShorterScans() {
        var s=baseline(10000);
        s.lookup(true,"east-1",200,100,2000);s.lookup(true,"west-1",20,100,2000);
        assertEquals(2,s.view().path("uncalibratedSkippedReads").asLong());
        assertFalse(s.view().path("estimateAvailable").asBoolean());
        assertFalse(s.view().has("estimatedMatchedReadSpeedRatio"));
    }
    @Test void expiresBaselineAndDoesNotUseFutureOrInsufficientSamples() {
        var s=baseline(10000);s.lookup(true,"west-1",200,100,1_802_000);
        assertFalse(s.view().path("estimateAvailable").asBoolean());
        var future=baseline(10000);future.lookup(true,"west-1",200,100,500);
        assertFalse(future.view().path("estimateAvailable").asBoolean());
        var thin=new SwarmReadSavings();thin.nativeRead("west-1",200,10000,1000);
        thin.lookup(true,"west-1",200,100,2000);
        assertFalse(thin.view().path("estimateAvailable").asBoolean());
    }
    @Test void estimatesUseMedianAndNeverBackfillEarlierDecisions() {
        var s=new SwarmReadSavings();s.lookup(true,"west-1",200,100,1000);
        for(long ms:new long[]{10000,10000,900000})s.nativeRead("west-1",200,ms,1500);
        s.lookup(true,"west-1",200,1000,2000);
        assertEquals(10000,s.view().path("estimatedAvoidedReadMs").asLong());
        assertEquals(8900,s.view().path("estimatedNetSavedMs").asLong());
        assertFalse(s.view().path("estimateComplete").asBoolean());
        assertEquals(1,s.view().path("uncalibratedSkippedReads").asLong());
    }
    @Test void emptySessionAndZeroLatencyDoNotClaimInfiniteSpeed() {
        var s=baseline(10000);assertFalse(s.view().has("skipPercent"));
        s.lookup(true,"west-1",200,0,2000);
        assertFalse(s.view().has("estimatedMatchedReadSpeedRatio"));
        assertTrue(SwarmReadSavings.lines(s.view()).stream().anyMatch(t->t.contains("not a controlled")));
    }
}
