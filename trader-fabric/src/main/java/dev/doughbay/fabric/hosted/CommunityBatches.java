package dev.doughbay.fabric.hosted;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Observation scope is captured when observed, never inferred again when retried. */
final class CommunityBatches {
    static final String NETWORK = "net-donut";
    record Observation(ObjectNode row,String scope,CommunityOrderReceipt receipt) {
        Observation(ObjectNode row,String scope){this(row,scope,null);}
        Observation {
            if(!scope.isEmpty()&&!scope.equals(NETWORK))throw new IllegalArgumentException();
            row=row.deepCopy();row.remove("region");
        }
    }
    record Packed(ObjectNode body,long dropped,Map<CommunityOrderReceipt,Integer> receipts){}
    static boolean networkAdvertised(JsonNode capabilities){
        if(!capabilities.path("regionField").asText().equals("region")
                ||!capabilities.path("regionQueryParameter").asText().equals("region"))return false;
        for(var scope:capabilities.path("networkScopes"))if(scope.isTextual()&&scope.asText().equals(NETWORK))return true;
        return false;
    }
    static Packed pack(ArrayDeque<Observation> queue,Set<String> types,long now){
        var body=DashboardProtocol.JSON.createObjectNode().put("schema",1).put("batchCreatedAt",now).put("sentAt",now);
        var records=body.putArray("records");String scope=null;long dropped=0;var receipts=new HashMap<CommunityOrderReceipt,Integer>();
        while(!queue.isEmpty()&&records.size()<500){
            var next=queue.peekFirst();
            if(!types.contains(next.row().path("type").asText())){queue.removeFirst();dropped++;if(next.receipt()!=null)next.receipt().fail();continue;}
            if(scope!=null&&!scope.equals(next.scope()))break;
            if(scope==null){scope=next.scope();if(!scope.isBlank())body.put("region",scope);}
            if(body.toString().getBytes(StandardCharsets.UTF_8).length+next.row().toString().getBytes(StandardCharsets.UTF_8).length>500_000)break;
            records.add(queue.removeFirst().row());
            if(next.receipt()!=null)receipts.merge(next.receipt(),1,Integer::sum);
        }
        return new Packed(body,dropped,Map.copyOf(receipts));
    }
    private CommunityBatches(){}
}
