package dev.doughbay.fabric;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.time.ZoneId;
import static org.junit.jupiter.api.Assertions.*;

class StaffWatchReportDataTest {
    StaffWatchReport.Snapshot snapshot() {
        return new StaffWatchReport.Snapshot(1000,true,true,List.of(new StaffWatchReport.Row("StaffOne","SPECTATOR",true,false,"Alt","Unknown | view 3")),
            List.of(new StaffWatchReport.Sighting(1000,"StaffOne",StaffWatchReport.Event.SPECTATOR,true,"Unknown","Alt")),"Unknown",
            List.of(new StaffActivityMetrics.Summary("StaffOne",1,60000,1000,"Insufficient coverage")),60000,2,"");
    }
    @Test void webDataContainsObserverHistoryAndCoverageWithoutTimezoneInferences() {
        var data=WebPages.staffData(snapshot(),2000,"Main",ZoneId.of("UTC"));
        assertEquals("Alt",data.path("rows").get(0).path("observer").asText());
        assertEquals("Unknown",data.path("rows").get(0).path("scope").asText());
        assertEquals("Spectator observed",data.path("rows").get(0).path("presence").asText());
        assertEquals(60000,data.path("summaries").get(0).path("observedMillis").asLong());
        assertEquals("UTC",data.path("timeZone").asText());assertFalse(data.has("possibleFollowing"));
        assertTrue(data.path("note").asText().contains("not staff timezones"));
    }
    @Test void staleWebDataRetainsHistoryAndHidesCurrentSightings() {
        var data=WebPages.staffData(snapshot(),20000,"Main",ZoneId.of("UTC"));
        assertTrue(data.path("rows").isEmpty());assertFalse(data.path("fresh").asBoolean());
        assertEquals(1,data.path("history").size());assertEquals(1,data.path("summaries").size());
    }
}
