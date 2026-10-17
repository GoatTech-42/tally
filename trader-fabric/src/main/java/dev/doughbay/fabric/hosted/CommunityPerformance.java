package dev.doughbay.fabric.hosted;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.UUID;

/** Local measurement only. Candidate milliseconds are hypothetical, never actual savings. */
public final class CommunityPerformance {
    public static final CommunityPerformance INSTANCE = new CommunityPerformance();
    public record Read(long beganNanos, boolean probe, double candidatePrice, String region) {}
    private final String session = UUID.randomUUID().toString();
    private final long beganAt = System.currentTimeMillis();
    private long connectedMs, lastTick;
    private boolean wasConnected;
    private long probeMs, orderMs, probes, sweeps, failedProbes, failedSweeps, pages, rows;
    private long candidateMs, candidates;
    private boolean enabled;

    public synchronized void enable() { enabled = true; }
    public synchronized void tick(boolean connected, long nanos) {
        if (!enabled) return;
        // Do not count offline gaps or a stopped/frozen game thread as connected time.
        if (wasConnected && connected && lastTick != 0) {
            long elapsed = (nanos - lastTick) / 1_000_000;
            if (elapsed > 0 && elapsed <= 5_000) connectedMs += elapsed;
        }
        wasConnected = connected;
        lastTick = nanos;
    }
    public synchronized Read begin(boolean probe, double candidatePrice, String region, long nanos) {
        return enabled ? new Read(nanos, probe, candidatePrice, region) : null;
    }
    public synchronized void end(Read read, boolean succeeded, double livePrice,
                                  String region, int pageCount, int rowCount, long nanos) {
        if (read == null) return;
        long elapsed = Math.max(0, (nanos - read.beganNanos()) / 1_000_000);
        if (read.probe()) {
            probeMs += elapsed;
            if (succeeded) probes++; else failedProbes++;
            if (succeeded && read.candidatePrice() > 0 && Double.isFinite(livePrice)
                    && livePrice > 0 && !read.region().isBlank() && read.region().equals(region)
                    && Math.abs(read.candidatePrice() - livePrice) / livePrice <= .10) {
                candidates++; candidateMs += elapsed;
            }
        } else {
            orderMs += elapsed;
            if (succeeded) sweeps++; else failedSweeps++;
            pages += Math.max(0, pageCount); rows += Math.max(0, rowCount);
        }
    }
    public synchronized ObjectNode view() {
        return DashboardProtocol.JSON.createObjectNode().put("schema", 1).put("session", session)
                .put("startedAt", beganAt).put("observedAt", System.currentTimeMillis())
                .put("connectedMs", connectedMs).put("probeReadMs", probeMs).put("orderReadMs", orderMs)
                .put("completedProbes", probes).put("failedProbes", failedProbes)
                .put("completedOrderReads", sweeps).put("failedOrderReads", failedSweeps)
                .put("orderPages", pages).put("orderRows", rows)
                .put("candidateProbes", candidates).put("hypotheticalCandidateMs", candidateMs)
                .put("actualSkippedReads", 0).put("actualSavedMs", 0);
    }
}
