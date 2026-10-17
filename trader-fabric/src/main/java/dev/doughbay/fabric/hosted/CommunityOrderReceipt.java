package dev.doughbay.fabric.hosted;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.JsonNode;

/** Local-only acknowledgement barrier. Never serialized into the community protocol. */
final class CommunityOrderReceipt {
    final String scope;
    private int pending, queued, accepted;
    private boolean failed, finished, published;
    private ObjectNode marker;
    CommunityOrderReceipt(String scope){this.scope=scope;}
    static boolean successfulBatch(JsonNode response,int expected){
        return response.path("rejected").isArray()&&response.path("rejected").isEmpty()
                &&response.path("accepted").isIntegralNumber()&&response.path("duplicates").isIntegralNumber()
                &&response.path("accepted").asLong()>=0&&response.path("duplicates").asLong()>=0
                &&!response.path("degraded").asBoolean()
                &&response.path("accepted").asLong()==expected-response.path("duplicates").asLong();
    }
    synchronized void queued(){if(finished||failed)throw new IllegalStateException();pending++;queued++;}
    synchronized void fail(){failed=true;marker=null;}
    synchronized ObjectNode accepted(int count){
        if(count<0||count>pending){fail();return null;}
        pending-=count;accepted+=count;return ready();
    }
    synchronized ObjectNode finish(ObjectNode value){
        if(finished)return null;finished=true;marker=value.deepCopy();return ready();
    }
    private ObjectNode ready(){
        if(failed||!finished||published||pending!=0)return null;
        published=true;var result=marker;marker=null;return result;
    }
    synchronized ObjectNode view(){return DashboardProtocol.JSON.createObjectNode().put("recordsQueued",queued)
            .put("recordsAccepted",accepted).put("recordsPending",pending).put("endMarkerQueued",published&&!failed)
            .put("uploadState",failed?"incomplete":published?"end_marker_queued":finished?"waiting_for_acknowledgements":"streaming");}
}
