package dev.doughbay.fabric;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PanelCommandQueueTest {
    @Test void rejectsFormattingCodesButPreservesAllowedUnicode() {
        assertThrows(IllegalArgumentException.class, () -> PanelCommandQueue.normalize("/msg ExamplePlayer \u00a7ahello"));
        assertEquals("msg ExamplePlayer caf\u00e9 \u91d1", PanelCommandQueue.normalize("/msg ExamplePlayer caf\u00e9 \u91d1"));
    }
    static class Port implements PanelCommandQueue.Port {
        String blocker="Native action still in progress";Object connection;final List<String> sent=new ArrayList<>();final Map<String,String> state=new HashMap<>();boolean fail;
        public Object connection(){return connection;}
        public String blocker(){return blocker;}
        public void queue(String id,String command,long expires){sent.add(command);state.put(id,"QUEUED");if(fail)throw new IllegalStateException();}
        public String status(String id){return state.getOrDefault(id,"UNKNOWN");}
        public boolean cancel(String id){return state.replace(id,"QUEUED","CANCELLED");}
    }
    @Test void waitsForSafeBoundaryAndPreservesFifoWithoutReplayingPayment(){
        var q=new PanelCommandQueue();var p=new Port();String first=UUID.randomUUID().toString(),second=UUID.randomUUID().toString();
        q.add(first,"/pay ExamplePlayer 123",1000);q.add(second,"/rtp",1000);q.tick(p,1100);assertTrue(p.sent.isEmpty());
        p.blocker=null;q.tick(p,1200);q.add(first,"/pay ExamplePlayer 123",1300);q.tick(p,1400);
        assertEquals(List.of("pay ExamplePlayer 123"),p.sent);
        p.state.put(first,"SENT");q.tick(p,1500);q.tick(p,1600);
        assertEquals(List.of("pay ExamplePlayer 123","rtp"),p.sent);
        assertEquals("SENT",q.views().getFirst().status());assertEquals("/pay",q.views().getFirst().command());
    }
    @Test void uncertainHandoffExpiresCancellationAndCapacityNeverSendTwice(){
        var q=new PanelCommandQueue();var p=new Port();p.blocker=null;p.fail=true;String id=UUID.randomUUID().toString();
        q.add(id,"/pay ExamplePlayer 123",1000);q.tick(p,1100);q.tick(p,1200);assertEquals(1,p.sent.size());assertEquals("UNCERTAIN",q.views().getFirst().status());
        assertThrows(IllegalArgumentException.class,()->q.add(id,"/pay DifferentPlayer 123",1300));
        var waiting=new PanelCommandQueue();p.blocker="Recovery hold";String other=UUID.randomUUID().toString();waiting.add(other,"/rtp",1000);waiting.tick(p,121000);assertEquals("EXPIRED",waiting.views().getFirst().status());
        for(int n=0;n<PanelCommandQueue.CAPACITY;n++)waiting.add(UUID.randomUUID().toString(),"/bal",122000);
        assertThrows(IllegalArgumentException.class,()->waiting.add(UUID.randomUUID().toString(),"/bal",122000));
        assertTrue(waiting.cancel(waiting.views().get(1).id(),p));
    }
    @Test void rejectsCommandInjectionAndManualTradeBypasses(){
        assertThrows(IllegalArgumentException.class,()->PanelCommandQueue.normalize("/pay ExamplePlayer 5\nrtp"));
        assertThrows(IllegalArgumentException.class,()->PanelCommandQueue.normalize("/ah sell 1"));
        assertThrows(IllegalArgumentException.class,()->PanelCommandQueue.normalize("/minecraft:pay ExamplePlayer 5"));
        assertThrows(IllegalArgumentException.class,()->PanelCommandQueue.normalize("/pay ExamplePlayer"));
        assertEquals("rtp west",PanelCommandQueue.normalize(" /RTP west "));
    }
    @Test void aConnectionChangeWhileWaitingForTheCheckpointCannotRetargetAnOldPayment(){
        var q=new PanelCommandQueue();var p=new Port();Object old=new Object();p.connection=old;
        q.add(UUID.randomUUID().toString(),"/pay ExamplePlayer 123",1000,old);q.tick(p,1100);assertTrue(p.sent.isEmpty());
        p.connection=new Object();p.blocker=null;q.tick(p,1200);assertTrue(p.sent.isEmpty());assertEquals("CANCELLED",q.views().getFirst().status());
    }
}
