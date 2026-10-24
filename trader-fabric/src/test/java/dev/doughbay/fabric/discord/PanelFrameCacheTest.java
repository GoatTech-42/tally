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
    @Test void fullResolutionTransferSurvivesRefreshAndExpiresWithoutCrossingAccounts() throws Exception {
        var now=new AtomicLong(10_000);var work=new ArrayDeque<Runnable>();var cache=new PanelFrameCache(work::add,now::get);
        var image=new java.awt.image.BufferedImage(1920,400,java.awt.image.BufferedImage.TYPE_INT_RGB);
        var random=new Random(7);for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++)image.setRGB(x,y,random.nextInt());
        var bytes=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(image,"png",bytes);
        byte[] original=bytes.toByteArray();var full=new PanelFrameCache.Frame(original,Base64.getEncoder().encodeToString(original),null,"Money",now.get());
        String token=cache.pin("A|MONEY",full);
        cache.request("A|MONEY",()->frame(20_000));work.remove().run();
        assertSame(full,cache.transfer("A|MONEY",token));assertNull(cache.transfer("B|MONEY",token));
        String encoded=cache.transfer("A|MONEY",token).preview();var assembled=new StringBuilder();
        for(int start=0;start<encoded.length();start+=PanelFrameCache.CHUNK_CHARS)assembled.append(encoded,start,Math.min(encoded.length(),start+PanelFrameCache.CHUNK_CHARS));
        byte[] restored=Base64.getDecoder().decode(assembled.toString());assertArrayEquals(original,restored);
        assertEquals(1920,javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(restored)).getWidth());
        now.addAndGet(60_001);assertNull(cache.transfer("A|MONEY",token));
    }
}
