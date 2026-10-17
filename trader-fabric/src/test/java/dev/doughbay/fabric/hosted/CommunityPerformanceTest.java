package dev.doughbay.fabric.hosted;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class CommunityPerformanceTest {
    @Test void offlineAndFrozenIntervalsAreNotConnectedTime() {
        var stats = new CommunityPerformance(); stats.enable();
        stats.tick(true, 1_000_000_000L); stats.tick(true, 2_000_000_000L);
        stats.tick(false, 3_000_000_000L); stats.tick(true, 50_000_000_000L);
        stats.tick(true, 60_000_000_000L); stats.tick(true, 61_000_000_000L);
        assertEquals(2000, stats.view().path("connectedMs").asLong());
    }
    @Test void OnlySuccessfulMatchingSameRegionReadsCountAsHypotheticalSavings() {
        var stats = new CommunityPerformance(); stats.enable();
        stats.end(stats.begin(true, 100, "asia-2", 0), true, 105, "asia-2", 0, 0, 2_000_000_000L);
        stats.end(stats.begin(true, 100, "asia-2", 0), true, 150, "asia-2", 0, 0, 2_000_000_000L);
        stats.end(stats.begin(true, 100, "asia-2", 0), false, 100, "asia-2", 0, 0, 2_000_000_000L);
        stats.end(stats.begin(true, 100, "asia-2", 0), true, 100, "asia-3", 0, 0, 2_000_000_000L);
        stats.end(stats.begin(false, 0, "", 0), true, 0, "", 10, 450, 5_000_000_000L);
        var value = stats.view();
        assertEquals(8000, value.path("probeReadMs").asLong());
        assertEquals(5000, value.path("orderReadMs").asLong());
        assertEquals(1, value.path("candidateProbes").asLong());
        assertEquals(2000, value.path("hypotheticalCandidateMs").asLong());
        assertEquals(0, value.path("actualSavedMs").asLong());
        assertEquals(450, value.path("orderRows").asLong());
    }
}
