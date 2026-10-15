package dev.doughbay.fabric;

import dev.doughbay.storage.StaffActivityStore;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;

/** Immutable panel data. Existing detector events only; no extra Minecraft requests. */
public final class StaffWatchReport {
    public enum Event { OBSERVED,SPECTATOR,HIDDEN_FROM_TAB,NO_LONGER_OBSERVED,NEARBY,MODE_CHANGED }
    public record Row(String name,String presence,boolean sameRegion,boolean nearby,String observer,String region) {
        public Row(String name,String presence,boolean sameRegion,boolean nearby){this(name,presence,sameRegion,nearby,"","Unknown");}
    }
    public record Sighting(long at,String name,Event event,boolean sameRegion,String region,String observer) {}
    public record Snapshot(long at,boolean enabled,boolean connected,List<Row> rows,List<Sighting> history,String region,
                           List<StaffActivityMetrics.Summary> summaries,long watchedMillis,int observers,String storageStatus) {
        public Snapshot {rows=List.copyOf(rows);history=List.copyOf(history);summaries=List.copyOf(summaries);}
        public boolean fresh(long now){return at>0 && now>=at && now-at<=15_000;}
        public String status(long now) {
            if(!storageStatus.isEmpty())return storageStatus;
            if(!fresh(now))return "Current observation unavailable or stale";
            return connected?"Watching with "+observers+" hive observer(s)":"No active observer; current presence unknown";
        }
        public List<Row> current(long now){return fresh(now)&&connected?rows:List.of();}
    }
    private static Snapshot empty(){return new Snapshot(0,false,false,List.of(),List.of(),"Unknown",List.of(),0,0,"");}
    private static volatile Snapshot current=empty();
    private static volatile StaffActivityStore.Sample sample;
    private static volatile String region="Unknown";
    private static volatile long view;
    private static Supplier<String> owner=()->"";
    private static final ArrayDeque<StaffActivityStore.Event> pending=new ArrayDeque<>();
    private static ScheduledExecutorService worker;
    private static StaffActivityStore store;
    private StaffWatchReport() {}
    public static Snapshot snapshot(){return current;}
    public static void observationBoundary(){view++;region="Unknown";}
    public static void region(String value) {
        region=value!=null && value.matches("(?:NA West|NA East|Oceania|Asia|EU West|EU Central|Europe) [0-9]{1,3}")?value:"Unknown";
    }
    public static void publish(long at,boolean enabled,boolean connected,List<Row> rows) {
        String account=owner.get();if(account==null || !account.matches("[A-Za-z0-9_]{1,16}"))return;
        var people=rows.stream().filter(r->r.name().matches("[A-Za-z0-9_]{1,16}")).limit(100)
            .map(r->new StaffActivityStore.Person(r.name(),r.presence(),r.sameRegion(),r.nearby())).toList();
        sample=new StaffActivityStore.Sample(at,account,enabled,connected,region+" | view "+view,people);
    }
    public static synchronized void record(long at,String name,Event event){record(at,name,event,false);}
    public static synchronized void record(long at,String name,Event event,boolean sameRegion) {
        if(at<=0 || name==null || !name.matches("[A-Za-z0-9_]{1,16}") || event==null)return;
        var existing=pending.stream().filter(e->e.name().equalsIgnoreCase(name)&&e.event().equals(event.name())&&e.scope().equals(region)&&Math.abs(e.at()-at)<2000).findFirst();
        if(existing.isPresent()) {
            var prior=existing.get();
            if(sameRegion && !prior.sameRegion()){pending.remove(prior);pending.addLast(new StaffActivityStore.Event(prior.at(),prior.name(),prior.event(),true,prior.scope()));}
            return;
        }
        if(pending.size()>=200)pending.removeFirst();
        pending.addLast(new StaffActivityStore.Event(at,name,event.name(),sameRegion,region));
    }
    public static synchronized void start(Path directory,Supplier<String> account) {
        if(worker!=null)return;owner=account;
        worker=Executors.newSingleThreadScheduledExecutor(r->{var thread=new Thread(r,"GoNuts-staff-activity");thread.setDaemon(true);return thread;});
        worker.scheduleWithFixedDelay(()->refresh(directory),0,5,TimeUnit.SECONDS);
    }
    private static void refresh(Path directory) {
        long now=System.currentTimeMillis();var observation=sample;if(observation==null)return;
        try {
            if(store==null)store=new StaffActivityStore(directory.resolve("staff-watch-activity.db"));
            boolean fresh=now>=observation.at() && now-observation.at()<=15_000;
            var safe=fresh?observation:new StaffActivityStore.Sample(now,observation.observer(),false,false,"Unknown",List.of());
            List<StaffActivityStore.Event> events;synchronized(StaffWatchReport.class){events=List.copyOf(pending);}
            store.accept(safe,events);
            synchronized(StaffWatchReport.class){for(var event:events)pending.removeFirstOccurrence(event);}
            var report=store.report(now);
            var rows=report.current().stream().map(r->new Row(r.name(),r.presence(),r.sameRegion(),r.nearby(),r.observer(),r.scope())).toList();
            var history=report.history().stream().map(e->new Sighting(e.at(),e.name(),Event.valueOf(e.event()),e.sameRegion(),e.scope(),e.observer())).toList();
            current=new Snapshot(now,safe.enabled(),report.watching()>0,rows,history,region,
                StaffActivityMetrics.summarize(report,ZoneId.systemDefault()),StaffActivityMetrics.duration(report.coverage()),report.watching(),"");
        } catch(Exception failure) {
            var previous=current;
            current=new Snapshot(0,false,false,List.of(),previous.history(),"Unknown",previous.summaries(),previous.watchedMillis(),0,"History temporarily unavailable");
            if(store!=null){try{store.close();}catch(Exception ignored){}store=null;}
        }
    }
    public static synchronized void close(){if(worker!=null){worker.execute(()->{if(store!=null)try{store.close();}catch(Exception ignored){}store=null;});worker.shutdown();}}
    public static String label(Event event) {
        return switch(event) {
            case OBSERVED->"Observed in player list";case SPECTATOR->"Spectator mode observed";
            case HIDDEN_FROM_TAB->"Hidden from TAB (not proof of vanish)";
            case NO_LONGER_OBSERVED->"No longer observed (not proof of offline)";
            case NEARBY->"Nearby staff observed";case MODE_CHANGED->"Game-mode change observed";
        };
    }
    public static String scopeLabel(String scope){return scope.replaceFirst(" \\| view [0-9]+$","");}
    public static String presenceLabel(String presence){return switch(presence){case "SPECTATOR"->"Spectator observed";case "VANISHED"->"Hidden from TAB";default->"In player list";};}
}
