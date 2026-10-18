package dev.doughbay.fabric.hosted;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/** Shared discovery and completed-sale history; native live reads and execution gates remain authoritative. */
public final class CommunityClient {
    public static final CommunityClient INSTANCE=new CommunityClient();
    private final ScheduledExecutorService worker=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"gonuts-community");t.setDaemon(true);return t;});
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER).build();
    private final ArrayDeque<CommunityBatches.Observation> queue=new ArrayDeque<>();
    private final LinkedHashSet<String> queries=new LinkedHashSet<>();
    private final java.util.concurrent.ConcurrentHashMap<String,Cached> cache=new java.util.concurrent.ConcurrentHashMap<>();
    private record Cached(JsonNode answer,long received,long expires,String region){}
    private volatile Set<String> discoveryMarkets=Set.of();
    private volatile Set<String> warmupMarkets=Set.of();
    private Set<String> warmupRequested=Set.of();
    private String warmupCursor="",warmupAccount="";
    private long warmupAsOf,warmupWindowStart,warmupEpoch,nextWarmup;
    private int warmupPages;
    private final Map<String,dev.doughbay.core.model.Sale> warmupRows=new LinkedHashMap<>();
    private record HistoryBatch(List<dev.doughbay.core.model.Sale> rows,String account,long epoch){}
    private volatile HistoryBatch warmupBatch;
    private volatile String warmupStatus="Local collection; shared history not requested";
    private volatile int warmupImported;
    public void refreshWarmupMarkets(Set<String> markets){
        warmupMarkets=Set.copyOf(markets.stream().filter(k->k.matches("[a-z0-9_.-]+:[a-z0-9_./-]+\\|[1-9][0-9]?"))
                .sorted().limit(50).toList());
    }
    /** Runs on the existing market worker with its database connection, never the game or network thread. */
    public int seedWarmup(dev.doughbay.storage.Database db,long now)throws java.sql.SQLException{
        var batch=warmupBatch;if(batch==null)return 0;
        java.util.function.BooleanSupplier allowed=()->enabled&&eligible&&connected&&guidanceConsent
                &&marketScope().equals("net-donut")&&batch.epoch()==generation.get()&&batch.account().equals(account);
        if(!allowed.getAsBoolean()){warmupBatch=null;return 0;}
        int minimum=(int)Math.max(dev.doughbay.fabric.Tuning.get("warmup.samples"),dev.doughbay.fabric.Tuning.get("orders.bid_min_samples"));
        int imported;
        try{imported=CommunityHistoryBootstrap.seed(db,batch.rows(),now,minimum,allowed);}
        catch(java.sql.SQLException|RuntimeException e){
            dev.doughbay.fabric.FaultTrace.INSTANCE.record(dev.doughbay.fabric.FaultTrace.Stage.COMMUNITY_HISTORY,e,System.currentTimeMillis());
            warmupBatch=null;warmupMarkets=Set.of();
            warmupStatus="Shared history could not be saved; collecting locally";return 0;
        }
        warmupImported+=imported;warmupBatch=null;warmupMarkets=Set.of();
        warmupStatus=imported>0?"Imported "+imported+" shared completed-sale observations; live checks still required"
                :"Shared history had no sufficiently fresh, covered market; collecting locally";
        return imported;
    }
    private long nextDiscoveryRefresh;
    private volatile long discoveryRefreshes,discoveryResponses,discoveryConsultations,discoveryPlansWithHints,discoveryBidPlans,discoveryChecks;
    private volatile ObjectNode lastDiscoveryUse=DashboardProtocol.JSON.createObjectNode();
    /** Only announces demand. HTTP runs on the existing worker, never on the game/planner thread. */
    public void refreshDiscoveryMarkets(Set<String> markets) {
        var clean=new TreeSet<String>();
        for(String key:markets)if(key.matches("[a-z0-9_.-]+:[a-z0-9_./-]+\\|[1-9][0-9]?"))clean.add(key);
        discoveryMarkets=Set.copyOf(clean.stream().limit(50).toList());
    }
    /** Cache-only: missing/expired/error data cannot hold up the native planner. */
    public Map<String,CommunityDiscoveryFeed.Hint> discoveryHints(Set<String> markets,long now) {
        discoveryConsultations++;
        if(!enabled||!connected||!eligible||!marketScope().equals("net-donut"))return Map.of();
        var hints=new HashMap<String,CommunityDiscoveryFeed.Hint>();
        for(String key:markets) {
            var value=cache.get(key);
            if(value==null||!value.region().equals("net-donut")||value.received()>now||value.expires()<=now)continue;
            var hint=CommunityDiscoveryFeed.read(key,value.answer(),now);if(hint!=null)hints.put(key,hint);
        }
        if(!hints.isEmpty())discoveryPlansWithHints++;
        return Map.copyOf(hints);
    }
    public void noteDiscoveryUse(CommunityDiscoveryFeed.Hint hint,String purpose) {
        if(hint==null||!Set.of("bid_plan","auction_check","order_quote").contains(purpose))return;
        if(purpose.equals("bid_plan"))discoveryBidPlans++;else discoveryChecks++;
        var view=DashboardProtocol.JSON.createObjectNode().put("at",System.currentTimeMillis()).put("market",hint.market())
                .put("observedAt",hint.observedAt()).put("purpose",purpose).put("historicalBidNotExecutable",hint.historicalBid())
                .put("historicalAskNotExecutable",hint.historicalAsk()).put("availabilityProven",false).put("actionsDispatched",false);
        var sources=view.putArray("sources");hint.sources().stream().sorted().forEach(sources::add);lastDiscoveryUse=view;
    }
    private final CommunityQueryBackoff queryBackoff=new CommunityQueryBackoff();
    private final CommunityServiceRetry serviceRetry=new CommunityServiceRetry();
    private volatile String requestStage="startup";
    private volatile int lastHttpStatus;
    private final AtomicLong generation=new AtomicLong();
    private volatile boolean enabled,started,connected,guidanceConsent,configReady,linkConsentApplied;
    private volatile String account="",status="Off",build="",lastUploadScope="",lastQueryScope="";
    private volatile long lastUpload,lastQuery,accepted,rejected,dropped,compared,usable,wouldReplace,agreements;
    private volatile boolean enrolled,eligible,networkSupported;
    private volatile long lastServiceFailure;
    private final ExecutorService shadowWorker=Executors.newSingleThreadExecutor(r->{var t=new Thread(r,"gonuts-order-shadow");t.setDaemon(true);return t;});
    private final AtomicLong shadowSequence=new AtomicLong();
    private record ShadowCycle(CommunityOrderShadow.Context context,CompletableFuture<CommunityOrderShadow.Snapshot> future){}
    private final ConcurrentHashMap<Long,ShadowCycle> shadowCycles=new ConcurrentHashMap<>();
    private volatile ObjectNode lastOrderShadow=DashboardProtocol.JSON.createObjectNode();
    private volatile ObjectNode snapshotValidation=DashboardProtocol.JSON.createObjectNode();
    private volatile long nextValidationAt;
    private volatile int validationAttempts;
    private final java.util.concurrent.atomic.AtomicBoolean validationRunning=new java.util.concurrent.atomic.AtomicBoolean();
    private long nextPerformance;
    private volatile String measurementStatus="Starting";
    private final java.util.concurrent.ConcurrentHashMap<String,Long> rejectionCodes=new java.util.concurrent.ConcurrentHashMap<>();
    private CommunityStore store;private volatile String credential="";private String contributor="",registeredAccount="";
    private volatile boolean credentialRejected;
    private Map<CommunityOrderReceipt,Integer> pendingReceipts=Map.of();
    private ObjectNode pending;private Set<String> types=Set.of();private long nextUpload,nextQuery,nextCapabilities,nextStatus;private boolean serviceEnabled;private int failures;
    private CommunityClient(){}
    public synchronized void start(Path game,String build){
        if(started)return;started=true;store=new CommunityStore(game);this.build=build;CommunityPerformance.INSTANCE.enable();
        worker.execute(()->{
            try{var config=store.read("settings.json");if(config!=null){enabled=config.path("enabled").asBoolean();guidanceConsent=config.path("guidanceEnabled").asBoolean(false);linkConsentApplied=config.path("linkConsentApplied").asBoolean(false);credential=config.path("credential").asText();contributor=config.path("contributorId").asText();registeredAccount=config.path("account").asText();
                if(!credential.isEmpty()&&!credential.matches("gnc1_[A-Za-z0-9_-]{43}"))throw new IllegalArgumentException();}
                credentialRejected=config!=null&&config.path("credentialRejected").asBoolean();
                pending=store.read("pending.json");enrolled=!credential.isEmpty();status=credentialRejected?"Swarm membership needs renewal; use Retry Swarm connection":enabled?"Starting Swarm":"Off";
            }catch(Exception e){enabled=false;credential="";status="Could not load private community settings";}
            finally{configReady=true;}
        });
        worker.scheduleWithFixedDelay(()->{try{pump();}catch(Exception e){
            long now=System.currentTimeMillis();lastServiceFailure=now;
            serviceRetry.fail(now,0);
            status="Swarm "+requestStage+": "+(lastHttpStatus>=400?"HTTP "+lastHttpStatus:CommunityServiceRetry.reason(e))
                    +"; retry pending, local scanning continues";
        }},1,1,TimeUnit.SECONDS);
        Runtime.getRuntime().addShutdownHook(new Thread(()->{worker.shutdownNow();shadowWorker.shutdownNow();},"gonuts-community-close"));
    }
    public void connected(boolean value){connected=value;}
    public String marketScope(){return connected&&networkSupported?CommunityBatches.NETWORK:"";}
    /** Cache-only admission for sharing detector observations; no enrollment or control side effects. */
    public boolean sharingStaffSightings(long now){
        return connected&&networkSupported&&staffSharingSession(now);
    }
    /** Rechecks consent and transport when delivering evidence observed before an automatic game disconnect. */
    public boolean staffSharingSession(long now){
        var dashboard=HostedDashboardClient.INSTANCE.view();
        return started&&enabled&&enrolled&&eligible&&dashboard.linked()&&dashboard.online()
                &&!account.isEmpty()&&account.equalsIgnoreCase(registeredAccount)
                &&lastUpload>0&&now>=lastUpload&&now-lastUpload<=600_000;
    }
    public void account(String name){account=CommunityWire.account(name)?name:"";}
    /** Linking the dashboard includes Swarm contribution; unlinking removes participation. */
    public void dashboardLinked(boolean linked){if(claimLinkedConsent(linked))enable();}
    synchronized boolean claimLinkedConsent(boolean linked){
        if(!started||!configReady||!linked||linkConsentApplied)return false;
        linkConsentApplied=true;return true;
    }
    public void dashboardUnlinked(){linkConsentApplied=false;disableSharing();}
    public boolean guidanceEnabled(){return guidanceEnabled(HostedDashboardClient.INSTANCE.view().linked());}
    boolean guidanceEnabled(boolean dashboardLinked){return started&&enabled&&guidanceConsent&&eligible&&dashboardLinked;}
    public void enable(){enable(false);}
    private void enable(boolean freshPairing){if(!started||!HostedDashboardClient.INSTANCE.view().linked())return;generation.incrementAndGet();status="Connecting Swarm through your linked dashboard";worker.execute(()->{try{if(freshPairing){credentialRejected=true;linkConsentApplied=true;}renewRejectedCredential();enabled=true;guidanceConsent=true;save();nextCapabilities=nextUpload=nextStatus=0;}catch(Exception e){enabled=false;guidanceConsent=false;status="Could not save consent";}});}
    /** A new explicit pairing must not inherit an earlier installation's membership. */
    public void dashboardPaired(){enable(true);}
    boolean renewRejectedCredential(){
        if(!credentialRejected&&enabled)return false;
        credential="";contributor="";registeredAccount="";enrolled=false;eligible=false;credentialRejected=false;
        return true;
    }
    void refuseCredential(int code)throws Exception{
        if(code!=401&&code!=403)throw new IllegalArgumentException("Not a credential refusal");
        enabled=false;eligible=false;credentialRejected=true;
        SwarmRank.clear();
        status="Swarm membership refused (HTTP "+code+"). Use Retry Swarm connection to renew through your linked dashboard.";
        save();
    }
    public void disable(){
        if(HostedDashboardClient.INSTANCE.view().linked()){status="Swarm participation is included with the dashboard; unlink to leave";return;}
        disableSharing();
    }
    private void disableSharing(){if(!started)return;enabled=false;guidanceConsent=false;generation.incrementAndGet();status="Disabling Swarm";worker.execute(()->{
        synchronized(this){queue.clear();queries.clear();cache.clear();}
        try{save();acknowledgeOrderBatch(false);store.remove("pending.json");pending=null;unenroll();}catch(Exception e){status="Off; waiting to remove server enrollment";}
    });}
    private void save()throws Exception{store.write("settings.json",DashboardProtocol.JSON.createObjectNode().put("enabled",enabled).put("guidanceEnabled",guidanceConsent).put("linkConsentApplied",linkConsentApplied).put("consent",CommunityWire.CONSENT)
            .put("credential",credential).put("contributorId",contributor).put("account",registeredAccount).put("credentialRejected",credentialRejected));}
    private volatile ObjectNode isolatedOrderJob=DashboardProtocol.JSON.createObjectNode();
    public void publishIsolatedOrderJob(ObjectNode state){isolatedOrderJob=state.deepCopy();}
    public void discardOrderShadow(long id){var cycle=shadowCycles.remove(id);if(cycle!=null)cycle.future().cancel(false);}
    private volatile ObjectNode experimentMeasurement=DashboardProtocol.JSON.createObjectNode();
    public void publishExperimentMeasurement(ObjectNode value){experimentMeasurement=value.deepCopy();}
    private volatile ObjectNode productionDiscovery=DashboardProtocol.JSON.createObjectNode().put("executionEnabled",false);
    public void publishProductionDiscovery(ObjectNode value){productionDiscovery=value.deepCopy();}
    public ObjectNode view(){
        var value=DashboardProtocol.JSON.createObjectNode().put("enabled",enabled).put("guidanceEnabled",guidanceConsent).put("enrolled",enrolled).put("eligible",eligible).put("shadow",!guidanceConsent).put("status",status)
            .put("lastUpload",lastUpload).put("lastQuery",lastQuery).put("accepted",accepted).put("rejected",rejected).put("dropped",dropped)
            .put("lastUploadScope",lastUploadScope).put("lastQueryScope",lastQueryScope).put("region",marketScope()).put("marketScope",marketScope()).put("observerShard",CommunityRegion.current()).put("comparisons",compared).put("usable",usable).put("wouldReplace",wouldReplace).put("agreements",agreements);
        value.put("requestStage",requestStage).put("lastHttpStatus",lastHttpStatus).put("needsRenewal",credentialRejected);
        value.putObject("warmup").put("status",warmupStatus).put("importedObservations",warmupImported).put("historicalOnly",true);
        value.set("productionDiscovery",productionDiscovery.deepCopy());
        var feed=value.putObject("discoveryFeed").put("requestedMarkets",discoveryMarkets.size()).put("refreshRequests",discoveryRefreshes)
                .put("responsesAccepted",discoveryResponses).put("plannerConsultations",discoveryConsultations)
                .put("plansWithFreshHints",discoveryPlansWithHints).put("guidedBidPlans",discoveryBidPlans).put("guidedNativeChecks",discoveryChecks)
                .put("backgroundOnly",true).put("executionPricesFromSharedData",false);
        feed.set("lastUse",lastDiscoveryUse.deepCopy());
        value.set("experimentMeasurement",experimentMeasurement.deepCopy());
        value.set("orderCoverage",CommunityObservations.orderCoverage());
        value.set("orderShadow",lastOrderShadow.deepCopy());
        value.set("orderJob",isolatedOrderJob.deepCopy());
        value.set("orderSnapshotValidation",snapshotValidation.deepCopy());
        value.set("performance",CommunityPerformance.INSTANCE.view().put("snapshotStatus",measurementStatus));
        var codes=value.putObject("rejectionCodes");rejectionCodes.forEach(codes::put);return value;
    }
    public synchronized void observe(ObjectNode record){
        if(!enabled||!connected||record==null)return;
        if(queue.size()>=10_000){dropped++;return;}
        queue.addLast(new CommunityBatches.Observation(record,marketScope()));
    }
    synchronized CommunityOrderReceipt beginOrderReceipt(){
        var receipt=new CommunityOrderReceipt(marketScope());
        if(!enabled||!connected||!receipt.scope.equals("net-donut"))receipt.fail();
        return receipt;
    }
    synchronized boolean streamOrder(CommunityOrderReceipt receipt,ObjectNode record){
        if(!enabled||!connected||!receipt.scope.equals(marketScope())||queue.size()>=10_000){receipt.fail();dropped++;return false;}
        try{receipt.queued();}catch(IllegalStateException ignored){return false;}
        queue.addLast(new CommunityBatches.Observation(record,receipt.scope,receipt));return true;
    }
    synchronized void finishOrderReceipt(CommunityOrderReceipt receipt,ObjectNode marker){
        publishOrderMarker(receipt,receipt.finish(marker));
    }
    private void publishOrderMarker(CommunityOrderReceipt receipt,ObjectNode marker){
        if(marker==null)return;
        if(!enabled||!connected||!receipt.scope.equals(marketScope())||queue.size()>=10_000){receipt.fail();return;}
        queue.addLast(new CommunityBatches.Observation(marker,receipt.scope));
    }
    private synchronized void acknowledgeOrderBatch(boolean accepted){
        var receipts=pendingReceipts;pendingReceipts=Map.of();
        for(var entry:receipts.entrySet()){
            if(!accepted)entry.getKey().fail();
            else publishOrderMarker(entry.getKey(),entry.getKey().accepted(entry.getValue()));
        }
    }
    public synchronized void requestMarket(String item,int stack){
        if(!enabled||!connected)return;
        try{String key=CommunityWire.key(item,stack);var value=cache.get(key);if(value!=null&&value.expires()>System.currentTimeMillis()&&value.region().equals(marketScope()))return;
            queries.add(key);while(queries.size()>100)queries.remove(queries.iterator().next());
        }catch(IllegalArgumentException ignored){}
    }
    public void compare(String item,int stack,double local,long at){
        if(!enabled||!Double.isFinite(local)||local<=0)return;
        try{
            String key=CommunityWire.key(item,stack);compared++;var value=cache.get(key);
            if(value==null||value.received()>at||value.expires()<at||!value.region().equals(marketScope()))return;
            var estimate=CommunityWire.estimate(value.answer());if(estimate==null)return;
            usable++;double difference=Math.abs(estimate.unitPrice()-local)/local;if(difference<=.10)agreements++;
            if(estimate.replaceable())wouldReplace++;
            org.slf4j.LoggerFactory.getLogger("gonuts-community").info("GoNuts community shadow: item={} source={} localUnit={} communityUnit={} differencePct={} replaceable={} skipped=false",key,estimate.source(),local,estimate.unitPrice(),Math.round(difference*100),estimate.replaceable());
        }catch(IllegalArgumentException ignored){}
    }
    private JsonNode shadowGet(String path)throws Exception{
        if(!queryBackoff.allowed(System.currentTimeMillis()))throw new IllegalArgumentException("snapshot_rate_limited");
        if(!enabled||!marketScope().equals("net-donut"))throw new IllegalArgumentException("scope_changed");
        var req=HttpRequest.newBuilder(CommunityWire.BASE.resolve(path)).timeout(java.time.Duration.ofSeconds(2)).header("Authorization","Bearer "+credential).GET().build();
        HttpResponse<byte[]> response;
        try{dev.doughbay.fabric.SwarmActivity.sent();response=http.send(req,HttpResponse.BodyHandlers.ofByteArray());if(response.statusCode()==200)dev.doughbay.fabric.SwarmActivity.received();}catch(Exception error){lastServiceFailure=System.currentTimeMillis();throw error;}
        if(response.statusCode()==429){queryBackoff.defer(System.currentTimeMillis(),response.headers().firstValue("Retry-After").orElse("60"));lastServiceFailure=System.currentTimeMillis();throw new IllegalArgumentException("snapshot_rate_limited");}
        if(response.statusCode()!=200){lastServiceFailure=System.currentTimeMillis();throw new IllegalArgumentException("snapshot_http_failure");}
        return json(response);
    }
    private void validation(long now){
        if(!enabled||!enrolled||!marketScope().equals("net-donut")||validationAttempts>=10||now<nextValidationAt||!validationRunning.compareAndSet(false,true))return;
        validationAttempts++;nextValidationAt=now+60_000;
        shadowWorker.execute(()->{
            try{
                var context=new CommunityOrderShadow.Context(now,200,Set.of(),false,0,false);
                CommunityOrderShadow.Snapshot snapshot;
                try{snapshot=CommunityOrderShadow.fetch(this::shadowGet,context,System.currentTimeMillis(),"validation");}
                catch(Exception error){String reason=error instanceof IllegalArgumentException?error.getMessage():"snapshot_unavailable";
                    snapshot=CommunityOrderShadow.unavailable(reason!=null&&reason.matches("[a-z_]{1,64}")?reason:"snapshot_unavailable");}
                if(!enabled)return;
                var result=CommunityOrderShadow.validation(snapshot,System.currentTimeMillis());
                var state=store.read("snapshot-validation.json");if(state==null)state=DashboardProtocol.JSON.createObjectNode().put("schema",1);
                state.set("latest",result);if(result.path("validated").asBoolean()){state.set("lastSuccess",result.deepCopy());validationAttempts=10;}
                store.write("snapshot-validation.json",state);snapshotValidation=state;
            }catch(Exception error){snapshotValidation=DashboardProtocol.JSON.createObjectNode().put("reason","validation_storage_failed");}
            finally{validationRunning.set(false);}
        });
    }
    public long beginOrderShadow(Set<String> managed,boolean ownFresh,int ownOrders,int pageCap,long at){
        if(!started||!enabled||!enrolled||!connected||shadowCycles.size()>=4)return 0;
        long id=shadowSequence.incrementAndGet();
        var context=new CommunityOrderShadow.Context(at,pageCap,managed,ownFresh,ownOrders,lastServiceFailure==0||at-lastServiceFailure>=300_000);
        String scope=marketScope();
        var future=CompletableFuture.supplyAsync(()->{
            if(!scope.equals("net-donut"))return CommunityOrderShadow.unavailable("scope_unavailable");
            try{return CommunityOrderShadow.fetch(this::shadowGet,context,System.currentTimeMillis());}
            catch(Exception error){String reason=error instanceof IllegalArgumentException?error.getMessage():"snapshot_unavailable";
                return CommunityOrderShadow.unavailable(reason!=null&&reason.matches("[a-z_]{1,64}")?reason:"snapshot_unavailable");}
        },shadowWorker).completeOnTimeout(CommunityOrderShadow.unavailable("snapshot_timeout"),60,TimeUnit.SECONDS);
        shadowCycles.put(id,new ShadowCycle(context,future));return id;
    }
    /** Nonblocking: the controller never waits on HTTP or runs on the network executor. */
    public CommunityOrderShadow.Snapshot orderShadowSnapshot(long id){
        var cycle=shadowCycles.get(id);if(cycle==null)return null;
        try{return cycle.future().getNow(null);}catch(CompletionException | CancellationException ignored){return CommunityOrderShadow.unavailable("snapshot_unavailable");}
    }
    public void finishOrderShadow(long id,List<dev.doughbay.fabric.AutomatedExecutionDriver.OrderRow> rows,boolean succeeded,long ended){
        finishOrderShadow(id,rows,succeeded,ended,null);
    }
    public void finishOrderShadow(long id,List<dev.doughbay.fabric.AutomatedExecutionDriver.OrderRow> rows,boolean succeeded,long ended,ObjectNode decisions){
        final ObjectNode decisionCopy=decisions==null?null:decisions.deepCopy();
        var cycle=shadowCycles.remove(id);if(cycle==null)return;
        if(succeeded){validationAttempts=0;nextValidationAt=ended+90_000;}
        var copy=List.copyOf(rows);
        cycle.future().thenAcceptAsync(snapshot->{
            if(!enabled)return;
            try{var context=cycle.context();context=new CommunityOrderShadow.Context(context.startedAt(),context.pageCap(),context.managed(),context.ownFresh(),context.ownOrders(),context.serviceHealthy()&&(lastServiceFailure==0||System.currentTimeMillis()-lastServiceFailure>=300_000));
                var result=CommunityOrderShadow.compare(snapshot,context,CommunityObservations.orderLevels(copy,ended),ended,succeeded);
                result.put("cycle",id).put("unsupportedLiveRows",Math.max(0,copy.size()-CommunityObservations.orderLevels(copy,ended).stream().mapToInt(r->r.path("depth").asInt(1)).sum()));
                if(decisionCopy!=null){
                    result.set("decisionComparison",decisionCopy);
                    result.put("tradingDecisionComparison",decisionCopy.path("verdict").asText("unverifiable"));
                    if(result.path("reason").asText().equals("decision_simulation_required"))
                        result.put("reason",decisionCopy.path("reason").asText("decision_comparison_unavailable"));
                }
                lastOrderShadow=result;
                var saved=store.read("order-shadow.json");if(saved==null)saved=DashboardProtocol.JSON.createObjectNode().put("schema",1);
                var entries=saved.withArray("cycles");while(entries.size()>=30)entries.remove(0);entries.add(result);store.write("order-shadow.json",saved);
            }catch(Exception error){lastOrderShadow=DashboardProtocol.JSON.createObjectNode().put("reason","comparison_or_storage_failed").put("wouldSkip",false).put("skipped",false);}
        },shadowWorker);
    }
    public void finishGuidanceShadow(long id,ObjectNode decisions,long ended){
        var cycle=shadowCycles.remove(id);if(cycle==null)return;
        var copy=decisions.deepCopy();
        cycle.future().thenAcceptAsync(snapshot->{
            if(!enabled)return;
            try{
                var result=CommunityOrderShadow.guidanceResult(snapshot,cycle.context(),copy,ended).put("cycle",id);
                lastOrderShadow=result;
                var saved=store.read("order-shadow.json");if(saved==null)saved=DashboardProtocol.JSON.createObjectNode().put("schema",1);
                var entries=saved.withArray("cycles");while(entries.size()>=30)entries.remove(0);entries.add(result);store.write("order-shadow.json",saved);
            }catch(Exception error){lastOrderShadow=DashboardProtocol.JSON.createObjectNode().put("reason","guidance_storage_failed").put("skipped",false);}
        },shadowWorker);
    }
    public double scanCandidate(String item,int stack){
        if(!enabled||!connected||!eligible)return 0;
        try{long now=System.currentTimeMillis();String region=marketScope();var value=cache.get(CommunityWire.key(item,stack));
            if(region.isBlank()||value==null||value.received()>now||value.expires()<now||!value.region().equals(region))return 0;
            var estimate=CommunityWire.estimate(value.answer());return estimate!=null&&estimate.replaceable()?estimate.unitPrice():0;
        }catch(IllegalArgumentException ignored){return 0;}
    }
    private void savePerformance(long now){
        if(now<nextPerformance)return;nextPerformance=now+60_000;
        try{var file=store.read("performance.json");if(file==null)file=DashboardProtocol.JSON.createObjectNode().put("schema",1);
            var current=CommunityPerformance.INSTANCE.view();current.put("build",DashboardProtocol.build(build));
            var sessions=DashboardProtocol.JSON.createArrayNode();
            for(var previous:file.path("sessions")){if(previous.path("session").asText().equals(current.path("session").asText()))continue;
                if(previous.path("observedAt").asLong()>now-7*24*60*60_000L)sessions.add(previous);}
            while(sessions.size()>=168)sessions.remove(0);sessions.add(current);file.set("sessions",sessions);store.write("performance.json",file);
            measurementStatus="Recording locally";
        }catch(Exception ignored){measurementStatus="Local measurement snapshot unavailable";}
    }
    private HttpResponse<byte[]> request(String method,String path,ObjectNode body,boolean auth)throws Exception{
        requestStage=path.equals("capabilities")?"capabilities":path.equals("me/accounts")?"account registration":path.equals("me")?"membership":path.equals("unenroll")?"leaving":"market query";
        lastHttpStatus=0;
        var builder=HttpRequest.newBuilder(CommunityWire.BASE.resolve(path)).timeout(java.time.Duration.ofSeconds(10));
        if(auth)builder.header("Authorization","Bearer "+credential);
        if(body!=null)builder.header("Content-Type","application/json").method(method,HttpRequest.BodyPublishers.ofString(body.toString()));else builder.method(method,HttpRequest.BodyPublishers.noBody());
        try{dev.doughbay.fabric.SwarmActivity.sent();var response=http.send(builder.build(),HttpResponse.BodyHandlers.ofByteArray());lastHttpStatus=response.statusCode();if(response.statusCode()>=200&&response.statusCode()<300)dev.doughbay.fabric.SwarmActivity.received();if(response.statusCode()==429&&CommunityQueryBackoff.query(method,path))queryBackoff.defer(System.currentTimeMillis(),response.headers().firstValue("Retry-After").orElse("60"));if(response.statusCode()>=400)lastServiceFailure=System.currentTimeMillis();
            if(response.statusCode()==429&&!CommunityQueryBackoff.query(method,path))serviceRetry.fail(System.currentTimeMillis(),retryAfter(response));
            if(response.statusCode()>=500)throw new java.io.IOException("Service unavailable");
            if(response.statusCode()>=200&&response.statusCode()<300)serviceRetry.success();
            return response;}
        catch(Exception error){lastServiceFailure=System.currentTimeMillis();throw error;}
    }
    private JsonNode json(HttpResponse<byte[]> response)throws Exception{
        if(response.body().length>2*1024*1024)throw new IllegalArgumentException();return DashboardProtocol.JSON.readTree(response.body());
    }
    private void pump()throws Exception{
        long now=System.currentTimeMillis();savePerformance(now);
        if(!serviceRetry.ready(now))return;
        if(!enabled){if(credentialRejected)return;if(!credential.isEmpty()&&now>=nextUpload){nextUpload=now+60_000;unenroll();}return;}
        if(!HostedDashboardClient.INSTANCE.view().linked())return;
        if(now>=nextCapabilities){nextCapabilities=now+60_000;var response=request("GET","capabilities",null,false);if(response.statusCode()!=200){serviceEnabled=false;networkSupported=false;status="Swarm capabilities unavailable (HTTP "+response.statusCode()+"); local scanning continues";return;}
            var c=json(response);serviceEnabled=c.path("enabled").asBoolean()&&java.util.stream.StreamSupport.stream(c.path("schemas").spliterator(),false).anyMatch(v->v.isInt()&&v.asInt()==1);var supported=new HashSet<String>();for(var type:c.path("types"))supported.add(type.asText());types=Set.copyOf(supported);networkSupported=serviceEnabled&&CommunityBatches.networkAdvertised(c);}
        if(!serviceEnabled||account.isBlank())return;
        if(credential.isEmpty()){
            if(now<nextUpload)return;nextUpload=now+60_000;enroll();return;
        }
        if(!registeredAccount.equals(account)){
            var body=DashboardProtocol.JSON.createObjectNode();body.putArray("observerAccounts").add(account);var response=request("PUT","me/accounts",body,true);
            if(response.statusCode()!=204){status="Swarm account registration unavailable (HTTP "+response.statusCode()+"); retry pending, local scanning continues";return;}registeredAccount=account;save();
        }
        if(now>=nextStatus){
            nextStatus=now+60_000;var response=request("GET","me",null,true);
            if(response.statusCode()==200)eligible=json(response).path("eligible").asBoolean();
            else if(response.statusCode()==401||response.statusCode()==403){refuseCredential(response.statusCode());return;}
        }
        validation(now);
        if(!discoveryMarkets.isEmpty()&&now>=nextDiscoveryRefresh&&queryBackoff.allowed(now)) {
            nextDiscoveryRefresh=now+30_000;queryDiscovery();
        }
        if(now>=nextQuery&&queryBackoff.allowed(now)){nextQuery=now+2_000;query(now);}
        if(now>=nextUpload){nextUpload=now+10_000;upload(now);}
        if(eligible&&guidanceConsent&&!warmupMarkets.isEmpty()&&warmupBatch==null&&now>=nextWarmup&&queryBackoff.allowed(now)){
            nextWarmup=now+2_000;queryWarmup(now);
        }
    }
    private void queryWarmup(long now)throws Exception{
        var wanted=warmupMarkets;long own=generation.get();String owner=account;
        if(!wanted.equals(warmupRequested)||warmupEpoch!=own||!warmupAccount.equals(owner)){
            warmupRequested=wanted;warmupEpoch=own;warmupAccount=owner;warmupAsOf=0;warmupCursor="";warmupPages=0;warmupRows.clear();
        }
        warmupStatus="Loading shared completed-sale history; local collection continues";
        String path=historyPath(wanted,warmupCursor);
        try{
            var response=request("GET",path,null,true);
            if(own!=generation.get()||!owner.equals(account)||!enabled||!eligible||!wanted.equals(warmupMarkets))return;
            if(response.statusCode()!=200){
                if(response.statusCode()==401||response.statusCode()==403)refuseCredential(response.statusCode());
                throw new IllegalArgumentException("Shared history unavailable");
            }
            var page=CommunityHistoryBootstrap.decode(json(response),wanted,System.currentTimeMillis(),warmupAsOf);
            if(warmupAsOf>0&&warmupWindowStart!=page.windowStart())throw new IllegalArgumentException("History window changed");
            warmupWindowStart=page.windowStart();
            warmupAsOf=page.asOf();warmupPages++;
            for(var row:page.sales()){
                var old=warmupRows.putIfAbsent(row.transactionHash(),row);
                if(old!=null&&!old.equals(row))throw new IllegalArgumentException("Conflicting history id");
            }
            if(warmupRows.size()>50_000||warmupPages>64||!page.next().isEmpty()&&page.next().equals(warmupCursor))throw new IllegalArgumentException("Unbounded history snapshot");
            warmupCursor=page.next();
            if(warmupCursor.isEmpty()){
                warmupBatch=new HistoryBatch(List.copyOf(warmupRows.values()),owner,own);warmupRows.clear();
                warmupStatus="Shared history received; validating on the market worker";
                var watcher=dev.doughbay.fabric.DoughBayClient.watcher();if(watcher!=null)watcher.sharedHistoryReady();
            }
        }catch(Exception e){
            warmupRequested=Set.of();warmupRows.clear();warmupCursor="";warmupAsOf=0;nextWarmup=now+60_000;
            dev.doughbay.fabric.FaultTrace.INSTANCE.record(dev.doughbay.fabric.FaultTrace.Stage.COMMUNITY_HISTORY,e,System.currentTimeMillis());
            warmupStatus="Shared history unavailable; collecting locally";
            // Optional bootstrap failure never stops contributions, discovery or local trading.
        }
    }
    static String historyPath(Set<String> wanted,String cursor){
        // Omit since on every page: the service binds the frozen default window to the cursor.
        String path="sales/history?region=net-donut&limit=1000&items="+URLEncoder.encode(String.join(",",new TreeSet<>(wanted)),StandardCharsets.UTF_8);
        return cursor.isEmpty()?path:path+"&after="+URLEncoder.encode(cursor,StandardCharsets.UTF_8);
    }
    private void enroll()throws Exception{
        requestStage="enrollment";lastHttpStatus=0;
        if(!HostedDashboardClient.INSTANCE.view().linked()){status="Link this client to the dashboard first";return;}
        long own=generation.get();var body=DashboardProtocol.JSON.createObjectNode().put("schema",1).put("build",DashboardProtocol.build(build)).put("consent",CommunityWire.CONSENT);
        String enrolledAccount=account;
        body.putArray("observerAccounts").add(enrolledAccount);var response=HostedDashboardClient.INSTANCE.enrollCommunity(body).get(20,TimeUnit.SECONDS);
        lastHttpStatus=response.statusCode();
        if(own!=generation.get()||!enabled)return;
        if(response.statusCode()!=200&&response.statusCode()!=201){if(response.statusCode()==401||response.statusCode()==403){refuseCredential(response.statusCode());return;}if(response.statusCode()==429)serviceRetry.fail(System.currentTimeMillis(),retryAfter(response));status="Enrollment refused (HTTP "+response.statusCode()+"); local scanning continues";return;}
        var j=json(response);String secret=j.path("credential").asText(),id=j.path("contributorId").asText();
        if(!secret.matches("gnc1_[A-Za-z0-9_-]{43}")||!id.matches("c_[A-Za-z0-9_-]{12}"))throw new IllegalArgumentException();
        credential=secret;contributor=id;registeredAccount=enrolledAccount;save();enrolled=true;if(!enabled){unenroll();return;}status="Enrolled in Swarm; live checks retained";nextUpload=0;
    }
    private void unenroll()throws Exception{
        if(credential.isEmpty()){status="Off";return;}
        var response=request("POST","unenroll",null,true);if(response.statusCode()==204||response.statusCode()==401){credential="";contributor="";enrolled=false;save();status="Off";}
        else status="Off; waiting to remove server enrollment";
    }
    private void upload(long now)throws Exception{
        if(pending==null){
            final CommunityBatches.Packed packed;
            synchronized(this){packed=CommunityBatches.pack(queue,types,now);dropped+=packed.dropped();}
            var body=packed.body();if(body.path("records").isEmpty())return;
            pending=DashboardProtocol.JSON.createObjectNode().put("key",UUID.randomUUID().toString().replace("-","")).set("body",body);pendingReceipts=packed.receipts();store.write("pending.json",pending);
        }
        requestStage="observation upload";lastHttpStatus=0;
        long own=generation.get();var body=(ObjectNode)pending.path("body");body.put("sentAt",System.currentTimeMillis());
        var request=HttpRequest.newBuilder(CommunityWire.BASE.resolve("observations")).timeout(java.time.Duration.ofSeconds(10)).header("Authorization","Bearer "+credential)
                .header("Content-Type","application/json").header("Idempotency-Key",pending.path("key").asText()).POST(HttpRequest.BodyPublishers.ofString(body.toString())).build();
        dev.doughbay.fabric.SwarmActivity.sent();var response=http.send(request,HttpResponse.BodyHandlers.ofByteArray());if(response.statusCode()==200||response.statusCode()==202)dev.doughbay.fabric.SwarmActivity.received();if(!enabled||generation.get()!=own)return;
        int code=response.statusCode();lastHttpStatus=code;if(code>=400)lastServiceFailure=System.currentTimeMillis();
        if(code==200||code==202){var result=json(response);if(!result.path("replay").asBoolean())accepted+=result.path("accepted").asLong();rejected+=result.path("rejected").size();for(var failure:result.path("rejected")){String reason=failure.path("code").asText();if(reason.matches("[a-z_]{1,48}")&&rejectionCodes.size()<32)rejectionCodes.merge(reason,1L,Long::sum);}eligible=result.path("eligible").asBoolean();lastUpload=System.currentTimeMillis();lastUploadScope=body.path("region").asText();failures=0;status="Shadow active; local scanning unchanged";acknowledgeOrderBatch(CommunityOrderReceipt.successfulBatch(result,body.path("records").size()));clearPending();}
        else if(code==429){nextUpload=System.currentTimeMillis()+retryAfter(response);status="Upload rate limited; local scanning continues";}
        else if(code>=500){nextUpload=System.currentTimeMillis()+Math.min(600_000,10_000L<<Math.min(6,failures++));status="Upload retry pending; local scanning continues";}
        else if(code==401||code==403){refuseCredential(code);}
        else{rejected+=body.path("records").size();acknowledgeOrderBatch(false);clearPending();status="Upload refused (HTTP "+code+"); local scanning continues";}
    }
    private void clearPending()throws Exception{store.remove("pending.json");pending=null;}
    private void query(long now)throws Exception{
        if(!queryBackoff.allowed(now))return;
        String key;
        synchronized(this){if(queries.isEmpty())return;key=queries.iterator().next();queries.remove(key);}
        String region=marketScope();var response=request("GET","market/"+URLEncoder.encode(key,StandardCharsets.UTF_8)+(region.isBlank()?"":"?region="+URLEncoder.encode(region,StandardCharsets.UTF_8)),null,true);lastQuery=System.currentTimeMillis();
        if(response.statusCode()==429){synchronized(this){if(queries.size()<100)queries.add(key);}status="Market queries rate limited; local scanning continues";return;}
        if(response.statusCode()!=200)return;
        var result=json(response);lastQueryScope=region;long ttl=Math.min(30,Math.max(1,result.path("ttlSeconds").asLong(30)))*1000;
        if(cache.size()>=100)cache.entrySet().removeIf(e->e.getValue().expires()<now);
        if(cache.size()<100||cache.containsKey(key))cache.put(key,new Cached(result,lastQuery,lastQuery+ttl,region));
    }
    private void queryDiscovery()throws Exception {
        var keys=discoveryMarkets.stream().sorted().toList();if(keys.isEmpty()||!connected||!marketScope().equals("net-donut"))return;
        discoveryRefreshes++;
        var response=request("GET","market?items="+URLEncoder.encode(String.join(",",keys),StandardCharsets.UTF_8)+"&region=net-donut",null,true);
        lastQuery=System.currentTimeMillis();if(response.statusCode()!=200)return;
        var items=json(response).path("items");if(!items.isArray()||items.size()!=keys.size())return;
        var received=new HashSet<String>();
        for(var item:items) {
            String key=item.path("itemKey").asText();
            if(!keys.contains(key)||!received.add(key)||!item.path("regionScope").asText().equals("net-donut"))return;
        }
        long now=System.currentTimeMillis();cache.entrySet().removeIf(e->e.getValue().expires()<=now);
        for(var item:items) {
            String key=item.path("itemKey").asText();long ttl=Math.min(30,Math.max(1,item.path("ttlSeconds").asLong(30)))*1000;
            if(cache.size()<100||cache.containsKey(key))cache.put(key,new Cached(item,now,now+ttl,"net-donut"));
        }
        lastQueryScope="net-donut";discoveryResponses++;
    }
    private static long retryAfter(HttpResponse<?> response){try{return Math.min(3600,Math.max(10,Long.parseLong(response.headers().firstValue("Retry-After").orElse("60"))))*1000;}catch(Exception e){return 60_000;}}
}
