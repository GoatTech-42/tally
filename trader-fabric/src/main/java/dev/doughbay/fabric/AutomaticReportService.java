package dev.doughbay.fabric;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.*;
import java.nio.channels.FileChannel;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.LongSupplier;

/** Tick publishes facts only; this daemon owns disk, filtering, capabilities and delivery. */
public final class AutomaticReportService implements AutoCloseable {
    interface Gateway {
        JsonNode capabilities() throws Exception;
        BugReportTransport.AutomaticResult send(byte[] body,String key) throws Exception;
    }
    interface Evidence { Prepared capture(AutomaticReportMetadata metadata) throws Exception; }
    record Prepared(BugReportPayload payload,String log) {}
    private record Sample(AutomaticReportDetector.Observation observation, AutomaticReportDetector.Candidate candidate, long observedAt) {}
    private final AutomaticReportDetector detector=new AutomaticReportDetector();
    private final AutomaticReportJournal journal;
    private final AutomaticReportOutbox outbox;
    private final Path lockFile;
    private final String build,minecraft;
    private final Gateway gateway;
    private final Evidence evidence;
    private final LongSupplier wallClock;
    private final ScheduledExecutorService daemon;
    private volatile Sample current;
    private volatile boolean closed;
    private volatile String status="Automatic critical reports: off";
    private boolean storageFailed;
    private long nextCapabilityCheck;

