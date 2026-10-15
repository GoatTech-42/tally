package dev.doughbay.fabric;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Bounded install-local retry and suppression metadata. Call only off the game thread. */
public final class AutomaticReportJournal {
    private static final long DAY = 86_400_000L, RETENTION = 7 * DAY;
    private static final ObjectMapper JSON = new ObjectMapper();
    public enum Outcome { QUEUED, SENT, EXHAUSTED, CANCELLED }
    public record Entry(String fingerprint, String retryKey, long reservedAt, long sentAt, Outcome outcome) {
        public Entry {
            if (fingerprint == null || !fingerprint.matches("[a-f0-9]{64}") || retryKey == null
                    || !retryKey.matches("[A-Za-z0-9_-]{8,64}") || reservedAt < 0 || sentAt < 0 || outcome == null)
                throw new IllegalArgumentException("Invalid automatic-report journal entry");
            if ((outcome == Outcome.SENT) != (sentAt > 0))
                throw new IllegalArgumentException("Invalid automatic-report receipt state");
        }
    }
    private final Path path;
    public AutomaticReportJournal(Path path) { this.path = Objects.requireNonNull(path); }
    public synchronized List<Entry> entries(long now) throws IOException { return List.copyOf(read(now)); }
    /** A queued report reuses its UUID across restarts. Sent reports are suppressed for 24 hours. */
    public synchronized Optional<Entry> reserve(String fingerprint, long now) throws IOException {
        List<Entry> entries = read(now);
        for (Entry entry : entries) if (entry.fingerprint().equals(fingerprint)) {
            if (entry.outcome() == Outcome.QUEUED) return Optional.of(entry);
            if (entry.outcome() == Outcome.SENT && now - entry.sentAt() < DAY) return Optional.empty();
            if (entry.outcome() == Outcome.EXHAUSTED && now - entry.reservedAt() < DAY) return Optional.empty();
        }
        // Count reservations, not just successes: outages and re-preparation cannot bypass the local cap.
        if (entries.stream().filter(e -> now - e.reservedAt() < DAY).count() >= 3) return Optional.empty();
        Entry next = new Entry(fingerprint, UUID.randomUUID().toString(), now, 0, Outcome.QUEUED);
        entries.add(next); write(entries); return Optional.of(next);
    }
    public synchronized void finish(String retryKey, Outcome outcome, long now) throws IOException {
        if (outcome == Outcome.QUEUED) throw new IllegalArgumentException("A terminal outcome is required");
        List<Entry> entries = read(now);
        boolean found = false;
        for (int i=0;i<entries.size();i++) {
            Entry prior = entries.get(i);
            if (prior.retryKey().equals(retryKey)) {
                if (prior.outcome() != Outcome.QUEUED) return;
                entries.set(i,new Entry(prior.fingerprint(),retryKey,prior.reservedAt(),
                    outcome == Outcome.SENT ? now : 0,outcome)); found=true; break;
            }
        }
        if (!found) throw new IOException("Automatic-report reservation is missing");
        write(entries);
    }
    private List<Entry> read(long now) throws IOException {
        if (!Files.exists(path)) return new ArrayList<>();
        if (Files.size(path)>65536) throw new IOException("Automatic-report journal is too large");
        try {
            var root=JSON.readTree(Files.readAllBytes(path));
            if (!root.isArray() || root.size()>64) throw new IOException("Invalid automatic-report journal");
            List<Entry> entries=new ArrayList<>(); Set<String> retryKeys=new HashSet<>(); Set<String> queued=new HashSet<>();
            for (var node:root) {
                Entry entry=JSON.treeToValue(node,Entry.class);
                if (!retryKeys.add(entry.retryKey()) || entry.reservedAt()>now+300_000 || entry.sentAt()>now+300_000
                        || entry.outcome()==Outcome.QUEUED && !queued.add(entry.fingerprint())
                        || entry.outcome()==Outcome.SENT && entry.sentAt()<entry.reservedAt())
                    throw new IOException("Invalid automatic-report journal chronology");
                if (now-entry.reservedAt()<RETENTION) entries.add(entry);
            }
            return entries;
        } catch (RuntimeException e) { throw new IOException("Invalid automatic-report journal",e); }
    }
    private void write(List<Entry> entries) throws IOException {
        if (entries.size()>64) throw new IOException("Automatic-report journal is full");
        Path parent=path.toAbsolutePath().getParent(); Files.createDirectories(parent);
        Path temporary=Files.createTempFile(parent,"auto-report-",".tmp");
        try {
            Files.write(temporary,JSON.writeValueAsBytes(entries));
            try { Files.move(temporary,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temporary,path,StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
    }
}
