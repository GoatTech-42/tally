package dev.doughbay.fabric.hosted;
import java.util.*;
/** Discovery eligibility only. Never prices a trade, attributes ownership or dispatches an action. */
public final class CommunityDiscoveryGate {
    public record Decision(String reason,String sourceReadId,long sourceEndedAt,Set<String> coveredMarkets) {
        public Decision { coveredMarkets=Set.copyOf(coveredMarkets); }
        public boolean reusable(){return reason.equals("covered_fresh_peer_discovery");}
    }
    public static Decision evaluate(CommunityOrderShadow.Snapshot snapshot,Set<String> requiredKeys,
            int requiredPages,long now,long ownReadAt,boolean bankReady,boolean guardsClear) {
        if(!guardsClear)return no("guard_or_exposure_hold");
        if(!bankReady)return no("balance_not_observed");
        if(ownReadAt<=0||ownReadAt>now||now-ownReadAt>60000)return no("own_orders_need_refresh");
        if(snapshot==null)return no("snapshot_pending");
        if(!snapshot.reason().isEmpty())return no(snapshot.reason());
        var m=snapshot.marker();long end=m.path("endedAt").asLong();
        String id=m.path("readId").asText();
        if(!id.matches("o_[A-Za-z0-9_-]{1,96}")||!m.path("sameOwner").asBoolean()||m.path("self").asBoolean())return no("not_eligible_peer");
        if(end<=0||end>now||now-end>CommunityMarketGuidance.HISTORY_TTL)return no("stale_read");
        String status=m.path("status").asText();
        if(!(status.equals("complete")||status.equals("capped"))||m.path("pages").asInt()<requiredPages
                ||snapshot.rows().isEmpty()||m.path("rows").asInt()!=snapshot.rows().size())return no("incomplete_coverage");
        var covered=new HashSet<String>();
        for(var row:snapshot.rows()) {
            String key=row.path("itemKey").asText();long at=row.path("observedAt").asLong();
            if(key.matches("[a-z0-9_.-]+:[a-z0-9_./-]+\\|[1-9][0-9]?")&&discoveryMetadata(key,row.path("parts").asText())
                    &&at>0&&at<=now&&now-at<=CommunityMarketGuidance.HISTORY_TTL)covered.add(key);
        }
        if(requiredKeys.isEmpty())return no("no_requested_markets");
        if(!covered.containsAll(requiredKeys))return new Decision("requested_markets_not_covered",id,end,covered);
        return new Decision("covered_fresh_peer_discovery",id,end,covered);
    }
    /** A current quote is required even for a market that had no current bid. Historical book values cannot override it. */
    public static boolean quoteFresh(long[] quote,long ownOrderSeenAt,long now,long ttl) {
        return quote!=null&&quote.length>=2&&quote[0]>=0&&quote[1]>ownOrderSeenAt
                &&quote[1]>0&&quote[1]<=now&&now-quote[1]<=ttl;
    }
    private static Decision no(String reason){return new Decision(reason,"",0,Set.of());}
    /** Template instructions describe the base item, not an enchantment/variant. Discovery only. */
    static boolean discoveryMetadata(String key,String parts) {
        if(parts.isEmpty())return true;
        Set<String> expected;
        if(key.equals("minecraft:netherite_upgrade_smithing_template|64"))
            expected=Set.of("Applies to:","Diamond Equipment","Ingredients:","Netherite Ingot");
        else if(key.equals("minecraft:silence_armor_trim_smithing_template|64")||key.equals("minecraft:flow_armor_trim_smithing_template|64"))
            expected=Set.of("Applies to:","Armor","Ingredients:","Ingots & Crystals");
        else return false;
        var lines=new ArrayList<>(List.of(parts.split(";",-1)));
        lines.remove("Smithing Template");
        return lines.size()==expected.size()&&new HashSet<>(lines).equals(expected);
    }
    private CommunityDiscoveryGate(){}
}
