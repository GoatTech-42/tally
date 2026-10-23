package dev.doughbay.fabric;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.doughbay.fabric.hosted.DashboardProtocol;
import net.minecraft.client.Minecraft;
import java.net.URI;
import java.net.http.*;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.io.InputStream;

/** Mandatory, disclosed, typed diagnostics. Independent of Swarm, dashboard pairing and trading authorization. */
public final class MandatoryDiagnostics {
    public static final String NOTICE="GoNuts uploads sanitized diagnostic events and numeric settings for bug diagnosis and cross-client failure detection. Rolling cloud retention:24 hours. No chat, names, locations, credentials or raw logs/settings files. Network outages do not stop trading.";
    public static final URI CAPABILITIES=URI.create("https://gonuts.at/v1/bugs/diagnostics/capabilities");
    public static final URI EVENTS=URI.create("https://gonuts.at/v1/bugs/diagnostics/events");
    public static final String HEADER="X-GoNuts-Diagnostics";
    private record Pending(String kind,List<String> lines,String settings,long at){}
    private static final ArrayBlockingQueue<Pending> queue=new ArrayBlockingQueue<>(4096);
    private static final AtomicLong localDrops=new AtomicLong();
    private static final java.util.concurrent.atomic.AtomicBoolean faultWake=new java.util.concurrent.atomic.AtomicBoolean();
    private static final ScheduledExecutorService worker=Executors.newSingleThreadScheduledExecutor(r->{var t=new Thread(r,"gonuts-diagnostics");t.setDaemon(true);return t;});
    private static final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).followRedirects(HttpClient.Redirect.NEVER).build();
    private static final Map<String,Long> repeatedGates=new LinkedHashMap<>();
    private static volatile boolean started,supported;
    private static boolean statusChecked;
    private static volatile String credential="",settings="",state="Not initialized";
    private static DiagnosticOutbox outbox;
    private static Path directory;
    private static String build;
    private static long contextAt,nextAttempt,capabilityAt,acked,refused,lastWall,retryBlockedUntil,statusAt;
    private static int attempts,frameEvents=32;
    private MandatoryDiagnostics(){}
    public static synchronized void start(Path gameDirectory,String currentBuild){
        if(started)return;started=true;directory=gameDirectory.resolve("config/doughbay/diagnostics");build=dev.doughbay.fabric.hosted.DashboardProtocol.build(currentBuild);
        if(build.isEmpty())build="unknown";
        DoughBayClient.LOGGER.info(NOTICE);
        worker.execute(()->{try{outbox=new DiagnosticOutbox(directory,build,System.currentTimeMillis());credential=outbox.credential();settings=SettingsCode.exportForReport();outbox.append("LIFECYCLE",List.of("life event=START build="+build+" reason=GAME_START"),settings,System.currentTimeMillis());state="Queued for upload";}catch(Exception unavailable){state="Local diagnostic storage unavailable";}});
        worker.scheduleWithFixedDelay(MandatoryDiagnostics::cycle,1,1,TimeUnit.SECONDS);
    }
    public static void tick(Minecraft client){
        long now=System.currentTimeMillis();if(!started||now>=contextAt&&now-contextAt<5000)return;contextAt=now;
        try{settings=SettingsCode.exportForReport();String context=DoughBayClient.automationSessionController().bugReportContext(now);List<String> safe=context.lines().filter(l->l.length()<=500&&ReportContextLine.valid(l,new HashSet<>())).limit(32).toList();if(!safe.isEmpty())offer("SNAPSHOT",safe,now);}catch(RuntimeException ignored){/* Never affect native safety or trading. */}
    }
    public static void offer(String kind,List<String> lines,long at){
        if(!started||settings.isEmpty()||!DiagnosticOutbox.valid(kind,lines))return;
        if(kind.equals("GATE"))synchronized(repeatedGates){String shape=lines.stream().map(l->l.replaceFirst("at=\\d+","at=0")).collect(java.util.stream.Collectors.joining("\n"));Long last=repeatedGates.put(shape,at);if(repeatedGates.size()>2048)repeatedGates.remove(repeatedGates.keySet().iterator().next());if(last!=null&&at>=last&&at-last<10_000){localDrops.incrementAndGet();return;}}
        var captured=new ArrayList<>(lines);
        if((kind.equals("GATE")||kind.equals("DECISION"))&&localDrops.get()>0)captured.add("cov v=1 captured_at="+at+" first_at="+at+" last_at="+at+" retained=1 omitted="+localDrops.get()+" evicted="+localDrops.get());
        var pending=new Pending(kind,List.copyOf(captured),settings,at);
        if(!queue.offer(pending)){
            boolean replaced=false;
            if(!kind.equals("GATE")&&!kind.equals("SNAPSHOT"))for(var older:queue)if((older.kind.equals("GATE")||older.kind.equals("SNAPSHOT"))&&queue.remove(older)){localDrops.incrementAndGet();replaced=queue.offer(pending);break;}
            if(!replaced)localDrops.incrementAndGet();
        }
        if(kind.equals("FAULT")&&faultWake.compareAndSet(false,true))worker.execute(()->{try{expedite();cycle();}finally{faultWake.set(false);}});
    }
    public static void action(String line,long at){offer("ACTION",List.of(line.stripTrailing()),at);}
    public static void decision(String line,long at){offer(line.contains(" event=REFUSED ")?"GATE":"DECISION",List.of(line),at);}
    private static void drain()throws Exception{
        for(int i=0;i<512;i++){var item=queue.poll();if(item==null)break;try{outbox.append(item.kind,item.lines,item.settings,item.at);}catch(IllegalArgumentException invalid){localDrops.incrementAndGet();}}
    }
    private record Reply(int status,JsonNode body){}
    private static Reply request(HttpRequest request)throws Exception{
        var response=http.send(request,HttpResponse.BodyHandlers.ofInputStream());
        try(InputStream body=response.body()){
            CompletableFuture.delayedExecutor(10,TimeUnit.SECONDS).execute(()->{try{body.close();}catch(Exception ignored){}});
            byte[] bytes=body.readNBytes(65_537);if(bytes.length>65_536)throw new IllegalArgumentException("Oversized diagnostic response");
            JsonNode data;try{data=DashboardProtocol.JSON.readTree(bytes);}catch(Exception invalid){data=DashboardProtocol.JSON.createObjectNode();}
            return new Reply(response.statusCode(),data==null?DashboardProtocol.JSON.createObjectNode():data);
        }
    }
    static HttpRequest uploadRequest(ObjectNode frame,String key){
        if(!key.matches("gnd1_[A-Za-z0-9_-]{43}"))throw new IllegalArgumentException("Invalid diagnostic credential");
        return HttpRequest.newBuilder(EVENTS).timeout(Duration.ofSeconds(15)).header(HEADER,key).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(frame.toString())).build();
    }
    private static void cycle(){
        if(outbox==null)return;long now=System.currentTimeMillis();if(now<lastWall){nextAttempt=0;capabilityAt=0;}lastWall=now;
        try{
            drain();if(now<nextAttempt)return;
            if(!supported){if(now<capabilityAt)return;capabilityAt=now+60_000;var reply=request(HttpRequest.newBuilder(CAPABILITIES).timeout(Duration.ofSeconds(15)).GET().build());var cap=reply.body;supported=reply.status==200&&cap.path("supported").asBoolean()&&cap.path("version").asInt()==1;if(!supported){state="Queued; server capability pending";return;}}
            // A long-running process can outlive the server's retained session after an outage too.
            if(!statusChecked||now<statusAt||now-statusAt>=300_000){var reply=request(HttpRequest.newBuilder(URI.create("https://gonuts.at/v1/bugs/diagnostics/status")).timeout(Duration.ofSeconds(15)).header(HEADER,credential).GET().build());if(reply.status!=200)throw new java.io.IOException("Diagnostic status unavailable");outbox.reconcileStatus(reply.body);statusChecked=true;statusAt=now;}
            var frame=outbox.batch(now,frameEvents);if(frame==null){state="Uploaded; no pending events";return;}
            outbox.sent(frame);var reply=request(uploadRequest(frame,credential));nextAttempt=now+5000;
            switch(reply.status){
                case 200->{if(!outbox.acknowledge(reply.body))throw new IllegalArgumentException("Invalid diagnostic acknowledgement");acked+=reply.body.path("stored").asLong()+reply.body.path("duplicate").asLong();refused+=reply.body.path("refused").asLong();attempts=0;retryBlockedUntil=0;state=reply.body.path("quota").path("exhausted").asBoolean()?"Cloud quota reached; coverage gap recorded":"Uploading sanitized diagnostics";if(reply.body.path("quota").path("exhausted").asBoolean())retryBlockedUntil=nextAttempt=now+300_000;}
                case 400->{outbox.reject(frame);state="Invalid event frame removed; coverage gap recorded";}
                case 413->{frameEvents=Math.max(1,frameEvents/2);state="Retrying a smaller diagnostic batch";}
                case 429,503->{retryBlockedUntil=nextAttempt=now+Math.max(5000,Math.min(3_600_000,reply.body.path("retryAfterMs").asLong(60_000)));state="Upload delayed; durable retry queued";}
                case 401->{retryBlockedUntil=nextAttempt=now+300_000;state="Diagnostic credential refused; queued locally";}
                case 409->{outbox.recoverConflict(frame);statusChecked=false;state="Diagnostic session repaired; durable events retained";}
                default->throw new java.io.IOException("Diagnostic service unavailable");
            }
        }catch(Exception failure){retryBlockedUntil=nextAttempt=now+Math.min(300_000,5000L<<Math.min(6,attempts++));state="Upload unavailable; durable retry queued";}
    }
    private static void expedite(){if(System.currentTimeMillis()>=retryBlockedUntil)nextAttempt=0;}
    public static CompletableFuture<Void> flushBeforeReport(){if(!started)return CompletableFuture.completedFuture(null);return CompletableFuture.runAsync(()->{expedite();cycle();},worker);}
    public static void stop(){
        if(!started)return;started=false;
        try{worker.submit(()->{try{if(outbox!=null){drain();outbox.append("LIFECYCLE",List.of("life event=STOP build="+build+" reason=GAME_STOP"),settings,System.currentTimeMillis());outbox.close();}}catch(Exception ignored){}}).get(2,TimeUnit.SECONDS);}catch(Exception ignored){}
        worker.shutdown();
    }
    public static HttpRequest attach(HttpRequest original){return attach(original,credential);}
    static HttpRequest attach(HttpRequest original,String key){
        if(key==null||!key.matches("gnd1_[A-Za-z0-9_-]{43}")||!original.uri().getScheme().equals("https")||!"gonuts.at".equals(original.uri().getHost())||!original.uri().getPath().matches("/v1/bugs(?:/\\d+/evidence)?")||!original.method().equals("POST"))return original;
        var builder=HttpRequest.newBuilder(original.uri()).method(original.method(),original.bodyPublisher().orElse(HttpRequest.BodyPublishers.noBody()));original.timeout().ifPresent(builder::timeout);
        original.headers().map().forEach((name,values)->{if(!name.equalsIgnoreCase(HEADER))for(String value:values)builder.header(name,value);});return builder.header(HEADER,key).build();
    }
    public static Map<String,Object> status(){return Map.of("required",true,"state",state,"retentionHours",24,"storedOrDuplicateAcked",acked,"refused",refused,"localOmitted",localDrops.get(),"queuedInMemory",queue.size());}
}
