package dev.doughbay.fabric.hosted;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CommunityOrderShadowTest {
    private final long now=1_800_000;
    private CommunityOrderShadow.Context context(){return new CommunityOrderShadow.Context(now,200,Set.of("minecraft:stone|64"),true,0,true);}
    private ObjectNode marker(){return DashboardProtocol.JSON.createObjectNode().put("readId","o_test_read").put("startedAt",now-200_000).put("endedAt",now-100_000).put("ageSeconds",100).put("pages",200).put("levels",1300).put("rows",1001).put("status","capped").put("capped",true).put("self",false).put("sameOwner",true);}
    private ObjectNode level(int i){return DashboardProtocol.JSON.createObjectNode().put("itemKey","minecraft:stone|64").put("unitPrice",100+i).put("total",64).put("delivered",0).put("remaining",64).put("depth",1).put("observedAt",now-150_000);}
    private ObjectNode page(int page){
        var response=DashboardProtocol.JSON.createObjectNode().put("regionScope","net-donut").put("page",page).put("pageSize",1000).put("rowsTotal",1001);
        response.set("read",marker());var rows=response.putArray("rows");
        for(int i=(page-1)*1000;i<Math.min(page*1000,1001);i++)rows.add(level(i));
        if(page==1)response.put("next",2);else response.putNull("next");return response;
    }
    private ObjectNode last(){var response=DashboardProtocol.JSON.createObjectNode().put("regionScope","net-donut");response.set("read",marker());return response;}
    @Test void eligibleLookupRequestsMatchingPeerAndEnforcesNinetySecondsLocally() throws Exception {
        var paths=new ArrayList<String>();var accepted=new ArrayList<String>();
        CommunityOrderShadow.Fetch transport=path->{paths.add(path);var reply=path.contains("last-read")?last():page(path.endsWith("page=1")?1:2);
            ((ObjectNode)reply.path("read")).put("endedAt",now-60000).put("ageSeconds",60);return reply;};
        var result=CommunityOrderShadow.fetch(transport,context(),now,"eligible",accepted::add);
        assertEquals(1001,result.rows().size());assertTrue(paths.getFirst().contains("eligible=1&maxAgeSeconds=90&minPages=200&sameOwner=1&notSelf=1"));
        assertFalse(paths.get(1).contains("eligible="));assertEquals(List.of("ordersLastRead","ordersSnapshot","ordersSnapshot"),accepted);
        assertThrows(IllegalArgumentException.class,()->CommunityOrderShadow.fetch(path->last(),context(),now,"eligible",accepted::add),"100-second read is too old even if an old server ignores filters");
    }
    @Test void emittedCountMayDifferFromFrozenRowsAndAllPagesAreRequired() throws Exception {
        var snapshot=CommunityOrderShadow.fetch(path->path.contains("last-read")?last():page(path.endsWith("page=1")?1:2),context(),now);
        assertEquals(1001,snapshot.rows().size());assertEquals(1300,snapshot.marker().path("levels").asInt());assertEquals("",snapshot.reason());
    }
    @Test void rejectsMixedScopeChangedSnapshotAndBrokenPagination(){
        assertThrows(IllegalArgumentException.class,()->CommunityOrderShadow.fetch(path->last().put("regionScope","mixed"),context(),now));
        assertThrows(IllegalArgumentException.class,()->CommunityOrderShadow.fetch(path->{if(path.contains("last-read"))return last();var p=page(path.endsWith("page=1")?1:2);if(path.endsWith("page=2"))p.put("rowsTotal",1002);return p;},context(),now));
        assertThrows(IllegalArgumentException.class,()->CommunityOrderShadow.fetch(path->path.contains("last-read")?last():page(1).put("next",1),context(),now));
    }
    @Test void ownAndForeignReadersAreNotCandidates() throws Exception {
        for(String flag:List.of("self","sameOwner")){
            var reply=last();((ObjectNode)reply.path("read")).put(flag,flag.equals("self"));
            var result=CommunityOrderShadow.fetch(path->{assertTrue(path.contains("last-read"));return reply;},context(),now);
            assertTrue(Set.of("self_read","other_owner").contains(result.reason()));assertTrue(result.rows().isEmpty());
        }
        var none=last();none.putNull("read");assertEquals("no_full_read",CommunityOrderShadow.fetch(path->none,context(),now).reason());
    }
    @Test void rejectsStaleOrInsufficientReads() throws Exception {
        var stale=last();((ObjectNode)stale.path("read")).put("ageSeconds",901);
        assertThrows(IllegalArgumentException.class,()->CommunityOrderShadow.fetch(path->stale,context(),now));
        var smaller=last();((ObjectNode)smaller.path("read")).put("pages",100);
        assertEquals("insufficient_page_coverage",CommunityOrderShadow.fetch(path->smaller,context(),now).reason());
    }
    @Test void postReadValidationTagsEveryRequestAndCannotBecomeASkipCandidate() throws Exception {
        var requests=new java.util.ArrayList<String>();
        var snapshot=CommunityOrderShadow.fetch(path->{requests.add(path);return path.contains("last-read")?last():page(path.contains("page=1")?1:2);},context(),now,"validation");
        assertEquals(3,requests.size());assertTrue(requests.stream().allMatch(path->path.contains("purpose=validation")));
        var result=CommunityOrderShadow.validation(snapshot,now);
        assertTrue(result.path("validated").asBoolean());assertTrue(result.path("validationOnly").asBoolean());
        assertFalse(result.path("candidateBeforeLiveRead").asBoolean());assertFalse(result.path("wouldSkip").asBoolean());
        assertFalse(result.path("skipFreshnessEvaluated").asBoolean());assertEquals(2,result.path("pagesFetched").asInt());assertEquals(1001,result.path("rowsReceived").asInt());
        assertEquals("post_read_validation",result.path("phase").asText());
    }
    @Test void transportValidationMayUseRetainedSnapshotsButPreReadFreshnessStillApplies() throws Exception {
        CommunityOrderShadow.Fetch old=path->{var response=path.contains("last-read")?last():page(path.contains("page=1")?1:2);for(var row:response.path("rows"))((ObjectNode)row).put("observedAt",now-1_250_000);((ObjectNode)response.path("read")).put("ageSeconds",1200).put("startedAt",now-1_300_000).put("endedAt",now-1_200_000);return response;};
        assertThrows(IllegalArgumentException.class,()->CommunityOrderShadow.fetch(old,context(),now));
        assertEquals(1001,CommunityOrderShadow.fetch(old,context(),now,"validation").rows().size());
        assertThrows(IllegalArgumentException.class,()->CommunityOrderShadow.fetch(old,context(),now,"unsupported"));
    }
    @Test void validationNeverBypassesOtherHiveClientRequirement() throws Exception {
        var self=last();((ObjectNode)self.path("read")).put("self",true);
        var result=CommunityOrderShadow.fetch(path->{assertTrue(path.contains("last-read"));return self;},context(),now,"validation");
        assertEquals("self_read",result.reason());assertFalse(CommunityOrderShadow.validation(result,now).path("validated").asBoolean());
    }
    @Test void comparisonsAreDiagnosticAndNeverClaimSavedTimeOrEquivalentDecisions(){
        var snapshot=new CommunityOrderShadow.Snapshot(marker(),List.of(level(0)),"");
        var live=List.of(CommunityWire.orderLevel("minecraft:stone",64,200,64,0,now,1));
        var result=CommunityOrderShadow.compare(snapshot,context(),live,now+1000,true);
        assertEquals(1,result.path("missingFromSnapshot").asInt());assertEquals(1,result.path("notObservedLive").asInt());
        assertEquals(1,result.path("highestBidDifferencesCount").asInt());assertFalse(result.path("wouldSkip").asBoolean());
        assertFalse(result.path("skipped").asBoolean());assertEquals(0,result.path("actualSavedMs").asLong());assertEquals("not_simulated",result.path("tradingDecisionComparison").asText());
        var own=new CommunityOrderShadow.Context(now,200,context().managed(),true,1,true);
        assertEquals("own_order_identity_unverifiable",CommunityOrderShadow.compare(snapshot,own,live,now,true).path("reason").asText());
    }
}
