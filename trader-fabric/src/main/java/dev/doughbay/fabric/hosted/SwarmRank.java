package dev.doughbay.fabric.hosted;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Own-Hive results only; never stores other members' names, results or ordering. */
public final class SwarmRank {
    private SwarmRank(){}
    private record Result(int percentile,int count,long computedAt,long expiresAt,String reason){}
    private static volatile Result current;
    public static void clear(){current=null;}
    static String accountId(String salt,String account) {
        if(salt==null||!salt.matches("[A-Za-z0-9_-]{32,128}")||account==null||account.isBlank())return "";
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((salt+"\n"+account.strip().toLowerCase(Locale.ROOT)).getBytes(StandardCharsets.UTF_8)));}
        catch(Exception e){return "";}
    }
    static void receive(JsonNode frame,String expected,long now) {
        clear();
        if(expected==null||!expected.matches("[a-f0-9]{64}")||!frame.path("accountId").asText().equals(expected))return;
        var r=frame.path("rank");
        if(r.path("unavailable").asBoolean()) {
            String reason=r.path("reason").asText();
            if(Set.of("insufficient_group","stale_snapshot","no_booked_source","low_coverage","not_contributing","invalid_data","contribution_check_unavailable").contains(reason))current=new Result(0,0,now,now+90_000,reason);
            return;
        }
        if(!r.path("metric").asText().equals("booked_realized_profit")||r.path("windowHours").asInt()!=24)return;
        for(String field:List.of("percentile","hivesCounted","computedAt","yourFigure","yourBookedSaleRecords","freshnessSeconds"))if(!r.path(field).isIntegralNumber()||!r.path(field).canConvertToLong())return;
        long p=r.path("percentile").asLong(),n=r.path("hivesCounted").asLong(),at=r.path("computedAt").asLong();
        long ttl=r.path("freshnessSeconds").asLong();
        if(p<0||p>100||n<10||n>1_000_000||at<=0||at>now+5000||now-at>90_000||ttl<1||ttl>90||at+ttl*1000<=now
                ||Math.abs((double)r.path("yourFigure").asLong())>1_000_000_000_000L||r.path("yourBookedSaleRecords").asLong()<0)return;
        long expiry=at+ttl*1000;
        if(r.has("expiresAt")){
            if(!r.path("expiresAt").isIntegralNumber()||!r.path("expiresAt").canConvertToLong())return;
            expiry=Math.min(expiry,r.path("expiresAt").asLong());
        }
        if(expiry<=now)return;
        current=new Result((int)p,(int)n,at,expiry,"");
    }
    public static List<String[]> rows(long now) {
        var r=current;
        if(r==null||now>=r.expiresAt()||now<r.computedAt()-5000)return List.<String[]>of(new String[]{"Swarm rank","Awaiting qualified data","dim"});
        if(!r.reason().isEmpty())return List.<String[]>of(new String[]{"Swarm rank",switch(r.reason()){
            case "insufficient_group"->"Needs 10 qualified active Hives";
            case "low_coverage"->"Collecting 24h ledger coverage";
            case "not_contributing"->"Active contribution required";
            default->"Qualified fresh data unavailable";
        },"dim"});
        return List.of(new String[]{"Swarm percentile","Above "+r.percentile()+"% of other qualified Hives","green"},
                new String[]{"Swarm basis",r.count()+" active Hives · booked profit, 24h · 90%+ observed"});
    }
}
