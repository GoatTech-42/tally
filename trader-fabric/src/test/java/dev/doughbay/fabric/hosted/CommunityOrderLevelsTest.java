package dev.doughbay.fabric.hosted;
import dev.doughbay.fabric.AutomatedExecutionDriver.OrderRow;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
class CommunityOrderLevelsTest {
    private OrderRow row(int page,long delivered,String owner,List<String> parts,String key){
        return new OrderRow("minecraft:diamond",key,"",parts,1200,delivered,192,page,List.of(),64,owner,123);
    }
    @Test void depthCountsOnlyIdenticalRowsOnTheSamePage(){
        var rows=CommunityObservations.orderLevels(List.of(row(1,64,"",List.of(),"minecraft:diamond"),row(1,64,"",List.of(),"minecraft:diamond"),row(2,64,"",List.of(),"minecraft:diamond"),row(1,128,"",List.of(),"minecraft:diamond")),123);
        assertEquals(3,rows.size());assertEquals(2,rows.get(0).path("depth").asInt());assertEquals(1,rows.get(1).path("depth").asInt());
        for(var level:rows)for(String forbidden:List.of("owner","sweepId","sweepSeq","scope","descriptor","parts"))assertFalse(level.has(forbidden));
    }
    @Test void namedAndUnidentifiedVariantsCannotBecomeAnonymousPlainMarkets(){
        assertTrue(CommunityObservations.orderLevels(List.of(row(1,0,"Trader1",List.of(),"minecraft:diamond"),row(1,0,"",List.of("Custom lore"),"minecraft:diamond"),row(1,0,"",List.of(),"minecraft:diamond#12345678")),123).isEmpty());
    }
    @Test void largeTotalsRespectTheContractBoundary(){
        assertNotNull(CommunityWire.orderLevel("minecraft:elytra",1,1200,2_100_000_000L,1_500_000_000L,123,1));
        assertNotNull(CommunityWire.orderLevel("minecraft:elytra",1,1200,1_000_000_000_000L,0,123,1));
        assertNull(CommunityWire.orderLevel("minecraft:elytra",1,1200,1_000_000_000_001L,0,123,1));
    }
    @Test void visiblePartsKeepVariantsSeparateWithoutExportingLegacyHashes(){
        var levels=CommunityObservations.orderLevels(List.of(row(1,0,"",List.of("Protection IV","Mending"),"minecraft:diamond#12345678"),row(1,0,"",List.of("Protection III","Mending"),"minecraft:diamond#87654321"),row(1,0,"",List.of(),"minecraft:diamond")),123);
        assertEquals(3,levels.size());assertEquals("minecraft:diamond|64",levels.get(0).path("itemKey").asText());
        assertEquals("Protection IV",levels.get(0).path("parts").get(0).asText());
        assertFalse(levels.get(2).has("parts"));assertFalse(levels.get(0).has("descriptor"));
    }
    @Test void unknownPersonalTextIsNotScrubbedIntoAPlainVariant(){
        assertNull(CommunityOrderParts.safe(List.of("Mending","Owner: Trader1")));
        assertNull(CommunityOrderParts.safe(List.of("Mending","token=secret")));
        assertNull(CommunityOrderParts.safe(java.util.Collections.nCopies(13,"Mending")));
        assertEquals(12,CommunityOrderParts.safe(java.util.Collections.nCopies(12,"Mending")).size());
        assertNotNull(CommunityOrderParts.safe(List.of("Bad Omen (01:40:00)","+3 Attack Damage","Applies to:","Diamond Equipment")));
    }
    @Test void invalidQuantitiesAreHeldBackAndLevelsDoNotBecomeAskEvidence(){
        assertNull(CommunityWire.orderLevel("minecraft:diamond",64,1200,192,193,123,1));
        var answer=DashboardProtocol.JSON.createObjectNode().put("regionScope","asia-2");
        answer.putObject("orderLevels").put("usability","replace_scan").putArray("rows").addObject().put("unitPrice",1200);
        assertNull(CommunityWire.estimate(answer));
    }
}
