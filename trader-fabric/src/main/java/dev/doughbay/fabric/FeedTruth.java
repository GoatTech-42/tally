package dev.doughbay.fabric;


/**
 * What the market-feed badge may honestly claim. A fresh snapshot alone is
 * not live market data: the keyless loop republishes a fresh snapshot on
 * every tick even when nothing has been observed, so freshness earns a
 * "live" badge only on the API scan path. Presentation only; this changes
 * no gating, permission, or automation behavior.
 */
enum FeedTruth {
    DEMO, STOPPED, STARTING, STALE, RETRYING, COLLECTING, LOCAL_HISTORY, DEGRADED, PARTIAL, LIVE_API;

    /** Same window DoughBayScreen.isFresh has always used. */
    private static final long FRESHNESS_MILLIS = 120_000L;

    static FeedTruth of(MarketWatcher.Snapshot snapshot, long now) {
        if (snapshot.demo()) return DEMO;
        String status = snapshot.status() == null ? "" : snapshot.status();
        if (status.startsWith("STOPPED")) return STOPPED;
        if (snapshot.updatedAt() <= 0) return STARTING;
        if (now - snapshot.updatedAt() > FRESHNESS_MILLIS) return STALE;
        // Status text replaces a still-fresh snapshot's standing when the
        // watcher is between scans, so a heartbeat or a mode switch never
        // earns "live": retrying after a fault, keyless startup before the
        // first keyless publish, and bootstrap work are not completed scans.
        if (status.startsWith("RETRYING")) return RETRYING;
        if (status.startsWith("Bootstrapping")) return STARTING;
        if (status.startsWith("KEYLESS:") || status.startsWith("Starting keyless mode")) {
            return snapshot.markets().isEmpty() ? COLLECTING : LOCAL_HISTORY;
        }
        if (status.startsWith("DEGRADED:")) return DEGRADED;
        if (status.startsWith("PARTIAL:")) return PARTIAL;
        return LIVE_API;
    }

    static boolean live(MarketWatcher.Snapshot snapshot, long now) {
        return of(snapshot, now) == LIVE_API;
    }

    /** Short kvRight label; the header maps its own uppercase wording. */
    static String label(MarketWatcher.Snapshot snapshot, long now, String demoLabel, String liveLabel) {
        return switch (of(snapshot, now)) {
            case DEMO -> demoLabel;
            case STOPPED -> "stopped";
            case STARTING -> "starting";
            case STALE -> "stale";
            case RETRYING -> "retrying";
            case COLLECTING -> "collecting";
            case LOCAL_HISTORY -> "local history";
            case DEGRADED -> "degraded";
            case PARTIAL -> "partial scan";
            case LIVE_API -> liveLabel;
        };
    }
}
