package dev.doughbay.fabric.hosted;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class CommunityDiscoveryFeedTest {
    long now=2_000_000;
    String key="minecraft:diamond|64";
    ObjectNode answer(){return DashboardProtocol.JSON.createObjectNode().put("itemKey",key).put("regionScope","net-donut").put("generatedAt",now);}
    @Test void recentPartialLevelsSupplyDiscoveryWithoutFullReadOrAvailabilityClaims(){
        var a=answer();a.putObject("orderLevels").put("usability","history").putArray("rows").addObject().put("unitPrice",1100).put("remaining",500).put("lastSeenAt",now-90_000);
        a.putObject("priceLevels").put("usability","history").putArray("rows").addObject().put("unitPrice",1000).put("lastSeenAt",now-80_000);
        var h=CommunityDiscoveryFeed.read(key,a,now);assertNotNull(h);assertEquals(1100,h.historicalBid());assertEquals(1000,h.historicalAsk());
        assertEquals(Set.of("orderLevels","priceLevels"),h.sources());assertFalse(a.has("lastFullRead"));
    }
    @Test void followEventsGuideEvenWithNoLevelRowsButOwnEventsAndPaymentsDoNot(){
        var a=answer();var recent=a.putObject("followActivity").put("usability","history").putArray("recent");
        recent.addObject().put("kind","ordered").put("quantity",64).put("total",64000).put("at",now-1000);
        recent.addObject().put("kind","listed").put("quantity",64).put("total",64).put("at",now-1000).put("actorOwnIndex",1);
        var h=CommunityDiscoveryFeed.read(key,a,now);assertNotNull(h);assertEquals(Set.of("follow_ordered"),h.sources());assertEquals(1000,h.historicalBid());assertEquals(0,h.historicalAsk());
        a.remove("followActivity");a.putObject("payments").putArray("recent").addObject().put("amount",900000).put("at",now-1000);
        assertNull(CommunityDiscoveryFeed.read(key,a,now));
    }
    @Test void staleFutureMixedScopeWrongMarketAndAbsentDataNeverBecomeHints(){
        var a=answer();var row=a.putObject("orderLevels").put("usability","history").putArray("rows").addObject().put("unitPrice",100).put("observedAt",now-900001);
        assertNull(CommunityDiscoveryFeed.read(key,a,now));row.put("observedAt",now+6000);assertNull(CommunityDiscoveryFeed.read(key,a,now));
        row.put("observedAt",now-1000);a.put("regionScope","mixed");assertNull(CommunityDiscoveryFeed.read(key,a,now));
        a.put("regionScope","net-donut");assertNull(CommunityDiscoveryFeed.read("minecraft:stone|64",a,now));assertNull(CommunityDiscoveryFeed.read(key,answer(),now));
    }
    @Test void variantRequirementsDoNotLeakIntoPlainMarketGuidance(){
        var a=answer();var row=a.putObject("orderLevels").put("usability","history").putArray("rows").addObject().put("unitPrice",100).put("observedAt",now-1000).put("parts","Mending");
        assertNull(CommunityDiscoveryFeed.read(key,a,now));
        String template="minecraft:netherite_upgrade_smithing_template|64";a.put("itemKey",template);row.put("parts","Smithing Template;Applies to:;Diamond Equipment;Ingredients:;Netherite Ingot");assertNotNull(CommunityDiscoveryFeed.read(template,a,now));
        row.put("parts","Smithing Template;Applies to:;Diamond Equipment;Ingredients:;Netherite Ingot;Mending");assertNull(CommunityDiscoveryFeed.read(template,a,now));
        row.putArray("parts").add("Mending");assertNull(CommunityDiscoveryFeed.read(template,a,now));
    }
    @Test void serverRowAgeIsAnchoredToGenerationTimeNotExtendedByAnotherRead(){
        var a=answer().put("generatedAt",now-20000);var row=a.putObject("priceLevels").put("usability","history").putArray("rows").addObject().put("unitPrice",100).put("ageSeconds",890);
        assertNull(CommunityDiscoveryFeed.read(key,a,now));row.put("ageSeconds",10);assertEquals(now-30000,CommunityDiscoveryFeed.read(key,a,now).observedAt());
    }
    @Test void discoveryRankingFallsBackNormallyWithoutSharedSignals(){
        var h=new CommunityDiscoveryFeed.Hint(key,now-1000,1,Set.of("follow_ordered"),1000,900);
        assertTrue(CommunityDiscoveryFeed.compareForDiscovery(h,null)<0);assertEquals(0,CommunityDiscoveryFeed.compareForDiscovery(null,null));
        assertTrue(CommunityDiscoveryFeed.compareForDiscovery(null,h)>0);
    }
}
