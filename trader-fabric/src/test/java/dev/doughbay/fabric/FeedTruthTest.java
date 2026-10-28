package dev.doughbay.fabric;

import static org.junit.jupiter.api.Assertions.*;
import dev.doughbay.core.model.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FeedTruthTest {
    static final long NOW = 1_700_000_000_000L;

    static MarketWatcher.Snapshot snap(List<MarketStats> markets, String status, long updatedAt) {
        return new MarketWatcher.Snapshot(List.of(), List.of(), markets, List.of(),
                Map.of(), Map.of(), null, "", -1, status, null, updatedAt, updatedAt, false);
    }

    static MarketStats market() {
        return new MarketStats("minecraft:stone", StackBucket.X64, 86_400_000L, 100, 0, 4, NOW,
                12800, 12800, 12800, 12800, 12800, 100, 0.02, 0.01, 0.9, NOW);
    }

    @Test void demoIsDemo() {
        assertEquals(FeedTruth.DEMO, FeedTruth.of(MarketWatcher.Snapshot.demo(NOW), NOW));
    }
    @Test void stoppedWinsOverFreshness() {
        assertEquals(FeedTruth.STOPPED, FeedTruth.of(snap(List.of(), "STOPPED", NOW), NOW));
    }
    @Test void neverPublishedIsStarting() {
        assertEquals(FeedTruth.STARTING, FeedTruth.of(MarketWatcher.Snapshot.idle("OK"), NOW));
    }
    @Test void oldSnapshotIsStale() {
        assertEquals(FeedTruth.STALE, FeedTruth.of(snap(List.of(), "OK", NOW - 120_001), NOW));
    }
    @Test void freshnessBoundaryIsStillFresh() {
        assertEquals(FeedTruth.LIVE_API, FeedTruth.of(snap(List.of(), "OK", NOW - 120_000), NOW));
    }
    @Test void freshApiScanIsLive() {
        assertEquals(FeedTruth.LIVE_API, FeedTruth.of(snap(List.of(), "OK", NOW), NOW));
    }
    @Test void keylessHeartbeatWithNoMarketsIsCollecting() {
        assertEquals(FeedTruth.COLLECTING, FeedTruth.of(
                snap(List.of(), "KEYLESS: 0 market(s) valued; 0 sale(s) observed in the last hour", NOW), NOW));
    }
    @Test void keylessHeartbeatWithHistoryMarketsIsLocalHistory() {
        assertEquals(FeedTruth.LOCAL_HISTORY, FeedTruth.of(
                snap(List.of(market()), "KEYLESS: 1 market(s) valued; 0 sale(s) observed in the last hour", NOW), NOW));
    }
    @Test void keylessStaleIsStaleNotLive() {
        assertEquals(FeedTruth.STALE, FeedTruth.of(snap(List.of(market()), "KEYLESS: 1 market(s) valued", NOW - 300_000), NOW));
    }
    @Test void degradedScanIsNotLive() {
        assertEquals(FeedTruth.DEGRADED, FeedTruth.of(
                snap(List.of(), "DEGRADED: completed-sale validation failed; valuations locked", NOW), NOW));
    }
    @Test void partialScanIsNotLive() {
        assertEquals(FeedTruth.PARTIAL, FeedTruth.of(snap(List.of(), "PARTIAL: 3 of 10 items scanned", NOW), NOW));
    }
    @Test void labelMapsEveryState() {
        assertEquals("demo preview", FeedTruth.label(MarketWatcher.Snapshot.demo(NOW), NOW, "demo preview", "live"));
        assertEquals("collecting", FeedTruth.label(snap(List.of(), "KEYLESS: 0 market(s)", NOW), NOW, "demo preview", "live"));
        assertEquals("local history", FeedTruth.label(snap(List.of(market()), "KEYLESS: 1 market(s)", NOW), NOW, "demo preview", "live"));
        assertEquals("live", FeedTruth.label(snap(List.of(), "OK", NOW), NOW, "demo preview", "live"));
        assertTrue(FeedTruth.live(snap(List.of(), "OK", NOW), NOW));
        assertFalse(FeedTruth.live(snap(List.of(), "KEYLESS: 0 market(s)", NOW), NOW));
    }
}
