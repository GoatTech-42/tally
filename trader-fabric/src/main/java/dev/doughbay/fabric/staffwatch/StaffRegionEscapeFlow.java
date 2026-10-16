package dev.doughbay.fabric.staffwatch;

import java.util.List;
import java.util.function.LongSupplier;

/** One fresh local alert -> hop; same identity within two minutes of arrival -> disconnect. */
public final class StaffRegionEscapeFlow {
    public interface Client {
        boolean connected();
        boolean pauseSafely();
        List<String> regions();
        String regionHint();
        boolean requestRegion(String region);
        boolean commandSent();
        boolean transferConfirmed();
        void clearDetections();
        void resume();
        void release();
        void disconnect(String watcher, String reason);
        void notice(String watcher, String detail);
    }
    public enum Phase { IDLE, PREPARING_FIRST, TRANSFERRING_FIRST, WATCHING }
    private static final long PREPARE_TIMEOUT = 15_000, TRANSFER_TIMEOUT = 30_000;
    public static final long WATCH_WINDOW = 2 * 60_000;
    private final java.util.Map<String,Long> watchUntil = new java.util.HashMap<>();
    private final Client client;
    private final LongSupplier clock;
    private Phase phase = Phase.IDLE;
    private String watcher = "", lastRegion = "";
    private long deadline;
    public StaffRegionEscapeFlow(Client client, LongSupplier clock) {
        this.client = client; this.clock = clock;
    }
    public Phase phase() { return phase; }
    public boolean ownsConnection() { return phase != Phase.IDLE && phase != Phase.WATCHING; }
    public boolean alert(String name, boolean enabled) {
        if (!enabled || name == null || name.isBlank() || !client.connected()) return false;
        if (phase != Phase.IDLE && phase != Phase.WATCHING) return false; // In-flight duplicates never escalate.
        expireWatchers();
        if (watchUntil.containsKey(name.toLowerCase(java.util.Locale.ROOT))) {
            watcher = name;
            fallback("Same staff detected in the new region within two minutes; disconnecting for 5–10 minutes");
            return true;
        }
        watcher = name;
        phase = Phase.PREPARING_FIRST;
        deadline = clock.getAsLong() + PREPARE_TIMEOUT;
        client.notice(watcher, "Preparing a safe region change");
        return true;
    }
    public void tick(boolean enabled) {
        if (!enabled) { cancel(); return; }
        if (phase == Phase.IDLE) return;
        if (phase == Phase.WATCHING) {
            if (!client.connected()) cancel();
            else expireWatchers();
            return;
        }
        if (!client.connected()) { fallback("Connection lost during region escape"); return; }
        if (clock.getAsLong() >= deadline) { fallback("Region escape timed out"); return; }
        switch (phase) {
            case PREPARING_FIRST -> {
                if (!client.pauseSafely()) return;
                String exclude = lastRegion.isBlank() ? client.regionHint() : lastRegion;
                String next = chooseRegion(client.regions(), exclude);
                if (next.isEmpty()) return; // Discovery may still be pending; timeout is bounded.
                client.clearDetections();
                if (!client.requestRegion(next)) { fallback("Region command unavailable or blocked"); return; }
                lastRegion = next;
                phase = Phase.TRANSFERRING_FIRST;
                deadline = clock.getAsLong() + TRANSFER_TIMEOUT;
                client.notice(watcher, "Changing region: " + next);
            }
            case TRANSFERRING_FIRST -> {
                if (!client.commandSent() || !client.transferConfirmed()) return;
                client.clearDetections(); // Accept only fresh post-arrival probes and observations.
                phase = Phase.WATCHING;
                deadline = clock.getAsLong() + WATCH_WINDOW;
                // Retain other identities: a different staff alert must not erase this one's window.
                if (watchUntil.size() >= 128) {
                    fallback("Too many pending staff follow checks; disconnecting conservatively");
                    return;
                }
                watchUntil.put(watcher.toLowerCase(java.util.Locale.ROOT), deadline);
                client.notice(watcher, "Region transfer confirmed; two-minute follow check started");
                client.resume();
            }
            default -> { }
        }
    }
    private void expireWatchers() {
        long now = clock.getAsLong();
        watchUntil.values().removeIf(until -> now >= until);
        if (phase == Phase.WATCHING && watchUntil.isEmpty()) {
            phase = Phase.IDLE; watcher = ""; deadline = 0;
        }
    }
    static String chooseRegion(List<String> regions, String exclude) {
        if (regions == null) return "";
        for (String candidate : List.of("west", "east", "asia", "eu west", "eu central", "oceania"))
            if (!candidate.equalsIgnoreCase(exclude) && regions.contains(candidate)) return candidate;
        return "";
    }
    private void fallback(String reason) {
        String who = watcher;
        client.notice(who, reason);
        // Release only after disconnect has taken ownership; never resume trading here.
        client.disconnect(who, reason);
        cancel();
    }
    public void cancel() {
        phase = Phase.IDLE; watcher = ""; lastRegion = ""; deadline = 0; watchUntil.clear();
        client.release();
    }
}
