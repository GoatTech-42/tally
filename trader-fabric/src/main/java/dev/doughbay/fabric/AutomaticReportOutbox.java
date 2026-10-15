package dev.doughbay.fabric;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.util.*;

/** One bounded, install-local frozen request. No raw logs or account identity are retained. */
public final class AutomaticReportOutbox {
    private static final ObjectMapper JSON=new ObjectMapper();
    private final Path file;
    public AutomaticReportOutbox(Path file) {this.file=Objects.requireNonNull(file);}
    public record Pending(String retryKey, String fingerprint, String body, long reservedAt, int attempts, long nextAttemptAt) {
        public Pending {
            if(retryKey==null || !retryKey.matches("[A-Za-z0-9_-]{8,64}") || fingerprint==null
                || !fingerprint.matches("[a-f0-9]{64}") || body==null || body.getBytes(StandardCharsets.UTF_8).length>32768
                || reservedAt<0 || attempts<0 || attempts>5 || nextAttemptAt<0)
                throw new IllegalArgumentException("Invalid automatic outbox");
            validateBody(body,fingerprint);
        }
        public byte[] bytes() {return body.getBytes(StandardCharsets.UTF_8);}
        public AutomaticReportDetector.Failure kind() {
            try {return AutomaticReportDetector.Failure.valueOf(JSON.readTree(body).path("automatic").path("kind").asText());}
            catch(IOException e) {throw new IllegalArgumentException("Invalid automatic outbox");}
        }
        public Pending attempted(long now) {return new Pending(retryKey,fingerprint,body,reservedAt,attempts+1,now+60_000);}
        public Pending delayed(long until) {return new Pending(retryKey,fingerprint,body,reservedAt,attempts,until);}
    }
    static void validateBody(String body,String fingerprint) {
        try {
            var root=JSON.readTree(body);
            if(root==null || !root.isObject() || root.path("schemaVersion").asInt()!=3) throw new IllegalArgumentException();
            var auto=root.path("automatic");
            var candidate=new AutomaticReportDetector.Candidate(
                AutomaticReportDetector.Failure.valueOf(auto.path("kind").asText()),
                AutomaticReportDetector.Signature.valueOf(auto.path("signature").asText()));
            var metadata=AutomaticReportMetadata.of(root.path("build").asText(),root.path("minecraftVersion").asText(),candidate);
            if(!metadata.fingerprint().equals(fingerprint)) throw new IllegalArgumentException();
            Map<String,Object> counters=JSON.convertValue(root.path("diagnostics"),new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>() {});
            String log=root.path("diagnosticLog").asText("");
            if(!log.isEmpty() && !validExcerpt(log)) throw new IllegalArgumentException();
            var payload=new BugReportPayload(root.path("build").asText(),root.path("minecraftVersion").asText(),
                root.path("description").asText(),root.path("expected").asText(),counters);
            if(!JSON.readTree(payload.json(log,metadata)).equals(root)) throw new IllegalArgumentException();
        } catch(Exception e) {throw new IllegalArgumentException("Invalid or unsafe automatic outbox");}
    }
    /** Validate regenerated lines again after a restart; never trust a locally edited excerpt. */
    static boolean validExcerpt(String log) {
        String prefix=BugReportLog.sanitize("");
        if(!log.startsWith(prefix) || log.getBytes(StandardCharsets.UTF_8).length>8192) return false;
        String events=log.substring(prefix.length());
        if(events.isEmpty()) return true;
        if(events.split("\n",-1).length>100) return false;
        for(String line:events.split("\n",-1)) {
            if(line.length()>500) return false;
            if(line.startsWith("  at ")) {
                if(!BugReportLog.sanitize(line).equals(prefix+line)) return false;
                continue;
            }
            if(!line.matches("(?:\\[\\d{2}:\\d{2}:\\d{2}\\] )?(?:automation state=(?:SCANNING|BID_DESK|PREPARING_LIST|LISTING|MONITORING|COOLDOWN|PAUSED|STOPPED|IDLE|BUYING|REPRICING)|order action did not complete|connection ended \\(reason omitted\\)|evasion escape triggered \\(identity/location omitted\\)|restart recovery reported limbo|exception type=(?:SQLiteException|IllegalStateException|IllegalArgumentException|NullPointerException|IOException|SocketException|TimeoutException|ConnectException)(?: code=SQLITE_(?:BUSY_SNAPSHOT|BUSY|LOCKED|ERROR|CONSTRAINT|CORRUPT|IOERR|FULL|READONLY))?)")) return false;
        }
        return true;
    }
    public Optional<Pending> load(long now) throws IOException {
        if(!Files.exists(file)) return Optional.empty();
        if(Files.size(file)>65536) throw new IOException("Automatic outbox too large");
        try {
            Pending pending=JSON.readValue(Files.readAllBytes(file),Pending.class);
            if(pending.reservedAt()>now+300_000 || pending.nextAttemptAt()>now+86_700_000) throw new IOException("Invalid outbox chronology");
            return Optional.of(pending);
        } catch(RuntimeException e) {throw new IOException("Invalid automatic outbox",e);}
    }
    public void save(Pending pending) throws IOException {
        Files.createDirectories(file.toAbsolutePath().getParent());
        Path fresh=Files.createTempFile(file.toAbsolutePath().getParent(),"report-outbox-",".tmp");
        try {
            Files.write(fresh,JSON.writeValueAsBytes(pending));
            try {Files.move(fresh,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
            catch(AtomicMoveNotSupportedException e) {Files.move(fresh,file,StandardCopyOption.REPLACE_EXISTING);}
        } finally {Files.deleteIfExists(fresh);}
    }
    public void clear() throws IOException {Files.deleteIfExists(file);}
}
