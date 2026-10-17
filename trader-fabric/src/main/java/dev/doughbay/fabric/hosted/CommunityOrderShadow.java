package dev.doughbay.fabric.hosted;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Bounded read-only Phase 1. No reference to a trading controller or mutable OrderBook. */
public final class CommunityOrderShadow {
    public interface Fetch { JsonNode get(String path) throws Exception; }
    public record Context(long startedAt,int pageCap,Set<String> managed,boolean ownFresh,int ownOrders,boolean serviceHealthy) {
        public Context { managed=Set.copyOf(managed); }
    }
    public record Snapshot(ObjectNode marker,List<ObjectNode> rows,String reason) {
        public Snapshot { marker=marker.deepCopy();rows=rows.stream().map(ObjectNode::deepCopy).toList(); }
    }
    public static final int MAX_ROWS=25_000,MAX_PAGES=25;
    public static Snapshot fetch(Fetch fetch,Context context,long now) throws Exception {
        return fetch(fetch,context,now,"");
    }
    public static Snapshot fetch(Fetch fetch,Context context,long now,String purpose) throws Exception {
        require(purpose.isEmpty()||purpose.equals("validation"),"invalid_purpose");
        String tag=purpose.isEmpty()?"":"&purpose=validation";
        JsonNode reply=fetch.get("orders/last-read?region=net-donut"+tag);
        scope(reply);JsonNode raw=reply.path("read");
        if(raw.isNull())return unavailable("no_full_read");
        ObjectNode marker=marker(raw,context,now,purpose.equals("validation")?86_400:900);
        String reason=eligibility(marker,context,purpose.equals("validation"));
        if(!reason.isEmpty())return new Snapshot(marker,List.of(),reason);
        int total=marker.path("rows").asInt();var rows=new ArrayList<ObjectNode>();
        var identities=new HashSet<String>();int bytes=0;
        for(int page=1;page<=MAX_PAGES;page++){
            var data=fetch.get("orders/snapshot?region=net-donut&read="+URLEncoder.encode(marker.path("readId").asText(),StandardCharsets.UTF_8)+"&page="+page+tag);
            scope(data);var repeated=marker(data.path("read"),context,now,purpose.equals("validation")?86_400:900);
            for(String field:List.of("readId","startedAt","endedAt","pages","levels","rows","status","capped","self","sameOwner"))
                require(marker.path(field).equals(repeated.path(field)),"snapshot_changed");
            require(number(data,"page")==page&&number(data,"pageSize")==1000&&number(data,"rowsTotal")==total,"pagination_changed");
            require(data.path("rows").isArray()&&data.path("rows").size()<=1000,"invalid_page");
            for(var row:data.path("rows")){
                var clean=row(row,now);require(clean.path("observedAt").asLong()>=marker.path("startedAt").asLong()-5000&&clean.path("observedAt").asLong()<=marker.path("endedAt").asLong()+65_000,"row_outside_read");bytes+=clean.toString().getBytes(StandardCharsets.UTF_8).length;require(bytes<=8*1024*1024,"snapshot_byte_limit");require(identities.add(identity(clean)),"duplicate_snapshot_row");rows.add(clean);
                require(rows.size()<=total&&rows.size()<=MAX_ROWS,"snapshot_too_large");
            }
            require(data.has("next"),"missing_next");
            if(data.path("next").isNull()){
                require(rows.size()==total,"snapshot_count_mismatch");
                return new Snapshot(marker,rows,"");
            }
            require(data.path("rows").size()==1000&&number(data,"next")==page+1,"invalid_next");
        }
        throw new IllegalArgumentException("snapshot_page_limit");
    }
    private static ObjectNode marker(JsonNode n,Context context,long now,long maxAgeSeconds){
        require(n.isObject(),"invalid_marker");
        require(n.path("readId").isTextual()&&n.path("readId").asText().matches("o_[A-Za-z0-9_-]{1,96}"),"invalid_read_id");
        long start=number(n,"startedAt"),end=number(n,"endedAt"),age=number(n,"ageSeconds");
        require(start>0&&start<=end&&end<=context.startedAt()+5000&&end<=now+5000&&age>=0,"invalid_read_time");
        require(age<=maxAgeSeconds&&now-end<=maxAgeSeconds*1000,"stale_read");
        long pages=number(n,"pages"),rows=number(n,"rows"),levels=number(n,"levels");
        require(pages>=100&&pages<=1000&&rows>=1000&&rows<=MAX_ROWS&&levels>=0&&levels<=1_000_000,"invalid_read_bounds");
        for(String field:List.of("sameOwner","self","capped"))require(n.path(field).isBoolean(),"invalid_marker_flags");
        String status=n.path("status").asText();require(status.equals("complete")||status.equals("capped"),"partial_read");
        require(n.path("capped").asBoolean()==status.equals("capped"),"inconsistent_cap");
        var clean=DashboardProtocol.JSON.createObjectNode();
        for(String field:List.of("readId","startedAt","endedAt","ageSeconds","pages","levels","rows","status","capped","self","sameOwner"))clean.set(field,n.path(field));
        return clean;
    }
    private static String eligibility(ObjectNode marker,Context context,boolean validationOnly){
        if(!marker.path("sameOwner").asBoolean())return "other_owner";
        if(marker.path("self").asBoolean())return "self_read";
        if(!validationOnly&&marker.path("capped").asBoolean()&&marker.path("pages").asInt()<context.pageCap())return "insufficient_page_coverage";
        return "";
    }
    private static ObjectNode row(JsonNode raw,long now){
        require(raw.isObject()&&raw.path("itemKey").isTextual(),"invalid_row");
        String item=raw.path("itemKey").asText();String[] key=item.split("\\|",-1);
        require(key.length==2&&key[1].matches("[1-9][0-9]?"),"unsupported_variant_key");
        require(CommunityWire.key(key[0],Integer.parseInt(key[1])).equals(item),"invalid_item_key");
        long price=number(raw,"unitPrice"),total=number(raw,"total"),delivered=number(raw,"delivered"),remaining=number(raw,"remaining"),depth=number(raw,"depth"),at=number(raw,"observedAt");
        require(price>0&&price<=1_000_000_000_000L&&total>0&&total<=1_000_000_000_000L&&delivered>=0&&delivered<=total&&remaining==total-delivered&&depth>=1&&depth<=1_000_000,"invalid_quantities");
        require(at>0&&at<=now+5000,"invalid_observation_time");
        var row=DashboardProtocol.JSON.createObjectNode().put("itemKey",item).put("unitPrice",price).put("total",total).put("delivered",delivered).put("remaining",remaining).put("depth",depth).put("observedAt",at);
        if(raw.has("parts")){
            require(raw.path("parts").isTextual()&&raw.path("parts").asText().length()<=1600,"invalid_parts");
            var parts=CommunityOrderParts.safe(raw.path("parts").asText().isEmpty()?List.of():List.of(raw.path("parts").asText().split(";",-1)));
            require(parts!=null,"unsupported_parts");if(!parts.isEmpty())row.put("parts",String.join(";",parts));
        }
        return row;
    }
    public static ObjectNode compare(Snapshot snapshot,Context context,List<ObjectNode> live,long endedAt,boolean succeeded){
        var result=DashboardProtocol.JSON.createObjectNode().put("startedAt",context.startedAt()).put("endedAt",endedAt)
                .put("phase","shadow").put("wouldSkip",false).put("skipped",false).put("actualSavedMs",0)
                .put("liveReadSucceeded",succeeded).put("tradingDecisionComparison","not_simulated");
        result.set("marker",snapshot.marker().deepCopy());
        if(!succeeded)return result.put("reason","live_read_failed");
        if(!snapshot.reason().isEmpty())return result.put("reason",snapshot.reason());
        var remote=new HashMap<String,ObjectNode>();for(var row:snapshot.rows())remote.put(identity(row),row);
        var local=new HashMap<String,ObjectNode>();
        for(var raw:live){var converted=raw.deepCopy();var parts=converted.remove("parts");if(parts!=null){var text=new ArrayList<String>();parts.forEach(p->text.add(p.asText()));if(!text.isEmpty())converted.put("parts",String.join(";",text));}
            converted.put("remaining",converted.path("total").asLong()-converted.path("delivered").asLong());
            local.put(identity(converted),converted);
        }
        int missing=0,notSeen=0,changed=0;
        for(var entry:local.entrySet()){var old=remote.get(entry.getKey());if(old==null)missing++;
            else if(old.path("delivered").asLong()!=entry.getValue().path("delivered").asLong()||old.path("depth").asLong()!=entry.getValue().path("depth").asLong())changed++;}
        for(String key:remote.keySet())if(!local.containsKey(key))notSeen++;
        result.put("snapshotRows",remote.size()).put("liveRows",local.size()).put("missingFromSnapshot",missing).put("notObservedLive",notSeen).put("changedDeliveryOrDepth",changed);
        var diffs=result.putArray("highestBidDifferences");int absent=0,bidDifferences=0;boolean stale=false;
        for(String market:context.managed()){
            long before=highest(remote.values(),market),after=highest(local.values(),market);if(before==0)absent++;
            if(before!=after){bidDifferences++;if(diffs.size()<50)diffs.addObject().put("itemKey",market).put("snapshotBid",before).put("liveBid",after);}
            for(var value:remote.values())if(value.path("itemKey").asText().equals(market)&&context.startedAt()-value.path("observedAt").asLong()>900_000)stale=true;
        }
        result.put("highestBidDifferencesCount",bidDifferences).put("managedMarkets",context.managed().size()).put("missingManagedMarkets",absent).put("staleManagedRows",stale)
                .put("ownOrdersConfirmed",context.ownFresh()).put("ownOrderIdentityAmbiguous",context.ownOrders()>0);
        String reason=!context.serviceHealthy()?"service_failure_cooldown":!context.ownFresh()?"own_orders_unconfirmed":context.ownOrders()>0?"own_order_identity_unverifiable":context.managed().isEmpty()?"no_managed_markets":absent>0?"missing_managed_market":stale?"stale_managed_rows":"decision_simulation_required";
        return result.put("snapshotDataEligible",context.serviceHealthy()&&context.ownFresh()&&context.ownOrders()==0&&!context.managed().isEmpty()&&absent==0&&!stale)
                .put("reason",reason);
    }
    private static long highest(Collection<ObjectNode> rows,String market){long price=0;for(var row:rows)if(row.path("itemKey").asText().equals(market)&&!row.has("parts")&&row.path("remaining").asLong()>0)price=Math.max(price,row.path("unitPrice").asLong());return price;}
    private static String identity(ObjectNode row){return DashboardProtocol.JSON.createArrayNode().add(row.path("itemKey").asText()).add(row.path("parts").asText()).add(row.path("unitPrice").asLong()).add(row.path("total").asLong()).toString();}
    public static ObjectNode validation(Snapshot snapshot,long at){
        boolean ok=snapshot.reason().isEmpty()&&!snapshot.rows().isEmpty()&&snapshot.rows().size()==snapshot.marker().path("rows").asInt()
                &&snapshot.marker().path("sameOwner").asBoolean()&&!snapshot.marker().path("self").asBoolean();
        var result=DashboardProtocol.JSON.createObjectNode().put("phase","post_read_validation").put("purpose","validation")
                .put("observedAt",at).put("validated",ok).put("validationOnly",true).put("candidateBeforeLiveRead",false)
                .put("skipFreshnessEvaluated",false).put("wouldSkip",false).put("skipped",false).put("actualSavedMs",0).put("rowsReceived",snapshot.rows().size())
                .put("pagesFetched",(snapshot.rows().size()+999)/1000).put("reason",ok?"snapshot_transport_validated":snapshot.reason().isEmpty()?"invalid_snapshot":snapshot.reason());
        result.set("marker",snapshot.marker().deepCopy());return result;
    }
    public static ObjectNode guidanceResult(Snapshot snapshot,Context context,ObjectNode decisions,long ended){
        var result=DashboardProtocol.JSON.createObjectNode().put("startedAt",context.startedAt()).put("endedAt",ended)
                .put("phase","guidance").put("fullReadBaselinePerformed",false).put("wouldSkip",false)
                .put("skipped",false).put("actualSavedMs",0).put("tradingDecisionComparison","not_evaluated")
                .put("reason",snapshot.reason().isEmpty()?decisions.path("backgroundGuidance").path("reason").asText("guidance_incomplete"):snapshot.reason());
        result.set("marker",snapshot.marker().deepCopy());result.set("decisionComparison",decisions.deepCopy());return result;
    }
    public static Snapshot unavailable(String reason){return new Snapshot(DashboardProtocol.JSON.createObjectNode(),List.of(),reason);}
    private static long number(JsonNode n,String field){require(n.path(field).isIntegralNumber()&&n.path(field).canConvertToLong(),"invalid_"+field);return n.path(field).asLong();}
    private static void scope(JsonNode n){require(n.path("regionScope").asText().equals("net-donut"),"wrong_scope");}
    private static void require(boolean value,String reason){if(!value)throw new IllegalArgumentException(reason);}
    private CommunityOrderShadow(){}
}
