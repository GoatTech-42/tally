package dev.doughbay.fabric.discord;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

/** Bounded, memory-only report frames. A slow render never runs in an HTTP read. */
final class PanelFrameCache {
    record Frame(byte[] png, String preview, ObjectNode report, String title, long at) {}
    private static final class Entry { Frame frame; long attempted = Long.MIN_VALUE / 2; }
    private final LinkedHashMap<String, Entry> frames = new LinkedHashMap<>(16,.75f,true);
    private record Transfer(Frame frame,long expires) {}
    private final LinkedHashMap<String,Transfer> transfers=new LinkedHashMap<>();
    static final int CHUNK_CHARS=128*1024;
    synchronized String pin(String key,Frame frame) {
        String token=key+"|"+frame.at();
        transfers.entrySet().removeIf(e->e.getValue().expires()<clock.getAsLong());
        transfers.put(token,new Transfer(frame,clock.getAsLong()+60_000));
        long bytes=transfers.values().stream().mapToLong(t->t.frame().preview().length()*2L).sum();
        var it=transfers.entrySet().iterator();while(it.hasNext()&&(transfers.size()>8||bytes>16*1024*1024)){var t=it.next();bytes-=t.getValue().frame().preview().length()*2L;it.remove();}
        return Long.toString(frame.at());
    }
    synchronized Frame transfer(String key,String token) {
        var t=transfers.get(key+"|"+token);
        return t==null||t.expires()<clock.getAsLong()?null:t.frame();
    }
    private final Executor executor;
    private final LongSupplier clock;
    private boolean running;
    PanelFrameCache() {
        this(Executors.newSingleThreadExecutor(r->{var t=new Thread(r,"gonuts-panel-cache");t.setDaemon(true);return t;}),System::currentTimeMillis);
    }
    PanelFrameCache(Executor executor, LongSupplier clock) { this.executor=executor;this.clock=clock; }
    synchronized Frame peek(String key) { var e=frames.get(key);return e==null?null:e.frame; }
    synchronized Frame request(String key, Supplier<Frame> build) {
        var e=frames.computeIfAbsent(key,k->new Entry());long now=clock.getAsLong();
        if(!running && now-e.attempted>=3_000 && (e.frame==null || now-e.frame.at()>=3_000)) {
            running=true;e.attempted=now;
            executor.execute(()->{
                try { var result=build.get();synchronized(this){ if(frames.get(key)==e)e.frame=result; } }
                catch(Exception unavailable) { /* Retain the last successful frame and its original age. */ }
                finally { synchronized(this){running=false;trim();} }
            });
        }
        trim();return e.frame;
    }
    synchronized void invalidate(String prefix) { frames.keySet().removeIf(k->k.startsWith(prefix)); transfers.keySet().removeIf(k->k.startsWith(prefix)); }
    private void trim() {
        long bytes=frames.values().stream().filter(e->e.frame!=null).mapToLong(e->e.frame.png().length+e.frame.preview().length()*2L).sum();
        var it=frames.entrySet().iterator();while(it.hasNext()&&(frames.size()>12||bytes>24*1024*1024)) {
            var e=it.next().getValue();if(e.frame!=null)bytes-=e.frame.png().length+e.frame.preview().length()*2L;it.remove();
        }
    }
}
