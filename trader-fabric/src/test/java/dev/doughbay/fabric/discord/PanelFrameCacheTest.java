package dev.doughbay.fabric.discord;

import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

class PanelFrameCacheTest {
    PanelFrameCache.Frame frame(long at){return new PanelFrameCache.Frame(new byte[]{1},"png",null,"Money",at);}
    @Test void slowRenderIsQueuedOnceAndNeverRunsInTheRequest() {
        var work=new ArrayDeque<Runnable>();var now=new AtomicLong(10_000);var cache=new PanelFrameCache(work::add,now::get);
        assertNull(cache.request("A|MONEY",()->frame(now.get())));
        assertEquals(1,work.size());
        assertNull(cache.request("A|MONEY",()->{fail("Duplicate render");return null;}));
        assertNull(cache.request("B|MONEY",()->frame(now.get())));assertEquals(1,work.size());
        work.remove().run();assertEquals(10_000,cache.peek("A|MONEY").at());assertNull(cache.peek("B|MONEY"));
        now.addAndGet(4_000);
        assertEquals(10_000,cache.request("A|MONEY",()->{throw new IllegalStateException("render unavailable");}).at());
        work.remove().run();assertEquals(10_000,cache.peek("A|MONEY").at(),"Failure must not make stale data look fresh");
    }
    @Test void accountAndTabKeysAreIndependentAndInvalidatedWorkCannotReappear() {
        var work=new ArrayDeque<Runnable>();var cache=new PanelFrameCache(work::add,()->10_000L);
        cache.request("A|MONEY",()->frame(1));work.remove().run();
        cache.request("A|ORDERS",()->frame(2));work.remove().run();
        cache.request("B|MONEY",()->frame(3));cache.invalidate("B|");work.remove().run();
        assertEquals(1,cache.peek("A|MONEY").at());assertEquals(2,cache.peek("A|ORDERS").at());assertNull(cache.peek("B|MONEY"));
        for(int i=0;i<20;i++){cache.request("scope"+i+"|MONEY",()->frame(1));work.remove().run();}
        assertNull(cache.peek("A|MONEY"),"Older views must be evicted from the bounded memory cache");
    }
}
