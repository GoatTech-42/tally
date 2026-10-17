package dev.doughbay.fabric.hosted;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class CommunityGuidanceJournalTest {
    @Test void targetedOnlyJournalMakesNoBaselineSkipOrCoverageClaim(){
        var marker=DashboardProtocol.JSON.createObjectNode().put("readId","o_peer");
        var snapshot=new CommunityOrderShadow.Snapshot(marker,List.of(),"");
        var context=new CommunityOrderShadow.Context(1000,200,Set.of(),true,1,true);
        var decisions=DashboardProtocol.JSON.createObjectNode();decisions.putObject("backgroundGuidance").put("reason","guidance_checked_live");
        var result=CommunityOrderShadow.guidanceResult(snapshot,context,decisions,2000);
        assertEquals("guidance",result.path("phase").asText());assertFalse(result.path("fullReadBaselinePerformed").asBoolean());
        assertFalse(result.path("skipped").asBoolean());assertEquals(0,result.path("actualSavedMs").asLong());
        assertEquals("not_evaluated",result.path("tradingDecisionComparison").asText());assertFalse(result.has("liveReadSucceeded"));
        marker.put("readId","changed");assertEquals("o_peer",result.path("marker").path("readId").asText());
    }
}
