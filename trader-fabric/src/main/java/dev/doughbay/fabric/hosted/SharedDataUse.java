package dev.doughbay.fabric.hosted;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;

/** Actual data consumption, not a counterfactual claim that a scan or time was saved. */
public final class SharedDataUse {
    public static final Map<String,List<String>> GROUPS;
    static {
        var groups=new LinkedHashMap<String,List<String>>();
        groups.put("cloudAccepted",List.of("market","marketBatch","ordersLastRead","ordersSnapshot","salesHistory"));
        groups.put("hintsConsumed",List.of("cloudAggregate","followTrade","priceLevel","hiveLocal","ordersSnapshot"));
        groups.put("historyImported",List.of("pages","sales"));
        groups.put("hiveLocalReuse",List.of("reads"));
        groups.put("fallbacks",List.of("noFullRead","tooOld","selfRead","otherOwner","tooFewPages","incompleteCoverage","snapshotRejected","requestFailed","rateLimited","guardNotReady"));
        GROUPS=Collections.unmodifiableMap(groups);
    }
    public static final SharedDataUse INSTANCE = new SharedDataUse();
    private final ObjectNode counters=empty();
    public static ObjectNode empty() {
        var value=DashboardProtocol.JSON.createObjectNode();
        GROUPS.forEach((group,keys)->{var out=value.putObject(group);keys.forEach(k->out.put(k,0));});return value;
    }
    public synchronized void add(String group,String key,long amount) {
        if(amount<0||!GROUPS.containsKey(group)||!GROUPS.get(group).contains(key))throw new IllegalArgumentException("Unknown data-use counter");
        var out=(ObjectNode)counters.path(group);out.put(key,Math.min(1_000_000_000L,out.path(key).asLong()+Math.min(amount,1_000_000_000L)));
    }
    public synchronized ObjectNode view(){return counters.deepCopy();}
    public void hint(CommunityDiscoveryFeed.Hint hint) {
        if(hint==null)return;
        add("hintsConsumed","cloudAggregate",1);
        if(hint.sources().stream().anyMatch(s->s.startsWith("follow_")))add("hintsConsumed","followTrade",1);
        if(hint.sources().stream().anyMatch(s->s.equals("asks")||s.equals("priceLevels")))add("hintsConsumed","priceLevel",1);
    }
    public static String fallback(String reason) {
        return switch(reason==null?"":reason) {
            case "no_full_read" -> "noFullRead";
            case "stale_read" -> "tooOld";
            case "self_read" -> "selfRead";
            case "other_owner","not_eligible_peer" -> "otherOwner";
            case "insufficient_page_coverage" -> "tooFewPages";
            case "incomplete_coverage","requested_markets_not_covered","no_requested_markets" -> "incompleteCoverage";
            case "snapshot_pending","snapshot_timeout","snapshot_unavailable","snapshot_http_failure" -> "requestFailed";
            case "snapshot_rate_limited" -> "rateLimited";
            case "guard_or_exposure_hold","balance_not_observed","own_orders_need_refresh" -> "guardNotReady";
            default -> "snapshotRejected";
        };
    }
    public static ObjectNode validated(JsonNode value) {
        if(!value.isObject()||value.size()!=GROUPS.size())return null;
        var out=empty();
        for(var group:GROUPS.entrySet()) {
            var input=value.path(group.getKey());if(!input.isObject()||input.size()!=group.getValue().size())return null;
            for(String key:group.getValue()) {
                var n=input.path(key);if(!n.isIntegralNumber()||!n.canConvertToLong()||n.asLong()<0||n.asLong()>1_000_000_000L)return null;
                ((ObjectNode)out.path(group.getKey())).put(key,n.asLong());
            }
        }return out;
    }
    public static boolean negotiated(JsonNode welcome) {
        var n=welcome.path("dataUseSchema");return n.isIntegralNumber()&&n.canConvertToLong()&&n.asLong()==1;
    }
    public static ObjectNode snapshot(JsonNode performance,JsonNode counters,long now) {
        var clean=validated(counters);if(clean==null)return null;
        String session=performance.path("session").asText();
        try{if(!UUID.fromString(session).toString().equalsIgnoreCase(session))return null;}catch(Exception e){return null;}
        var start=performance.path("startedAt");var at=performance.path("observedAt");
        if(!start.isIntegralNumber()||!start.canConvertToLong()||!at.isIntegralNumber()||!at.canConvertToLong()||start.asLong()<=0||at.asLong()<start.asLong()
                ||at.asLong()>now+5000||now-at.asLong()>30000)return null;
        long fallbacks=0;for(String key:GROUPS.get("fallbacks"))fallbacks+=clean.path("fallbacks").path(key).asLong();
        if(fallbacks!=performance.path("discoverySavings").path("fallbackReads").asLong())return null;
        var out=DashboardProtocol.JSON.createObjectNode().put("schema",1).put("session",session)
                .put("startedAt",start.asLong()).put("observedAt",at.asLong());out.setAll(clean);return out;
    }
    public static void attach(ObjectNode telemetry,JsonNode performance,JsonNode counters,boolean supported,boolean sharing,long now) {
        telemetry.remove("dataUse");if(!supported||!sharing)return;
        var snapshot=snapshot(performance,counters,now);if(snapshot!=null)telemetry.set("dataUse",snapshot);
    }
    public static ObjectNode hive(JsonNode rows) {
        var known=new HashSet<String>();var valid=new HashMap<String,ObjectNode>();
        for(var row:rows) {
            String id=row.path("id").asText();if(id.isBlank())continue;known.add(id);
            if(row.path("available").asBoolean()) {var clean=validated(row.path("community").path("dataUse"));if(clean!=null)valid.putIfAbsent(id,clean);}
        }
        var sum=empty();for(var row:valid.values())for(var group:GROUPS.entrySet())for(String key:group.getValue()) {
            var target=(ObjectNode)sum.path(group.getKey());target.put(key,target.path(key).asLong()+row.path(group.getKey()).path(key).asLong());
        }
        sum.put("knownAccounts",known.size()).put("reportingAccounts",valid.size()).put("missingAccounts",known.size()-valid.size());return sum;
    }
    public static List<String> lines(JsonNode v) {
        var rows=new ArrayList<String>();
        rows.add("Data use: "+v.path("reportingAccounts").asLong()+" / "+v.path("knownAccounts").asLong()+" accounts reporting");
        if(v.path("reportingAccounts").asLong()==0){rows.add("Data-use measurements unavailable; older clients are unmeasured.");return rows;}
        long cloud=0;for(String key:GROUPS.get("cloudAccepted"))cloud+=v.path("cloudAccepted").path(key).asLong();
        rows.add("Validated cloud responses: "+cloud+" (market batches "+v.path("cloudAccepted").path("marketBatch").asLong()+")");
        rows.add("Cloud hints used in decisions: "+v.path("hintsConsumed").path("cloudAggregate").asLong());
        rows.add("Included sources: follow activity "+v.path("hintsConsumed").path("followTrade").asLong()+"; price levels "+v.path("hintsConsumed").path("priceLevel").asLong()+" (can overlap)");
        rows.add("Shared history imported: "+v.path("historyImported").path("sales").asLong()+" sale rows");
        rows.add("Local stored-book reads: "+v.path("hiveLocalReuse").path("reads").asLong()+"; local-book planning uses: "+v.path("hintsConsumed").path("hiveLocal").asLong());
        var reasons=new ArrayList<String>();for(String key:GROUPS.get("fallbacks"))if(v.path("fallbacks").path(key).asLong()>0)
            reasons.add(key.replaceAll("([a-z])([A-Z])","$1 $2").toLowerCase(Locale.ROOT)+": "+v.path("fallbacks").path(key).asLong());
        if(!reasons.isEmpty())rows.add("Rejected scan-replacement lookups: "+String.join("; ",reasons));
        rows.add("These are data-use counts, not scans avoided or time saved. Local reads may reuse this account's own saved book.");return rows;
    }
}
