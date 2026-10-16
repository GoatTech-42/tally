package dev.doughbay.fabric.staffwatch;

import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

class ManualRegionFlowTest {
    final AtomicLong now=new AtomicLong();
    final Fake client=new Fake();
    final ManualRegionFlow flow=new ManualRegionFlow(client,now::get);
    @Test void acceptsOnlySixVerifiedDestinations() {
        for(String bad:new String[]{"","asia\nstop","Asia","end","nether","east;stop","rtp asia"})assertFalse(flow.request(bad));
        assertEquals(6,ManualRegionFlow.DESTINATIONS.size());assertTrue(flow.request("asia"));
    }
    @Test void disconnectedOrBlockedClientCannotQueue() {client.available=false;assertFalse(flow.request("east"));assertFalse(flow.active());}
    @Test void waitsForSafeOperationAndDurableCheckpoint() {
        client.safe=false;flow.request("asia");flow.tick();assertEquals(0,client.sends);
        client.safe=true;client.durable=false;flow.tick();assertEquals(0,client.sends);
        client.durable=true;flow.tick();assertEquals(1,client.sends);assertEquals("asia",client.destination);
        flow.tick();assertEquals(1,client.sends);assertEquals(0,client.resumes);
    }
    @Test void confirmedArrivalReleasesHoldBeforeOwnedResume() {
        flow.request("eu central");flow.tick();client.arrived=true;flow.tick();
        assertFalse(flow.active());assertEquals(1,client.resumes);assertTrue(client.releasedBeforeResume);
    }
    @Test void ignoresArrivalUntilCommandCanBeSent() {
        client.durable=false;client.arrived=true;flow.request("west");flow.tick();assertEquals(0,client.resumes);
    }
    @Test void repeatedClicksNeverQueueTwoTransfers() {
        assertTrue(flow.request("asia"));assertFalse(flow.request("east"));flow.tick();assertEquals("asia",client.destination);assertEquals(1,client.sends);
    }
    @Test void timeoutWithdrawsWithoutResumingOrRetrying() {
        flow.request("asia");flow.tick();now.set(30_000);flow.tick();flow.tick();
        assertFalse(flow.active());assertEquals(1,client.releases);assertEquals(0,client.resumes);assertEquals(1,client.sends);
    }
    @Test void aLateSafeCheckpointStillGetsAFullTransferTimeout() {
        client.safe=false;flow.request("asia");now.set(29_000);client.safe=true;flow.tick();
        now.set(30_000);flow.tick();assertTrue(flow.active());
        client.arrived=true;now.set(58_999);flow.tick();assertEquals(1,client.resumes);
    }
    @Test void cancellationBeforeSendDoesNotSendLater() {
        client.safe=false;flow.request("asia");flow.cancel("Operator stopped");client.safe=true;flow.tick();
        assertEquals(0,client.sends);assertEquals(0,client.resumes);assertFalse(flow.active());
    }
    @Test void disconnectOrStaffOwnershipCancelsWithoutAutomaticResume() {
        flow.request("east");flow.tick();client.available=false;client.arrived=true;flow.tick();
        assertFalse(flow.active());assertEquals(0,client.resumes);assertEquals(1,client.releases);
    }
    static class Fake implements ManualRegionFlow.Client {
        boolean available=true,safe=true,durable=true,arrived,releasedBeforeResume;
        int sends,resumes,releases;String destination;
        public boolean available(){return available;}
        public boolean prepare(){return safe;}
        public boolean checkpointReady(){return durable;}
        public void send(String d){sends++;destination=d;}
        public boolean arrived(){return arrived;}
        public void release(){releases++;}
        public void resumeOwnedPause(){releasedBeforeResume=releases>0;resumes++;}
    }
}
