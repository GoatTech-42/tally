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
    @Test void hiveObservationsProduceOneRowPerStaffMemberWithoutLosingConflictingEvidence() {
        var source = snapshot();
        var rows = List.of(
            new StaffWatchReport.Row("StaffOne", "ONLINE", false, false, "Alt", "Asia 4"),
            new StaffWatchReport.Row("staffone", "ONLINE", false, false, "Main", "Unknown"),
            new StaffWatchReport.Row("StaffTwo", "ONLINE", false, false, "Alt", "Asia 4"),
            new StaffWatchReport.Row("StaffTwo", "SPECTATOR", true, false, "Main", "NA West 2"));
        var shared = new StaffWatchReport.Snapshot(1000,true,true,rows,source.history(),"Unknown",source.summaries(),60000,2,"");
        var data = WebPages.staffData(shared,2000,"Main",ZoneId.of("UTC"));
        assertEquals(2,data.path("rows").size());
        var one = data.path("rows").get(0);
        assertEquals("Online in player list",one.path("presence").asText());
        assertEquals("Alt, Main",one.path("observer").asText());
        assertEquals("Alt: Asia 4; Main: Unknown",one.path("scope").asText());
        var two = data.path("rows").get(1);
        assertEquals("Alt: Online in player list; Main: Spectator observed",two.path("presence").asText());
        assertEquals("Alt: Region unconfirmed; Main: Same-region signal",two.path("region").asText());
        assertEquals(source.history().size(),data.path("history").size(),"History remains individual observations");
    }
    @Test void historyMergesOnlyMatchingEventsAcrossObserversWithinTenSeconds() {
        var seen = StaffWatchReport.Event.OBSERVED;
        var gone = StaffWatchReport.Event.NO_LONGER_OBSERVED;
        var history = List.of(
            new StaffWatchReport.Sighting(60000,"StaffOne",seen,false,"Unknown","Main"),
            new StaffWatchReport.Sighting(54000,"staffone",seen,true,"Asia 4","Alt"),
            new StaffWatchReport.Sighting(53000,"StaffOne",gone,false,"Unknown","Alt"),
            new StaffWatchReport.Sighting(52000,"StaffOne",seen,false,"Unknown","Main"),
            new StaffWatchReport.Sighting(51000,"StaffOne",seen,false,"Unknown","Main"),
            new StaffWatchReport.Sighting(30000,"StaffOne",seen,false,"Unknown","Alt"));
        var data = WebPages.staffHistoryData(history);
        assertEquals(5,data.size());
        assertEquals("Main, Alt",data.get(0).path("observer").asText());
        assertEquals(54000,data.get(0).path("firstAt").asLong());
        assertEquals("Main: Region unconfirmed; Alt: Same-region signal",data.get(0).path("region").asText());
        assertEquals(60000,data.get(0).path("at").asLong());
        assertEquals("Main",data.get(2).path("observer").asText());
        assertEquals("Main",data.get(3).path("observer").asText());
        assertEquals(30000,data.get(4).path("at").asLong());
    }
}
