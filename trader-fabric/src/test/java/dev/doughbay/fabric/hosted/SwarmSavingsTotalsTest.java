package dev.doughbay.fabric.hosted;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class SwarmSavingsTotalsTest {
    private com.fasterxml.jackson.databind.node.ObjectNode metrics(long skips,long wait,long avoided){
        return DashboardProtocol.JSON.createObjectNode().put("attempts",skips+1).put("actualSkippedReads",skips)
                .put("fallbackReads",1).put("lookupWaitMs",wait).put("calibratedSkippedReads",skips)
                .put("uncalibratedSkippedReads",0).put("estimatedAvoidedReadMs",avoided)
                .put("estimatedNetSavedMs",999999).put("estimatedMatchedReadSpeedRatio",999);
    }
    private void add(com.fasterxml.jackson.databind.node.ArrayNode rows,String id,boolean live,com.fasterxml.jackson.databind.JsonNode m){
        rows.addObject().put("id",id).put("available",live).putObject("community").set("readSavings",m);
    }
    @Test void combinesEachAccountOnceAndRecomputesNetWithoutAveragingRatios(){
        var rows=DashboardProtocol.JSON.createArrayNode();add(rows,"a",true,metrics(2,1000,5000));
        add(rows,"b",true,metrics(3,9000,2000));add(rows,"a",true,metrics(2,1000,5000));
        var total=SwarmSavingsTotals.hive(rows);
        assertEquals(2,total.path("reportingAccounts").asInt());assertEquals(2,total.path("knownAccounts").asInt());
        assertEquals(5,total.path("actualSkippedReads").asLong());assertEquals(-3000,total.path("estimatedNetSavedMs").asLong());
        assertFalse(total.has("estimatedMatchedReadSpeedRatio"));
    }
    @Test void offlineOldAndMalformedClientsRemainUnmeasured(){
        var rows=DashboardProtocol.JSON.createArrayNode();add(rows,"a",false,metrics(2,1,5));
        add(rows,"b",true,DashboardProtocol.JSON.createObjectNode());
        add(rows,"c",true,metrics(2,1,5).put("attempts",99));
        var total=SwarmSavingsTotals.hive(rows);assertEquals(3,total.path("missingAccounts").asInt());
        assertFalse(total.path("hasMeasurements").asBoolean());
    }
    @Test void aMissingDuplicateCannotHideTheFreshAccountSnapshot(){
        var rows=DashboardProtocol.JSON.createArrayNode();add(rows,"a",false,metrics(2,1,5));
        add(rows,"a",true,metrics(2,1,5));var total=SwarmSavingsTotals.hive(rows);
        assertEquals(1,total.path("knownAccounts").asInt());assertEquals(1,total.path("reportingAccounts").asInt());
        assertEquals(2,total.path("actualSkippedReads").asLong());
    }
    @Test void rejectsNegativeFractionalAndOverflowingInputs(){
        assertNull(SwarmSavingsTotals.validated(metrics(2,1,5).put("lookupWaitMs",-1)));
        assertNull(SwarmSavingsTotals.validated(metrics(2,1,5).put("lookupWaitMs",.5)));
        assertNull(SwarmSavingsTotals.validated(metrics(2,1,5).put("lookupWaitMs",Long.MAX_VALUE)));
    }
    @Test void calibratedAndUncalibratedCoverageIsNotPresentedAsComplete(){
        var rows=DashboardProtocol.JSON.createArrayNode();add(rows,"a",true,metrics(2,1,5));
        add(rows,"b",true,metrics(3,2,0).put("calibratedSkippedReads",0).put("uncalibratedSkippedReads",3));
        var total=SwarmSavingsTotals.hive(rows);assertTrue(total.path("estimateAvailable").asBoolean());
        assertFalse(total.path("estimateComplete").asBoolean());assertEquals(3,total.path("uncalibratedSkippedReads").asLong());
    }
}
