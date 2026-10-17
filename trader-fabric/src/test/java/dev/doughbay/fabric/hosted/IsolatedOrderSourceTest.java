package dev.doughbay.fabric.hosted;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
class IsolatedOrderSourceTest {
    @Test void staleLookupQueuesPeerOnceAndRetryResetsBudget(){
        var s=new IsolatedOrderSource();var calls=new AtomicInteger();
        assertEquals("source_queued",s.request("stale_read","Alt",List.of("alt","Main"),true,p->{assertEquals("Main",p);calls.incrementAndGet();return true;}));
        assertEquals("source_already_requested",s.request("stale_read","Alt",List.of("Main"),true,p->{fail();return true;}));
        s.reset();assertEquals("source_queued",s.request("no_full_read","Alt",List.of("Main"),true,p->{calls.incrementAndGet();return true;}));
        assertEquals(2,calls.get());
    }
    @Test void failedPersistenceAndMissingPeerAreExplicit(){
        var s=new IsolatedOrderSource();assertEquals("source_write_failed",s.request("self_read","Alt",List.of("Main"),true,p->false));
        assertEquals("source_already_requested",s.request("stale_read","Alt",List.of("Main"),true,p->{fail();return true;}));
        s.reset();assertEquals("source_no_peer",s.request("stale_read","Alt",List.of("ALT"),true,p->{fail();return true;}));
    }
    @Test void guardAndUnrelatedFailuresNeverPost(){
        var s=new IsolatedOrderSource();assertEquals("source_guard_wait",s.request("stale_read","Alt",List.of("Main"),false,p->{fail();return true;}));
        assertEquals("source_not_needed",s.request("snapshot_rate_limited","Alt",List.of("Main"),true,p->{fail();return true;}));
        assertEquals("source_queued",s.request("stale_read","Alt",List.of("Main"),true,p->true));
    }
}
