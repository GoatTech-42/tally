package dev.doughbay.fabric.hosted;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;

/** Passive, session-local discovery accounting. A skipped read has no measured counterfactual. */
public final class SwarmReadSavings {
    private record Sample(String region, int pages, long at, long millis) {}
    private final ArrayDeque<Sample> samples = new ArrayDeque<>();
    private long attempts, skipped, fallbacks, lookupMs, estimatedAvoidedMs, calibrated;
    private long calibratedLookupMs;
    private static final long TTL = 30 * 60_000;

    public synchronized void nativeRead(String region, int pages, long millis, long now) {
        if (region == null || region.isBlank() || pages <= 0 || millis <= 0) return;
        prune(now);
        while (samples.size() >= 100) samples.removeFirst();
        samples.addLast(new Sample(region, pages, now, millis));
    }

    /** Called once per completed shared lookup, including rejected snapshots and timeouts. */
    public synchronized void lookup(boolean reused, String region, int pages, long millis, long now) {
        if (millis < 0) return;
        attempts++; lookupMs += millis;
        if (!reused) { fallbacks++; return; }
        skipped++;
        prune(now);
        var matching = samples.stream().filter(s -> s.region().equals(region) && s.pages() == pages)
                .mapToLong(Sample::millis).sorted().toArray();
        if (matching.length < 3) return;
        // Freeze the estimate at the decision, never backfill old skips from future evidence.
        long median = matching[(matching.length - 1) / 2];
        estimatedAvoidedMs += median; calibratedLookupMs += millis; calibrated++;
    }

    private void prune(long now) {
        samples.removeIf(s -> s.at() > now || now - s.at() > TTL);
    }

    public synchronized ObjectNode view() {
        var out = DashboardProtocol.JSON.createObjectNode().put("attempts", attempts)
                .put("actualSkippedReads", skipped).put("fallbackReads", fallbacks)
                .put("lookupWaitMs", lookupMs).put("calibratedSkippedReads", calibrated)
                .put("uncalibratedSkippedReads", skipped - calibrated)
                .put("estimatedAvoidedReadMs", estimatedAvoidedMs)
                .put("estimatedNetSavedMs", estimatedAvoidedMs - lookupMs)
                .put("estimateAvailable", calibrated > 0).put("estimateComplete", skipped > 0 && skipped == calibrated)
                .put("baselineWindowMinutes", 30).put("minimumBaselineReads", 3)
                .put("scope", "this client session; broad discovery only")
                .put("causalSpeedupProven", false);
        if (attempts > 0) out.put("skipPercent", 100.0 * skipped / attempts);
        if (calibrated > 0 && calibratedLookupMs > 0)
            out.put("estimatedMatchedReadSpeedRatio", (double) estimatedAvoidedMs / calibratedLookupMs);
        return out;
    }

    public static List<String> lines(com.fasterxml.jackson.databind.JsonNode v) {
        var rows = new ArrayList<String>();
        rows.add("Scans skipped: " + v.path("actualSkippedReads").asLong() + " / " + v.path("attempts").asLong()
                + " shared lookups; fallbacks: " + v.path("fallbackReads").asLong());
        rows.add("Measured shared lookup wait: " + seconds(v.path("lookupWaitMs").asLong()));
        if (v.path("estimateAvailable").asBoolean()) {
            rows.add("Estimated net time saved" + (v.path("estimateComplete").asBoolean()?"":" (partial baseline)") + ": " + seconds(v.path("estimatedNetSavedMs").asLong())
                    + " (includes all lookup waits; negative means overhead)");
            rows.add("Calibrated skips: " + v.path("calibratedSkippedReads").asLong() + "; without baseline: "
                    + v.path("uncalibratedSkippedReads").asLong());
        } else rows.add("Time saved: not calibrated yet; needs 3 recent successful scans of matching region and page count.");
        if (v.has("estimatedMatchedReadSpeedRatio")) rows.add(String.format(Locale.ROOT,
                "Estimated matched discovery speed: %.2fx (1x = equal; below 1x = slower)",
                v.path("estimatedMatchedReadSpeedRatio").asDouble()));
        rows.add("Ratio covers calibrated successful lookups only; net estimate subtracts fallback waits too. This client session only; not a controlled speed or profit claim. Live trade checks still run.");
        return List.copyOf(rows);
    }

    private static String seconds(long millis) { return String.format(Locale.ROOT, "%.1fs", millis / 1000.0); }
}
