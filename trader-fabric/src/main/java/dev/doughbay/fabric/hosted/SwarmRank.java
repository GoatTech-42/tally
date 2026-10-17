package dev.doughbay.fabric.hosted;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Own-Hive results only; never stores other members' names, results or ordering. */
public final class SwarmRank {
    private SwarmRank(){}
    private record Result(int percentile,int count,int position,long computedAt,long expiresAt,String reason){}
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
            if(Set.of("insufficient_group","stale_snapshot","no_booked_source","low_coverage","not_contributing","invalid_data","contribution_check_unavailable").contains(reason))current=new Result(0,0,0,now,now+90_000,reason);
            return;
        }
        if(!r.path("metric").asText().equals("booked_realized_profit")||r.path("windowHours").asInt()!=24)return;
        for(String field:List.of("percentile","hivesCounted","computedAt","yourFigure","yourBookedSaleRecords","freshnessSeconds"))if(!r.path(field).isIntegralNumber()||!r.path(field).canConvertToLong())return;
        long p=r.path("percentile").asLong(),n=r.path("hivesCounted").asLong(),at=r.path("computedAt").asLong();
        long ttl=r.path("freshnessSeconds").asLong();
        if(p<0||p>100||n<2||n>1_000_000||at<=0||at>now+5000||now-at>90_000||ttl<1||ttl>90||at+ttl*1000<=now
                ||Math.abs((double)r.path("yourFigure").asLong())>1_000_000_000_000L||r.path("yourBookedSaleRecords").asLong()<0)return;
        long expiry=at+ttl*1000;
        if(r.has("expiresAt")){
            if(!r.path("expiresAt").isIntegralNumber()||!r.path("expiresAt").canConvertToLong())return;
            expiry=Math.min(expiry,r.path("expiresAt").asLong());
        }
        if(expiry<=now)return;
        int position=0;
        if(r.has("position")){
            if(!r.path("position").isIntegralNumber()||!r.path("position").canConvertToInt())return;
            position=r.path("position").asInt();if(position<1||position>n)return;
        }
        current=new Result((int)p,(int)n,position,at,expiry,"");
    }
    public static List<String[]> rows(long now) {
        var r=current;
        if(r==null||now>=r.expiresAt()||now<r.computedAt()-5000)return List.<String[]>of(new String[]{"Swarm rank","Waiting for current Swarm data","dim"});
        if(!r.reason().isEmpty())return List.<String[]>of(new String[]{"Swarm rank",switch(r.reason()){
            case "insufficient_group"->"Waiting for another Hive";
            case "low_coverage"->"Waiting for reported profit";
            case "not_contributing"->"Active contribution required";
            default->"Current reported profit unavailable";
        },"dim"});
        return List.of(new String[]{"Estimated Swarm rank",r.position()>0?"#"+r.position()+" of "+r.count()+" active Hives":"Above "+r.percentile()+"% of other active Hives","green"},
                new String[]{"Swarm basis","Reported profit, last 24h - approximate; available data"});
    }
}
