package dev.doughbay.fabric.hosted;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class CommunityDiscoveryGateTest {
    long now=2000000;
    CommunityOrderShadow.Snapshot snapshot(){
        var m=DashboardProtocol.JSON.createObjectNode().put("readId","o_peer").put("endedAt",now-90000).put("sameOwner",true).put("self",false).put("status","capped").put("pages",200).put("rows",1);
        var row=DashboardProtocol.JSON.createObjectNode().put("itemKey","minecraft:stone|64").put("observedAt",now-100000).put("unitPrice",999999999);
        return new CommunityOrderShadow.Snapshot(m,List.of(row),"");
    }
    CommunityDiscoveryGate.Decision check(CommunityOrderShadow.Snapshot s,Set<String> keys){return CommunityDiscoveryGate.evaluate(s,keys,200,now,now-1000,true,true);}
    @Test void acceptsCoveredHistoryWithoutReturningAnyExecutablePrice(){
        var result=check(snapshot(),Set.of("minecraft:stone|64"));assertTrue(result.reusable());assertEquals(Set.of("minecraft:stone|64"),result.coveredMarkets());
    }
    @Test void missingMarketIsUnknownRatherThanAnEmptyMarket(){
        assertFalse(check(snapshot(),Set.of("minecraft:diamond|64")).reusable());assertFalse(check(snapshot(),Set.of()).reusable());
    }
    @Test void standardTemplateInstructionsCoverDiscoveryButVariantsAndIncompleteLoreDoNot(){
        for(String item:List.of("netherite_upgrade_smithing_template","silence_armor_trim_smithing_template","flow_armor_trim_smithing_template")) {
            String key="minecraft:"+item+"|64";
            String parts=item.startsWith("netherite_upgrade")?"Applies to:;Diamond Equipment;Ingredients:;Netherite Ingot":"Applies to:;Armor;Ingredients:;Ingots & Crystals";
            var s=snapshot();s.rows().get(0).put("itemKey",key).put("parts",parts);
            assertTrue(check(s,Set.of(key)).reusable());
            s.rows().get(0).put("parts","Smithing Template;"+parts);assertTrue(check(s,Set.of(key)).reusable(),"exact frozen snapshot metadata");
            s.rows().get(0).put("parts",parts+";Unbreaking III");assertFalse(check(s,Set.of(key)).reusable());
            s.rows().get(0).put("parts","Applies to:;Armor");assertFalse(check(s,Set.of(key)).reusable());
        }
        var s=snapshot();s.rows().get(0).put("parts","Applies to:;Armor;Ingredients:;Ingots & Crystals");
        assertFalse(check(s,Set.of("minecraft:stone|64")).reusable());
    }
    @Test void freshnessCoverageAndPeerFailuresFallBack(){
        var s=snapshot();s.marker().put("endedAt",now-900001);assertFalse(check(s,Set.of("minecraft:stone|64")).reusable());
        s=snapshot();s.marker().put("rows",2);assertFalse(check(s,Set.of("minecraft:stone|64")).reusable());
        s=snapshot();s.marker().put("self",true);assertFalse(check(s,Set.of("minecraft:stone|64")).reusable());
        s=snapshot();s.marker().put("pages",100);assertFalse(check(s,Set.of("minecraft:stone|64")).reusable());
        assertFalse(check(CommunityOrderShadow.unavailable("snapshot_rate_limited"),Set.of("minecraft:stone|64")).reusable());
    }
    @Test void guardsBalanceAndOwnOrdersRemainIndependentGates(){
        var keys=Set.of("minecraft:stone|64");
        assertFalse(CommunityDiscoveryGate.evaluate(snapshot(),keys,200,now,now-1000,false,true).reusable());
        assertFalse(CommunityDiscoveryGate.evaluate(snapshot(),keys,200,now,now-1000,true,false).reusable());
        assertFalse(CommunityDiscoveryGate.evaluate(snapshot(),keys,200,now,now-60001,true,true).reusable());
    }
    @Test void liveQuotesMustFollowOwnOrderEvidenceAndNeverComeFromTheFuture(){
        assertTrue(CommunityDiscoveryGate.quoteFresh(new long[]{0,now-1000},now-2000,now,30000));
        assertFalse(CommunityDiscoveryGate.quoteFresh(new long[]{123,now-30001},0,now,30000));
        assertFalse(CommunityDiscoveryGate.quoteFresh(new long[]{123,now+1},0,now,30000));
        assertFalse(CommunityDiscoveryGate.quoteFresh(new long[]{123,now-1000},now-1000,now,30000));
    }
}
