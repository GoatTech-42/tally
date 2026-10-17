package dev.doughbay.fabric.hosted;

import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.doughbay.fabric.AutomatedExecutionDriver.OrderRow;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CommunityOrderStreamingTest {
    static class Sink implements CommunityObservations.OrderSink {
        final ArrayDeque<CommunityBatches.Observation> queue=new ArrayDeque<>();
        final CommunityOrderReceipt receipt=new CommunityOrderReceipt("net-donut");
        boolean refuse;
        public CommunityOrderReceipt begin(){return receipt;}
        public boolean send(CommunityOrderReceipt r,ObjectNode row){
            if(refuse)return false;r.queued();queue.add(new CommunityBatches.Observation(row,r.scope,r));return true;
        }
        public void finish(CommunityOrderReceipt r,ObjectNode marker){publish(r.finish(marker));}
        void publish(ObjectNode marker){if(marker!=null)queue.add(new CommunityBatches.Observation(marker,receipt.scope));}
        CommunityBatches.Packed pack(){return CommunityBatches.pack(queue,Set.of("order_level","order","order_sweep_end"),2000);}
        void ack(CommunityBatches.Packed batch){batch.receipts().forEach((r,count)->publish(r.accepted(count)));}
    }
    private OrderRow row(int page,long price){return new OrderRow("minecraft:diamond","minecraft:diamond","",List.of(),price,0,192,page,List.of(),64,"",1000+page);}
    @Test void rowsLeaveDuringReadAndEndWaitsForEveryBatchAcknowledgement(){
        var sink=new Sink();var read=new CommunityObservations.OrderRead(1000,sink);
        for(int page=1;page<=12;page++){
            var rows=new ArrayList<OrderRow>();for(int i=0;i<45;i++)rows.add(row(page,100+i));
            read.page(page,rows,1000+page);
        }
        var first=sink.pack();assertEquals(500,first.body().path("records").size());
        assertTrue(first.body().path("records").findValuesAsText("type").stream().noneMatch("order_sweep_end"::equals));
        read.finish(200,false,true,2000);
        var last=sink.pack();assertEquals(40,last.body().path("records").size());assertTrue(sink.queue.isEmpty());
        // Even an acknowledged later batch cannot release the marker before the earlier one.
        sink.ack(last);assertTrue(sink.queue.isEmpty());
        sink.ack(first);var end=sink.pack().body().path("records");
        assertEquals(1,end.size());assertEquals("order_sweep_end",end.get(0).path("type").asText());
        assertEquals(540,end.get(0).path("levels").asInt());assertTrue(end.get(0).path("capped").asBoolean());
        assertFalse(end.get(0).path("complete").asBoolean());
        assertFalse(first.body().toString().contains("receipt"));
    }
    @Test void acknowledgedPagesDoNotNeedReuploadWhenTheReadEnds(){
        var sink=new Sink();var read=new CommunityObservations.OrderRead(1000,sink);
        read.page(1,List.of(row(1,100),row(1,100)),1001);var first=sink.pack();
        assertEquals(2,first.body().path("records").get(0).path("depth").asInt());sink.ack(first);
        read.page(1,List.of(row(1,100)),1001);assertTrue(sink.queue.isEmpty());
        read.page(2,List.of(row(2,100)),1002);var second=sink.pack();sink.ack(second);
        read.finish(2,true,false,2000);read.finish(2,true,false,2000);
        var end=sink.pack().body().path("records");assertEquals(1,end.size());
        assertEquals(2,end.get(0).path("levels").asInt());assertEquals(0,end.get(0).path("records").asInt());
        assertEquals(1000,end.get(0).path("startedAt").asLong());
    }
    @Test void queuedButUnacknowledgedTimeoutOrRateLimitNeverReleasesEnd(){
        var sink=new Sink();var read=new CommunityObservations.OrderRead(1000,sink);
        read.page(1,List.of(row(1,100)),1001);var pending=sink.pack();
        read.finish(200,false,true,2000);assertTrue(sink.queue.isEmpty());
        assertEquals(1,sink.receipt.view().path("recordsPending").asInt());
        // The existing HTTP retry reuses this exact batch; only success acknowledges it.
        sink.ack(pending);assertEquals(1,sink.queue.size());
    }
    @Test void rejectionQueueOverflowAndAbortPreventFullReadMarkers(){
        for(String reason:List.of("rejection","overflow","abort")){
            var sink=new Sink();var read=new CommunityObservations.OrderRead(1000,sink);
            sink.refuse=reason.equals("overflow");read.page(1,List.of(row(1,100)),1001);
            var batch=sink.pack();
            if(reason.equals("abort"))read.abort();else if(reason.equals("rejection"))sink.receipt.fail();
            read.finish(200,false,true,2000);sink.ack(batch);
            assertTrue(sink.queue.isEmpty(),reason);assertEquals("incomplete",sink.receipt.view().path("uploadState").asText());
        }
    }
    @Test void unsupportedStreamRecordInvalidatesTheBarrier(){
        var sink=new Sink();sink.send(sink.receipt,CommunityWire.event("unsupported",1000));
        var packed=sink.pack();assertEquals(1,packed.dropped());
        sink.finish(sink.receipt,CommunityWire.event("order_sweep_end",2000));
        assertTrue(sink.queue.isEmpty());assertEquals("incomplete",sink.receipt.view().path("uploadState").asText());
    }
    @Test void malformedOrRepeatedAcknowledgementsCannotPublishASecondMarker(){
        var receipt=new CommunityOrderReceipt("net-donut");receipt.queued();
        assertNull(receipt.finish(CommunityWire.event("order_sweep_end",2000)));
        assertNotNull(receipt.accepted(1));assertNull(receipt.accepted(0));
        assertNull(receipt.finish(CommunityWire.event("order_sweep_end",2000)));
        var invalid=new CommunityOrderReceipt("net-donut");invalid.queued();assertNull(invalid.accepted(2));
        assertNull(invalid.finish(CommunityWire.event("order_sweep_end",2000)));
    }
    @Test void replayAndDuplicateAcknowledgementsRequireACompleteUnrejectedResponse(){
        var response=DashboardProtocol.JSON.createObjectNode().put("accepted",400).put("duplicates",100).put("replay",true);
        response.putArray("rejected");assertTrue(CommunityOrderReceipt.successfulBatch(response,500));
        assertFalse(CommunityOrderReceipt.successfulBatch(response,501));
        assertFalse(CommunityOrderReceipt.successfulBatch(response.deepCopy().put("degraded",true),500));
        var rejected=response.deepCopy();rejected.withArray("rejected").addObject().put("index",0).put("code","invalid_row");
        assertFalse(CommunityOrderReceipt.successfulBatch(rejected,500));
        assertFalse(CommunityOrderReceipt.successfulBatch(response.deepCopy().put("accepted",-1).put("duplicates",501),500));
        assertFalse(CommunityOrderReceipt.successfulBatch(DashboardProtocol.JSON.createObjectNode(),500));
    }
}
