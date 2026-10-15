package dev.doughbay.fabric;
import dev.doughbay.storage.StaffActivityStore;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.time.*;
import static org.junit.jupiter.api.Assertions.*;

class StaffActivityMetricsTest {
    StaffActivityStore.Interval interval(long from,long to){return new StaffActivityStore.Interval(from,to);}
    @Test void overlappingHiveSightingsCountOnce() {
        var spans=List.of(interval(1000,6000),interval(3000,8000),interval(8000,9000));
        assertEquals(8000,StaffActivityMetrics.duration(spans));assertEquals(1,StaffActivityMetrics.union(spans).size());
    }
    @Test void observationGapsRemainExcluded() {
        assertEquals(2000,StaffActivityMetrics.duration(List.of(interval(1000,2000),interval(5000,6000))));
        assertEquals(2,StaffActivityMetrics.union(List.of(interval(1000,2000),interval(5000,6000))).size());
    }
    @Test void aFewMinutesDoNotProduceAvailabilityClaims() {
        var report=new StaffActivityStore.Report(List.of(),List.of(new StaffActivityStore.Session("StaffOne",1000,6000,"ONLINE","Main","Unknown")),List.of(interval(1000,6000)),List.of(),1);
        assertTrue(StaffActivityMetrics.summarize(report,ZoneId.of("UTC")).getFirst().usualHours().startsWith("Insufficient coverage"));
    }
    @Test void observedHoursUseCoveredTimeAndViewerTimezoneRatherThanStaffTimezone() {
        var coverage=new ArrayList<StaffActivityStore.Interval>();var sessions=new ArrayList<StaffActivityStore.Session>();
        for(int day=1;day<=3;day++) {
            long at=ZonedDateTime.of(2026,10,day,18,0,0,0,ZoneId.of("UTC")).toInstant().toEpochMilli();
            coverage.add(interval(at,at+3_600_000));
            sessions.add(new StaffActivityStore.Session("StaffOne",at,at+1_800_000,"ONLINE","Main","Unknown"));
            sessions.add(new StaffActivityStore.Session("staffone",at,at+1_800_000,"ONLINE","Alt","Unknown"));
        }
        var report=new StaffActivityStore.Report(List.of(),sessions,coverage,List.of(),2);
        var utc=StaffActivityMetrics.summarize(report,ZoneId.of("UTC")).getFirst();
        assertEquals(5_400_000,utc.observedMillis());assertEquals(3,utc.intervals());assertEquals("18:00 (50% observed)",utc.usualHours());
        assertEquals("11:00 (50% observed)",StaffActivityMetrics.summarize(report,ZoneId.of("America/Los_Angeles")).getFirst().usualHours());
    }
    @Test void daylightSavingBoundaryDoesNotDuplicateElapsedTime() {
        long from=Instant.parse("2026-11-01T08:30:00Z").toEpochMilli(),to=Instant.parse("2026-11-01T10:30:00Z").toEpochMilli();
        assertEquals(7_200_000,StaffActivityMetrics.duration(List.of(interval(from,to))));
    }
    @Test void staleSnapshotHidesCurrentRowsButRetainsHistory() {
        var snapshot=new StaffWatchReport.Snapshot(1000,true,true,List.of(new StaffWatchReport.Row("StaffOne","ONLINE",true,false)),List.of(),"Unknown",List.of(),5000,1,"");
        assertEquals(1,snapshot.current(2000).size());assertTrue(snapshot.current(17000).isEmpty());assertTrue(snapshot.status(17000).contains("stale"));
    }
}
