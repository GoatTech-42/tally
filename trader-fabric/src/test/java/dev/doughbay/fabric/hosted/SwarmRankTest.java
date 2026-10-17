package dev.doughbay.fabric.hosted;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class SwarmRankTest {
    @TempDir Path root;
    private static final long NOW=200_000_000L;
    private static final String ID="a".repeat(64);
    @AfterEach void clean(){SwarmRank.clear();}
    private static com.fasterxml.jackson.databind.node.ObjectNode frame(){
        var f=DashboardProtocol.JSON.createObjectNode().put("type","swarm_rank").put("accountId",ID);
        f.putObject("rank").put("metric","booked_realized_profit").put("windowHours",24).put("percentile",75).put("hivesCounted",12)
            .put("computedAt",NOW).put("yourFigure",-20).put("yourBookedSaleRecords",30).put("freshnessSeconds",5);
        return f;
    }
    @Test void coverageStartsUnknownSurvivesRestartAndNeverCreditsGaps() throws Exception {
        Path file=root.resolve("coverage.json");var c=new SwarmRankCoverage(file);
        assertEquals(0,c.observe(NOW));
        assertEquals(30_000.0/SwarmRankCoverage.WINDOW,c.observe(NOW+30_000),1e-12);
        c=new SwarmRankCoverage(file);
        assertEquals(60_000.0/SwarmRankCoverage.WINDOW,c.observe(NOW+60_000),1e-12);
        assertEquals(60_000.0/SwarmRankCoverage.WINDOW,c.observe(NOW+200_000),1e-12);
        assertEquals(0,c.observe(NOW));
        assertEquals(0,c.observe(NOW+2*SwarmRankCoverage.WINDOW));
        Files.writeString(file,"broken");assertEquals(0,new SwarmRankCoverage(file).observe(NOW));assertEquals("broken",Files.readString(file));
    }
    @Test void coverageOnlyReachesFullAfterObservedDay(){
        var c=new SwarmRankCoverage(root.resolve("coverage.json"));
        for(int i=0;i<1440;i++)assertTrue(c.observe(NOW+i*60_000L)<1);
        assertEquals(1,c.observe(NOW+SwarmRankCoverage.WINDOW));
    }
    @Test void failedReadBreaksCoverageEvenAcrossRestart(){
        Path file=root.resolve("gap.json");var c=new SwarmRankCoverage(file);
        c.observe(NOW);c.gap();c=new SwarmRankCoverage(file);
        assertEquals(0,c.observe(NOW+30_000));
        assertEquals(30_000.0/SwarmRankCoverage.WINDOW,c.observe(NOW+60_000),1e-12);
    }
    @Test void identitiesAreCaseInsensitiveAndDifferentForDifferentOwners(){
        String salt="x".repeat(32);
        assertEquals(SwarmRank.accountId(salt,"Example"),SwarmRank.accountId(salt,"example"));
        assertNotEquals(SwarmRank.accountId(salt,"example"),SwarmRank.accountId("y".repeat(32),"example"));
        assertEquals("",SwarmRank.accountId("","example"));assertEquals("",SwarmRank.accountId(salt,""));
    }
    @Test void ownResultExpiresAndUnqualifiedDataCannotProducePercentile(){
        SwarmRank.receive(frame(),ID,NOW);
        assertTrue(SwarmRank.rows(NOW).getFirst()[1].contains("75%"));
        assertFalse(SwarmRank.rows(NOW+90_001).getFirst()[1].contains("75%"));
        SwarmRank.receive(frame(),"b".repeat(64),NOW);assertFalse(SwarmRank.rows(NOW).getFirst()[1].contains("75%"));
        var small=frame();((com.fasterxml.jackson.databind.node.ObjectNode)small.get("rank")).put("hivesCounted",9);
        SwarmRank.receive(small,ID,NOW);assertFalse(SwarmRank.rows(NOW).getFirst()[1].contains("75%"));
        var bad=frame();((com.fasterxml.jackson.databind.node.ObjectNode)bad.get("rank")).put("percentile",Double.NaN);
        SwarmRank.receive(bad,ID,NOW);assertFalse(SwarmRank.rows(NOW).getFirst()[1].contains("75%"));
        SwarmRank.receive(frame(),ID,NOW-6000);assertFalse(SwarmRank.rows(NOW).getFirst()[1].contains("75%"));
        var unavailable=frame();var r=(com.fasterxml.jackson.databind.node.ObjectNode)unavailable.get("rank");r.removeAll();r.put("unavailable",true).put("reason","insufficient_group");
        SwarmRank.receive(unavailable,ID,NOW);assertEquals("Needs 10 qualified active Hives",SwarmRank.rows(NOW).getFirst()[1]);
    }
}
