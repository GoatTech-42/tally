package dev.doughbay.fabric.hosted;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayDeque;

/** Confirmed listing actions only. Incomplete observation windows are unavailable, never projected. */
public final class DashboardActivityCounters {
    private record Event(long at,boolean reprice){}
    private final ArrayDeque<Event> events=new ArrayDeque<>();
    private long observedFrom,lastSequence;
    public DashboardActivityCounters(long startedAt){observedFrom=startedAt;}
    public synchronized void observe(long sequence,String intent,String outcome,boolean reprice,long at,long now){
        if(sequence<=lastSequence)return;lastSequence=sequence;
        if(!intent.equals("LIST")||!outcome.equals("SUCCEEDED")||at<observedFrom||at>now)return;
        prune(now);events.addLast(new Event(at,reprice));
        if(events.size()>100_000){events.clear();observedFrom=now;}
    }
    public synchronized ObjectNode snapshot(long now){
        prune(now);var root=DashboardProtocol.JSON.createObjectNode();
        for(int hours:new int[]{1,24}){
            long from=now-hours*3_600_000L;if(observedFrom>from)continue;
            long listings=0,reprices=0;
            for(var event:events)if(event.at()>from&&event.at()<=now){if(event.reprice())reprices++;else listings++;}
            String key=hours==1?"h1":"h24";
            root.withObject("listings").put(key,DashboardProtocol.count(listings));
            root.withObject("reprices").put(key,DashboardProtocol.count(reprices));
        }
        return root;
    }
    private void prune(long now){while(!events.isEmpty()&&events.peekFirst().at()<=now-86_400_000L)events.removeFirst();}
}
