package dev.doughbay.storage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class StaffActivityStoreTest {
    @TempDir Path dir;
    StaffActivityStore.Sample sample(long at,String observer,boolean watching,String scope,String... names) {
        return new StaffActivityStore.Sample(at,observer,watching,watching,scope,
            java.util.Arrays.stream(names).map(name->new StaffActivityStore.Person(name,"ONLINE",true,false)).toList());
    }
    @Test void coverageAndHistoryPersistAcrossRestartWithoutBridgingDowntime() throws Exception {
        Path file=dir.resolve("history.db");
        try(var store=new StaffActivityStore(file)) {
            store.accept(sample(100000,"Main",true,"Unknown","StaffOne"),List.of());
            store.accept(sample(105000,"Main",true,"Unknown","StaffOne"),List.of());
        }
        try(var store=new StaffActivityStore(file)) {
            store.accept(sample(200000,"Main",true,"Unknown","StaffOne"),List.of());
            var report=store.report(200000);
            assertEquals(2,report.sessions().size());assertEquals(2,report.coverage().size());
            assertEquals(1,report.current().size());assertEquals(5000,report.sessions().stream().mapToLong(s->s.to()-s.from()).sum());
        }
    }
    @Test void twoObserversKeepTheirOwnCurrentRowsAndShareHistory() throws Exception {
        Path file=dir.resolve("history.db");
        try(var main=new StaffActivityStore(file);var alt=new StaffActivityStore(file)) {
            main.accept(sample(100000,"Main",true,"East","StaffOne"),List.of());
            alt.accept(sample(100000,"Alt",true,"West","StaffOne","StaffTwo"),List.of());
            main.accept(sample(105000,"Main",true,"East","StaffOne"),List.of());
            alt.accept(sample(105000,"Alt",true,"West","StaffOne","StaffTwo"),List.of());
            var report=main.report(105000);assertEquals(2,report.watching());assertEquals(3,report.current().size());
            assertEquals(3,report.sessions().size());assertTrue(report.current().stream().anyMatch(r->r.observer().equals("Alt")));
        }
    }
    @Test void disabledOrDisconnectedObserverIsNotCurrentAndDoesNotAddCoverage() throws Exception {
        try(var store=new StaffActivityStore(dir.resolve("history.db"))) {
            store.accept(sample(100000,"Main",true,"East","StaffOne"),List.of());
            store.accept(sample(105000,"Main",true,"East","StaffOne"),List.of());
            store.accept(sample(110000,"Main",false,"East"),List.of());
            store.accept(sample(120000,"Main",false,"East"),List.of());
            var report=store.report(120000);assertTrue(report.current().isEmpty());assertEquals(0,report.watching());
            assertEquals(5000,report.coverage().getFirst().to()-report.coverage().getFirst().from());
        }
    }
    @Test void absenceEndsSightingButNotObservationCoverage() throws Exception {
        try(var store=new StaffActivityStore(dir.resolve("history.db"))) {
            store.accept(sample(100000,"Main",true,"East","StaffOne"),List.of());
            store.accept(sample(105000,"Main",true,"East","StaffOne"),List.of());
            store.accept(sample(110000,"Main",true,"East"),List.of());
            var report=store.report(110000);assertTrue(report.current().isEmpty());assertEquals(1,report.watching());
            assertEquals(105000,report.sessions().getFirst().to());assertEquals(110000,report.coverage().getFirst().to());
        }
    }
    @Test void stalledSamplingAndScopeChangesSplitCoverage() throws Exception {
        try(var store=new StaffActivityStore(dir.resolve("history.db"))) {
            store.accept(sample(100000,"Main",true,"East","StaffOne"),List.of());
            store.accept(sample(105000,"Main",true,"East","StaffOne"),List.of());
            store.accept(sample(130000,"Main",true,"East","StaffOne"),List.of());
            store.accept(sample(135000,"Main",true,"West","StaffOne"),List.of());
            var report=store.report(135000);assertEquals(3,report.coverage().size());assertEquals(3,report.sessions().size());
            assertTrue(store.report(160000).current().isEmpty());
        }
    }
    @Test void durableEventsAreIdempotentAndRetainObserverEvidence() throws Exception {
        try(var store=new StaffActivityStore(dir.resolve("history.db"))) {
            var event=new StaffActivityStore.Event(100000,"StaffOne","SPECTATOR",true,"East");
            store.accept(sample(100000,"Main",true,"East","StaffOne"),List.of(event));
            store.accept(sample(105000,"Main",true,"East","StaffOne"),List.of(event));
            var events=store.report(105000).history();assertEquals(1,events.size());assertEquals("Main",events.getFirst().observer());
        }
    }
    @Test void invalidNamesAndPresenceAreRejected() {
        assertThrows(IllegalArgumentException.class,()->new StaffActivityStore.Person("<script>","ONLINE",false,false));
        assertThrows(IllegalArgumentException.class,()->new StaffActivityStore.Person("StaffOne","DefinitelyOffline",false,false));
    }
    @Test void strongerRepeatedEvidenceUpdatesOneEventRatherThanDuplicatingIt() throws Exception {
        try(var store=new StaffActivityStore(dir.resolve("history.db"))) {
            store.accept(sample(100000,"Main",true,"East","StaffOne"),List.of(new StaffActivityStore.Event(100000,"StaffOne","SPECTATOR",false,"East")));
            store.accept(sample(105000,"Main",true,"East","StaffOne"),List.of(new StaffActivityStore.Event(100000,"StaffOne","SPECTATOR",true,"East")));
            assertEquals(1,store.report(105000).history().size());assertTrue(store.report(105000).history().getFirst().sameRegion());
        }
    }
}
