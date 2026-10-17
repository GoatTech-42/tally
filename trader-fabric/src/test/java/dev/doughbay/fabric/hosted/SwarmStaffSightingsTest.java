package dev.doughbay.fabric.hosted;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SwarmStaffSightingsTest {
    final long now=2_000_000;
    @Test void onlyLocalPositiveDetectorEvidenceIsShared() {
        var feed=new SwarmStaffSightings();
        feed.observe("DemoStaff","na-west-3","OBSERVED",now,false,true,now);
        feed.observe("DemoStaff","na-west-3","HIDDEN_FROM_TAB",now,true,true,now);
        feed.observe("DemoStaff","na-west-3","NO_LONGER_OBSERVED",now,true,true,now);
        assertTrue(feed.snapshot(true,now).isEmpty());
        feed.observe("DemoStaff","na-west-3","NEARBY",now,true,true,now);
        var row=feed.snapshot(true,now).get(0);
        assertEquals(4,row.size());assertEquals("na-west",row.path("region").asText());
        assertEquals("DemoStaff",row.path("staff").asText());assertEquals(now,row.path("observedAt").asLong());
        assertFalse(row.has("observer"));assertFalse(row.has("account"));
    }
    @Test void lossOfMembershipDropsHistoryAndDoesNotReplayAfterRejoin() {
        var feed=new SwarmStaffSightings();
        feed.observe("DemoStaff","eu-central-2","SPECTATOR",now,true,true,now);
        assertEquals(1,feed.snapshot(true,now).size());
        assertTrue(feed.snapshot(false,now).isEmpty());
        assertTrue(feed.snapshot(true,now+1).isEmpty());
        feed.observe("DemoStaff","eu-central-2","SPECTATOR",now+1,true,false,now+1);
        assertTrue(feed.snapshot(true,now+2).isEmpty());
    }
    @Test void dedupPreservesLatestEventAndDoesNotRefreshOldTimestamps() {
        var feed=new SwarmStaffSightings();
        feed.observe("DemoStaff","na-east-1","OBSERVED",now-2,true,true,now);
        feed.observe("demostaff","na-east-2","NEARBY",now,true,true,now);
        feed.observe("DEMOSTAFF","na-east-3","OBSERVED",now-1,true,true,now);
        assertEquals(1,feed.snapshot(true,now).size());
        assertEquals("NEARBY",feed.snapshot(true,now).get(0).path("event").asText());
        assertEquals(now,feed.snapshot(true,now+100).get(0).path("observedAt").asLong());
        assertTrue(feed.snapshot(true,now+SwarmStaffSightings.TTL+1).isEmpty());
    }
    @Test void malformedFutureAndStaleEventsNeverEnterFeed() {
        var feed=new SwarmStaffSightings();
        for(long at:new long[]{0,now+1,now-SwarmStaffSightings.TTL-1})
            feed.observe("DemoStaff","asia-4","NEARBY",at,true,true,now);
        feed.observe("<script>","asia-4","NEARBY",now,true,true,now);
        feed.observe("DemoStaff","asia-4",null,now,true,true,now);
        assertTrue(feed.snapshot(true,now).isEmpty());
        feed.observe("DemoStaff","server.example","NEARBY",now,true,true,now);
        assertEquals("unknown",feed.snapshot(true,now).get(0).path("region").asText());
    }
    @Test void bufferIsBoundedAndClockRollbackClearsFutureRecords() {
        var feed=new SwarmStaffSightings();
        for(int i=0;i<30;i++)feed.observe("Staff"+i,"oceania-1","NEARBY",now+i,true,true,now+i);
        assertEquals(20,feed.snapshot(true,now+30).size());
        assertEquals("Staff29",feed.snapshot(true,now+30).get(0).path("staff").asText());
        assertTrue(feed.snapshot(true,now-1).isEmpty());
    }
    @Test void regionNeverLeaksShardOrGuessesCoordinates() {
        assertEquals("eu-west",SwarmStaffSightings.broadRegion("eu-west-12"));
        for(String label:new String[]{"", "Unknown", "NA West 3", "x=123 z=456", "na-west-1234"})
            assertEquals("unknown",SwarmStaffSightings.broadRegion(label));
    }
    @Test void alreadyObservedHandoffSurvivesBufferClearWithoutReplayingHistory() {
        var feed=new SwarmStaffSightings();
        assertTrue(feed.observe("DemoStaff","na-west-1","NEARBY",now,true,true,now));
        var queued=feed.snapshot(true,now);
        assertTrue(feed.snapshot(false,now+1).isEmpty());
        assertEquals(1,queued.size());
        assertEquals(now,queued.get(0).path("observedAt").asLong());
        assertTrue(feed.snapshot(true,now+2).isEmpty());
    }
}
