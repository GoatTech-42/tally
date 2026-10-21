package dev.doughbay.fabric.hosted;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.UUID;

/** Optional negotiated counter snapshots. No names, paths, prices or per-account ratios. */
public final class SwarmSavingsWire {
    public static boolean negotiated(JsonNode welcome){
        return welcome.path("readSavingsSchema").isIntegralNumber()&&welcome.path("readSavingsSchema").canConvertToLong()&&welcome.path("readSavingsSchema").asLong()==1;
    }
    public static ObjectNode snapshot(JsonNode performance,long now){
        if(performance==null)return null;
        var counters=SwarmSavingsTotals.validated(performance.path("discoverySavings"));
        if(counters==null)return null;
        for(String key:java.util.List.of("attempts","actualSkippedReads","fallbackReads","calibratedSkippedReads","uncalibratedSkippedReads"))
            if(counters.path(key).asLong()>1_000_000_000L)return null;
        String session=performance.path("session").asText();
        try{if(!UUID.fromString(session).toString().equals(session))return null;}catch(IllegalArgumentException e){return null;}
        var start=performance.path("startedAt");var at=performance.path("observedAt");
        if(!start.isIntegralNumber()||!start.canConvertToLong()||!at.isIntegralNumber()||!at.canConvertToLong())return null;
        long began=start.asLong(),observed=at.asLong();
        if(began<=0||began>observed||observed>now+5000||now-observed>30_000)return null;
        var out=DashboardProtocol.JSON.createObjectNode().put("schema",1).put("session",session)
                .put("startedAt",began).put("observedAt",observed);
        for(String key:SwarmSavingsTotals.COUNTERS)out.set(key,counters.get(key));
        out.set("estimatedNetSavedMs",counters.get("estimatedNetSavedMs"));
        return out;
    }
    public static void attach(ObjectNode telemetry,JsonNode performance,boolean supported,boolean sharing,long now){
        telemetry.remove("readSavings");if(!supported||!sharing)return;
        var value=snapshot(performance,now);if(value!=null)telemetry.set("readSavings",value);
    }
    public static ObjectNode comparison(ObjectNode telemetry){
        var shape=telemetry.deepCopy();shape.remove("at");
        if(shape.path("readSavings") instanceof ObjectNode savings)savings.remove("observedAt");
        if(shape.path("dataUse") instanceof ObjectNode use)use.remove("observedAt");
        return shape;
    }
    private SwarmSavingsWire(){}
}
