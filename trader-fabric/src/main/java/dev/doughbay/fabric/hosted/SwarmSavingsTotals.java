package dev.doughbay.fabric.hosted;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;

/** Counts client measurements once; estimates remain estimates and ratios are never averaged. */
public final class SwarmSavingsTotals {
    public static final List<String> COUNTERS=List.of("attempts","actualSkippedReads","fallbackReads","lookupWaitMs",
            "calibratedSkippedReads","uncalibratedSkippedReads","estimatedAvoidedReadMs");
    public static ObjectNode validated(JsonNode source) {
        if(source==null||!source.isObject())return null;
        var out=DashboardProtocol.JSON.createObjectNode();
        for(String key:COUNTERS){var n=source.path(key);
            if(!n.isIntegralNumber()||!n.canConvertToLong()||n.asLong()<0||n.asLong()>1_000_000_000_000L)return null;
            out.put(key,n.asLong());
        }
        if(out.path("actualSkippedReads").asLong()+out.path("fallbackReads").asLong()!=out.path("attempts").asLong()
                ||out.path("calibratedSkippedReads").asLong()+out.path("uncalibratedSkippedReads").asLong()!=out.path("actualSkippedReads").asLong()
                ||out.path("calibratedSkippedReads").asLong()==0&&out.path("estimatedAvoidedReadMs").asLong()!=0)return null;
        return derived(out);
    }
    private static ObjectNode derived(ObjectNode out) {
        long skips=out.path("actualSkippedReads").asLong(),calibrated=out.path("calibratedSkippedReads").asLong();
        out.put("estimatedNetSavedMs",out.path("estimatedAvoidedReadMs").asLong()-out.path("lookupWaitMs").asLong())
                .put("estimateAvailable",calibrated>0).put("estimateComplete",skips>0&&calibrated==skips);
        if(out.path("attempts").asLong()>0)out.put("skipPercent",100.0*skips/out.path("attempts").asLong());
        return out;
    }
    public static ObjectNode hive(JsonNode accounts) {
        var out=DashboardProtocol.JSON.createObjectNode();COUNTERS.forEach(k->out.put(k,0));
        var seen=new HashSet<String>();var valid=new LinkedHashMap<String,ObjectNode>();int reporting=0;
        for(var row:accounts){String id=row.path("id").asText();if(id.isBlank())continue;seen.add(id);
            if(!row.path("available").asBoolean())continue;
            var value=validated(row.path("community").path("readSavings"));if(value!=null)valid.putIfAbsent(id,value);
        }
        for(var value:valid.values()){
            reporting++;for(String key:COUNTERS)out.put(key,out.path(key).asLong()+value.path(key).asLong());
        }
        return derived(out).put("knownAccounts",seen.size()).put("reportingAccounts",reporting)
                .put("missingAccounts",seen.size()-reporting).put("hasMeasurements",reporting>0)
                .put("scope","sum of fresh client sessions in this Hive; bot-time, not wall-clock");
    }
    public static List<String> lines(JsonNode total) {
        var lines=new ArrayList<String>();
        lines.add("Your Hive total: "+total.path("reportingAccounts").asInt()+" / "+total.path("knownAccounts").asInt()+" accounts reporting");
        if(!total.path("hasMeasurements").asBoolean()){
            lines.add("Measurements unavailable; offline, stale and older clients are unmeasured, not zero.");return List.copyOf(lines);
        }
        lines.add("Scans skipped: "+total.path("actualSkippedReads").asLong()+"; shared lookups: "+total.path("attempts").asLong()+"; fallbacks: "+total.path("fallbackReads").asLong());
        lines.add(String.format(Locale.ROOT,"Measured shared lookup wait: %.1fs",total.path("lookupWaitMs").asLong()/1000.0));
        lines.add(total.path("estimateAvailable").asBoolean()?String.format(Locale.ROOT,"Estimated net bot-time saved: %.1fs%s",total.path("estimatedNetSavedMs").asLong()/1000.0,total.path("estimateComplete").asBoolean()?"":" (partial baseline)"):"Estimated time saved: not calibrated yet.");
        lines.add("Calibrated skips: "+total.path("calibratedSkippedReads").asLong()+"; without baseline: "+total.path("uncalibratedSkippedReads").asLong());
        lines.add("Sum of fresh client sessions; offline/older clients excluded. Bot-time is not elapsed wall-clock or proven trading speed/profit.");
        return List.copyOf(lines);
    }
    private SwarmSavingsTotals(){}
}