    public AutomaticReportService(Path gameRoot,String build) {
        this(gameRoot,build,"26.2",new Gateway() {
            private final BugReportTransport transport=new BugReportTransport();
            public JsonNode capabilities() throws Exception {return transport.automaticCapabilities();}
            public BugReportTransport.AutomaticResult send(byte[] body,String key) throws Exception {return transport.sendAutomatic(body,key);}
        },metadata -> {
            var log=gameRoot.resolve("logs/latest.log");
            return new Prepared(new BugReportPayload(build,"26.2",metadata.description(),metadata.expected(),
                BugReportDiagnostics.readCounters(log)),BugReportLog.read(log));
        },System::currentTimeMillis,true);
    }
    AutomaticReportService(Path gameRoot,String build,String minecraft,Gateway gateway,Evidence evidence,LongSupplier clock,boolean startDaemon) {
        this.build=build;this.minecraft=minecraft;this.gateway=gateway;this.evidence=evidence;this.wallClock=clock;
        Path directory=gameRoot.resolve("gonuts-reports");
        journal=new AutomaticReportJournal(directory.resolve("journal.json"));
        outbox=new AutomaticReportOutbox(directory.resolve("outbox.json"));
        lockFile=directory.resolve("delivery.lock");
        daemon=startDaemon?Executors.newSingleThreadScheduledExecutor(r -> {var thread=new Thread(r,"GoNuts-critical-reports");thread.setDaemon(true);return thread;}):null;
        if(daemon!=null) daemon.scheduleWithFixedDelay(this::process,5,5,TimeUnit.SECONDS);
    }
    public void observe(AutomaticReportDetector.Observation observation,long elapsedMillis) {
        current=new Sample(observation,detector.observe(observation,elapsedMillis).orElse(null),wallClock.getAsLong());
        if(!observation.optedIn()) status="Automatic critical reports: off";
    }
    public String status() {return status;}
    private boolean consented() {var sample=current;return !closed && sample!=null && sample.observation().optedIn();}
    private boolean connected() {
        var sample=current;
        return consented() && sample.observation().connected() && wallClock.getAsLong()-sample.observedAt()>=0
            && wallClock.getAsLong()-sample.observedAt()<15_000;
    }
    private boolean matches(AutomaticReportOutbox.Pending pending) {
        var sample=current;
        return connected() && sample.candidate()!=null && AutomaticReportMetadata.of(build,minecraft,sample.candidate()).fingerprint().equals(pending.fingerprint());
    }
    /** Serialized by the single daemon and an install-local process lock. No submissions if storage is unsafe. */
    void process() {
        if(!consented() || storageFailed) return;
        try {
            Files.createDirectories(lockFile.getParent());
            try(var channel=FileChannel.open(lockFile,StandardOpenOption.CREATE,StandardOpenOption.WRITE);
                var lock=channel.tryLock()) {
                if(lock==null) return;
                deliver();
            }
        } catch(java.nio.channels.OverlappingFileLockException ignored) {
            // Another client using the same game directory owns delivery.
        } catch(Exception e) {
            storageFailed=true;
            status="Automatic reports paused: local state unavailable; no report sent";
        }
    }
    private void deliver() throws Exception {
        long now=wallClock.getAsLong();
        var loaded=outbox.load(now);
        AutomaticReportOutbox.Pending pending=loaded.orElse(null);
        if(pending!=null) {
            var entry=journal.entries(now).stream().filter(e -> e.retryKey().equals(pendingKey(loaded))).findFirst()
                .orElseThrow(() -> new java.io.IOException("Missing report reservation"));
            if(!entry.fingerprint().equals(pending.fingerprint()) || entry.reservedAt()!=pending.reservedAt())
                throw new java.io.IOException("Outbox reservation mismatch");
            if(entry.outcome()!=AutomaticReportJournal.Outcome.QUEUED) {outbox.clear();return;}
            if(pending.attempts()==0 && !matches(pending)) {
                journal.finish(pending.retryKey(),AutomaticReportJournal.Outcome.CANCELLED,now);outbox.clear();return;
            }
            if(now-pending.reservedAt()>=86_400_000 || pending.attempts()>=5) {
                journal.finish(pending.retryKey(),AutomaticReportJournal.Outcome.EXHAUSTED,now);outbox.clear();
                status="Automatic report retries exhausted; repeated incident suppressed";return;
            }
            if(!connected() || now<pending.nextAttemptAt()) return;
        }
        if(!connected() || now<nextCapabilityCheck) return;
        var sample=current;
        if(pending==null && sample.candidate()==null) {status="Automatic reports ready: no critical failure";return;}
        JsonNode capabilities;
        try {capabilities=gateway.capabilities();}
        catch(Exception e) {nextCapabilityCheck=now+300_000;status="Automatic reports waiting for receiver support";return;}
        sample=current;
        var kind=pending!=null?pending.kind():sample.candidate()==null?null:sample.candidate().failure();
        if(kind==null || !BugReportTransport.advertisesAutomaticReports(capabilities,kind)
            || !BugReportTransport.advertisesLogSupport(capabilities)) {
            nextCapabilityCheck=now+300_000;status="Automatic reports waiting for receiver support";return;
        }
        if(pending==null) {
            var candidate=current.candidate();
            if(!connected() || candidate==null) return;
            var metadata=AutomaticReportMetadata.of(build,minecraft,candidate);
            // Never replace a missing frozen body for an older reservation: it may have reached the receiver.
            if(journal.entries(now).stream().anyMatch(e -> e.outcome()==AutomaticReportJournal.Outcome.QUEUED))
                throw new java.io.IOException("Incomplete report outbox");
            Prepared prepared;
            try {prepared=evidence.capture(metadata);}
            catch(Exception e) {nextCapabilityCheck=now+300_000;status="Automatic report waiting for sanitized diagnostics";return;}
            var reservation=journal.reserve(metadata.fingerprint(),now);
            if(reservation.isEmpty()) {nextCapabilityCheck=now+300_000;status="Automatic report suppressed by duplicate or daily limit";return;}
            var entry=reservation.get();
            pending=new AutomaticReportOutbox.Pending(entry.retryKey(),entry.fingerprint(),
                new String(prepared.payload().json(prepared.log(),metadata),java.nio.charset.StandardCharsets.UTF_8),now,0,0);
            outbox.save(pending);
        }
        // Recovery or opt-out during capability/evidence preparation prevents the first upload.
        if(!connected() || pending.attempts()==0 && !matches(pending)) return;
        pending=pending.attempted(now);outbox.save(pending);
        if(!consented()) return;
        BugReportTransport.AutomaticResult receipt;
        try {receipt=gateway.send(pending.bytes(),pending.retryKey());}
        catch(Exception e) {
            outbox.save(pending.delayed(now+Math.min(3_600_000,60_000L<<(pending.attempts()-1))));
            status="Automatic report waiting to retry the same request";return;
        }
        if(receipt.result().filed()) {
            journal.finish(pending.retryKey(),AutomaticReportJournal.Outcome.SENT,wallClock.getAsLong());outbox.clear();
            status="Automatic critical report received: "+receipt.result().issueUrl();
        } else if(receipt.permanent()) {
            journal.finish(pending.retryKey(),AutomaticReportJournal.Outcome.EXHAUSTED,wallClock.getAsLong());outbox.clear();
            status="Automatic report rejected; retries stopped";
        } else {
            outbox.save(pending.delayed(Math.max(receipt.result().retryAtMillis(),now+Math.min(3_600_000,60_000L<<(pending.attempts()-1)))));
            status="Automatic report waiting to retry the same request";
        }
    }
    private static String pendingKey(Optional<AutomaticReportOutbox.Pending> loaded) {return loaded.orElseThrow().retryKey();}
    @Override public void close() {closed=true;if(daemon!=null)daemon.shutdownNow();}
}
