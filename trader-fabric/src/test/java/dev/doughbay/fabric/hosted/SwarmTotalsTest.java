package dev.doughbay.fabric.hosted;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class SwarmTotalsTest {
    static final long NOW=1_000_000;
    private com.fasterxml.jackson.databind.node.ObjectNode stats(){
        var s=DashboardProtocol.JSON.createObjectNode().put("at",NOW);
        var c=s.putObject("community").putObject("stored");
        c.putObject("participants").put("activeBots10m",12).put("activeHives10m",7);
        c.putObject("marketsCovered").put("itemsWithOrderLevels24h",300);
        c.putObject("storedRecords").putObject("events").put("sale",1234);
        return s;
    }
    @Test void distinguishesBotsHivesMarketsAndObservations(){
        SwarmTotals.receive(stats(),NOW);
        var rows=SwarmTotals.rows(NOW);
        assertEquals("7",rows.get(0)[1]);assertEquals("12",rows.get(1)[1]);
        assertEquals("300",rows.get(2)[1]);assertEquals("1,234",rows.get(3)[1]);
        assertTrue(rows.get(0)[0].contains("10 min"));
    }
    @Test void expiredMissingFutureAndDisconnectedDataNeverLooksCurrent(){
        SwarmTotals.receive(stats(),NOW);assertEquals(1,SwarmTotals.rows(NOW+180_000).size());
        SwarmTotals.receive(stats(),NOW-6000);assertEquals(1,SwarmTotals.rows(NOW).size());
        SwarmTotals.receive(stats(),NOW);SwarmTotals.receive(null,NOW);assertEquals(1,SwarmTotals.rows(NOW).size());
        SwarmTotals.receive(stats(),NOW);SwarmRank.clear();assertEquals(1,SwarmTotals.rows(NOW).size());
    }
    @Test void unavailableAndInvalidCountsAreNotZero(){
        var s=stats();var p=(com.fasterxml.jackson.databind.node.ObjectNode)s.path("community").path("stored").path("participants");
        p.put("activeHives10m",-1);p.remove("activeBots10m");SwarmTotals.receive(s,NOW);
        assertEquals("Unavailable",SwarmTotals.rows(NOW).get(0)[1]);
        assertEquals("Unavailable",SwarmTotals.rows(NOW).get(1)[1]);
        p.put("activeHives10m",0);SwarmTotals.receive(s,NOW);assertEquals("0",SwarmTotals.rows(NOW).get(0)[1]);
        p.put("activeHives10m",1.5);SwarmTotals.receive(s,NOW);assertEquals("Unavailable",SwarmTotals.rows(NOW).get(0)[1]);
    }
    @Test void frameExpiryAndAccessWithholdingAreHonoredIndependentlyOfRank(){
        var frame=DashboardProtocol.JSON.createObjectNode().put("frameVersion",2).put("at",NOW)
                .put("expiresAt",NOW+120_000).put("hive",3);
        frame.set("stats",stats());SwarmTotals.receiveFrame(frame,NOW);
        assertEquals("7",SwarmTotals.rows(NOW).get(0)[1]);
        assertEquals("3",SwarmTotals.rows(NOW).getLast()[1]);
        assertEquals(1,SwarmTotals.rows(NOW+120_000).size());
        frame.remove("stats");frame.put("statsWithheld","not_participating");
        frame.putObject("swarm").put("at",NOW).put("active",12).put("ttlSeconds",600);
        SwarmTotals.receiveFrame(frame,NOW);
        assertEquals("Unavailable",SwarmTotals.rows(NOW).get(0)[1]);
        assertEquals("12",SwarmTotals.rows(NOW).get(1)[1]);
        frame.put("expiresAt",NOW+120_001);SwarmTotals.receiveFrame(frame,NOW);
        assertEquals(1,SwarmTotals.rows(NOW).size());
    }
}
