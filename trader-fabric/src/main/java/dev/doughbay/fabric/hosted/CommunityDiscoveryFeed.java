package dev.doughbay.fabric.hosted;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;

/** Recent aggregate observations guide checks only. No ownership, liquidity or executable-price claim. */
public final class CommunityDiscoveryFeed {
    public record Hint(String market,long observedAt,int signals,Set<String> sources,
                       double historicalBid,double historicalAsk) {
        public Hint { sources=Set.copyOf(sources); }
        public double discoverySpread(){return historicalBid>0&&historicalAsk>0?(historicalBid-historicalAsk)/historicalAsk:0;}
    }
    public static int compareForDiscovery(Hint a,Hint b) {
        if(a==null||b==null)return a==b?0:a==null?1:-1;
        int edge=Double.compare(b.discoverySpread(),a.discoverySpread());
        return edge!=0?edge:Long.compare(b.observedAt(),a.observedAt());
    }
    private static long observed(JsonNode row,JsonNode answer,long now) {
        long at=0;
        for(String name:List.of("observedAt","lastSeenAt","at"))if(row.path(name).isIntegralNumber())at=Math.max(at,row.path(name).asLong());
        if(at==0&&row.path("ageSeconds").isIntegralNumber()&&row.path("ageSeconds").asLong()>=0) {
            long generated=answer.path("generatedAt").asLong();long age=row.path("ageSeconds").asLong();
            if(generated>0&&generated<=now+5000&&age<=900)at=generated-age*1000;
        }
        return at>0&&at<=now+5000&&now-at<=CommunityMarketGuidance.HISTORY_TTL?Math.min(now,at):0;
    }
    private static boolean own(JsonNode row) {
        return row.path("own").asBoolean()||row.has("sellerOwnIndex")||row.has("ownerOwnIndex")||row.has("actorOwnIndex")||row.has("accountOwnIndex");
    }
    public static Hint read(String market,JsonNode answer,long now) {
        if(!market.matches("[a-z0-9_.-]+:[a-z0-9_./-]+\\|[1-9][0-9]?")||Integer.parseInt(market.substring(market.indexOf('|')+1))>64
                ||!answer.path("regionScope").asText().equals("net-donut")
                ||!answer.path("itemKey").asText().equals(market))return null;
        long newest=0;int count=0;double bid=0,ask=Double.POSITIVE_INFINITY;var sources=new HashSet<String>();
        for(String name:List.of("asks","priceLevels","orders","orderLevels")) {
            var block=answer.path(name);if(!Set.of("history","replace_scan").contains(block.path("usability").asText()))continue;
            for(var row:block.path("rows")) {
                long at=observed(row,answer,now);double price=row.path("unitPrice").asDouble();
                if(at==0||own(row)||!Double.isFinite(price)||price<=0||price>1e12||(row.has("parts")&&!row.path("parts").isTextual())
                        ||!CommunityDiscoveryGate.discoveryMetadata(market,row.path("parts").asText()))continue;
                if((name.equals("orders")||name.equals("orderLevels"))&&row.has("remaining")&&row.path("remaining").asLong()<=0)continue;
                newest=Math.max(newest,at);count++;sources.add(name);
                if(name.equals("orders")||name.equals("orderLevels"))bid=Math.max(bid,price);else ask=Math.min(ask,price);
            }
        }
        var follows=answer.path("followActivity");
        if(follows.path("usability").asText().equals("history"))for(var row:follows.path("recent")) {
            long at=observed(row,answer,now);String kind=row.path("kind").asText();
            long quantity=row.path("quantity").asLong(),total=row.path("total").asLong();
            if(at==0||own(row)||!Set.of("listed","bought","ordered").contains(kind)||quantity<=0||total<=0||total>1e12)continue;
            newest=Math.max(newest,at);count++;sources.add("follow_"+kind);
            double price=total/(double)quantity;
            if(kind.equals("ordered"))bid=Math.max(bid,price);else ask=Math.min(ask,price);
        }
        return count==0?null:new Hint(market,newest,count,sources,bid,Double.isFinite(ask)?ask:0);
    }
    private CommunityDiscoveryFeed(){}
}
