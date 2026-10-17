package dev.doughbay.fabric.hosted;

import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.doughbay.fabric.AutomatedExecutionDriver;
import java.util.*;

/** Collects only structured fields already observed in-game. No extra game requests. */
public final class CommunityObservations {
    private static volatile ObjectNode orderCoverage=DashboardProtocol.JSON.createObjectNode();
    private static volatile CommunityOrderReceipt coverageReceipt;
    public static ObjectNode orderCoverage(){var result=orderCoverage.deepCopy();var receipt=coverageReceipt;if(receipt!=null)result.set("streamUpload",receipt.view());return result;}
    public static void asks(List<ObjectNode> rows,long started,long ended){
        var byMarket=new LinkedHashMap<String,LinkedHashMap<String,ObjectNode>>();
        for(var row:rows){if(row==null)continue;String key=row.path("itemKey").asText();
            byMarket.computeIfAbsent(key,k->new LinkedHashMap<>()).put(row.path("seller").asText().toLowerCase(Locale.ROOT)+"|"+row.path("stack").asInt()+"|"+row.path("unitPrice").asLong(),row);}
        for(var market:byMarket.entrySet()){
            String scan="a_"+UUID.randomUUID().toString().replace("-","").substring(0,24);
            for(var row:market.getValue().values())CommunityClient.INSTANCE.observe(row.put("scanId",scan));
            var end=CommunityWire.event("ask_scan_end",ended).put("scanId",scan).put("itemKey",market.getKey()).put("rows",market.getValue().size()).put("complete",false).put("startedAt",started).put("endedAt",ended);
            CommunityClient.INSTANCE.observe(end);
        }
    }
    /** Depth counts identical anonymous rows on one observed page, never across sequential pages. */
    static List<ObjectNode> orderLevels(List<AutomatedExecutionDriver.OrderRow> rows,long ended){
        var levels=new LinkedHashMap<String,ObjectNode>();
        for(var row:rows){
            if(CommunityWire.account(row.owner()))continue;
            var parts=CommunityOrderParts.safe(row.parts());
            if(parts==null||(row.itemKey().contains("#")&&parts.isEmpty()))continue;
            // Anonymous level identity explicitly includes parts; do not export the legacy Java hash.
            var level=CommunityWire.orderLevel(row.itemId(),row.stack(),row.unitPrice(),row.total(),row.delivered(),row.observedAt()>0?row.observedAt():ended,row.page());
            if(level==null)continue;
            if(!parts.isEmpty()){var field=level.putArray("parts");parts.forEach(field::add);}
            String identity=level.path("itemKey").asText()+"|"+parts.toString()+"|"+row.unitPrice()+"|"+row.total()+"|"+row.delivered()+"|"+row.page();
            var previous=levels.get(identity);
            if(previous==null)levels.put(identity,level);
            else{previous.put("depth",previous.path("depth").asInt()+1);previous.put("observedAt",Math.max(previous.path("observedAt").asLong(),level.path("observedAt").asLong()));}
        }return List.copyOf(levels.values());
    }
    interface OrderSink {
        CommunityOrderReceipt begin();
        boolean send(CommunityOrderReceipt receipt,ObjectNode row);
        void finish(CommunityOrderReceipt receipt,ObjectNode marker);
    }
    public static OrderRead beginOrderRead(long started){
        return new OrderRead(started,new OrderSink(){
            public CommunityOrderReceipt begin(){return CommunityClient.INSTANCE.beginOrderReceipt();}
            public boolean send(CommunityOrderReceipt receipt,ObjectNode row){return CommunityClient.INSTANCE.streamOrder(receipt,row);}
            public void finish(CommunityOrderReceipt receipt,ObjectNode marker){CommunityClient.INSTANCE.finishOrderReceipt(receipt,marker);}
        });
    }
    /** One page is queued once, immediately after parsing; no network work runs on the game thread. */
    public static final class OrderRead {
        private final OrderSink sink;
        private final CommunityOrderReceipt receipt;
        private final String sweep="o_"+UUID.randomUUID().toString().replace("-","").substring(0,24);
        private final long started;
        private int lastPage,rowsRead,seq,missingOwners,invalidRows,levels,anonymousRows;
        private boolean closed;
        OrderRead(long started,OrderSink sink){this.started=started;this.sink=sink;receipt=sink.begin();}
        public void page(int page,List<AutomatedExecutionDriver.OrderRow> rows,long at){
            if(closed||page<=lastPage)return;
            lastPage=page;rowsRead+=rows.size();
            for(var row:rows){
                var record=CommunityWire.order(row.owner(),row.itemKey(),row.stack(),row.unitPrice(),row.total(),row.delivered(),row.observedAt()>0?row.observedAt():at,sweep,seq);
                if(record==null){if(!CommunityWire.account(row.owner()))missingOwners++;else invalidRows++;continue;}
                if(seq==0)record.put("sweepStartedAt",started);
                if(!sink.send(receipt,record))receipt.fail();seq++;
            }
            var pageLevels=orderLevels(rows,at);
            for(var level:pageLevels){if(!sink.send(receipt,level))receipt.fail();levels++;anonymousRows+=level.path("depth").asInt();}
            coverage(at,lastPage,false,false);
        }
        public void finish(int pages,boolean complete,boolean capped,long ended){
            if(closed)return;closed=true;coverage(ended,pages,complete,capped);
            sink.finish(receipt,CommunityWire.event("order_sweep_end",ended).put("sweepId",sweep).put("scope","full")
                    .put("pages",Math.max(1,pages)).put("records",seq).put("complete",complete&&!capped).put("capped",capped)
                    .put("levels",levels).put("startedAt",started).put("endedAt",ended));
        }
        public void abort(){if(!closed){closed=true;receipt.fail();}}
        private void coverage(long at,int pages,boolean complete,boolean capped){
            orderCoverage=DashboardProtocol.JSON.createObjectNode().put("at",at).put("streaming",true).put("rowsRead",rowsRead)
                    .put("rowsWithOwner",rowsRead-missingOwners).put("rowsQueued",seq).put("orderLevelsQueued",levels)
                    .put("anonymousRowsQueued",anonymousRows).put("rowsMissingOwner",missingOwners).put("invalidRows",invalidRows)
                    .put("pages",pages).put("completeRead",complete).put("capped",capped).put("levels",levels)
                    .put("completeUpload",complete&&!capped).put("namedOrderCoverageComplete",complete&&missingOwners==0&&invalidRows==0);
            coverageReceipt=receipt;
        }
    }
    public static void orders(List<AutomatedExecutionDriver.OrderRow> rows,int pages,boolean complete,long ended){
        orders(rows,pages,complete,false,ended);
    }
    public static void orders(List<AutomatedExecutionDriver.OrderRow> rows,int pages,boolean complete,boolean capped,long ended){
        coverageReceipt=null;
        String sweep="o_"+UUID.randomUUID().toString().replace("-","").substring(0,24);int seq=0,missingOwners=0,invalidRows=0;boolean allKnown=true;
        long started=rows.stream().mapToLong(AutomatedExecutionDriver.OrderRow::observedAt).filter(t->t>0).min().orElse(ended);
        for(var row:rows){var record=CommunityWire.order(row.owner(),row.itemKey(),row.stack(),row.unitPrice(),row.total(),row.delivered(),row.observedAt()>0?row.observedAt():ended,sweep,seq);
            if(record==null){allKnown=false;if(!CommunityWire.account(row.owner()))missingOwners++;else invalidRows++;continue;}
            if(seq==0)record.put("sweepStartedAt",started);CommunityClient.INSTANCE.observe(record);seq++;
        }
        var levels=orderLevels(rows,ended);int anonymousRows=0;
        for(var level:levels){CommunityClient.INSTANCE.observe(level);anonymousRows+=level.path("depth").asInt();}
        orderCoverage=DashboardProtocol.JSON.createObjectNode().put("at",ended).put("rowsRead",rows.size()).put("rowsWithOwner",rows.size()-missingOwners).put("rowsQueued",seq).put("orderLevelsQueued",levels.size()).put("anonymousRowsQueued",anonymousRows).put("rowsMissingOwner",missingOwners).put("invalidRows",invalidRows).put("pages",pages).put("completeRead",complete).put("capped",capped).put("levels",levels.size()).put("completeUpload",complete&&!capped).put("namedOrderCoverageComplete",complete&&allKnown);
        CommunityClient.INSTANCE.observe(CommunityWire.event("order_sweep_end",ended).put("sweepId",sweep).put("scope","full").put("pages",Math.max(1,pages)).put("records",seq)
                .put("complete",complete&&!capped).put("capped",capped).put("levels",levels.size()).put("startedAt",started).put("endedAt",ended));
    }
    private CommunityObservations(){}
}
