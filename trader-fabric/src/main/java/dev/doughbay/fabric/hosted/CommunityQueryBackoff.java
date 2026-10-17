package dev.doughbay.fabric.hosted;
import java.util.concurrent.atomic.AtomicLong;
/** Shared by background market queries and snapshot validation; uploads have a separate budget. */
final class CommunityQueryBackoff {
    private final AtomicLong until = new AtomicLong();
    boolean allowed(long now) { return now >= until.get(); }
    long until() { return until.get(); }
    void defer(long now, String retryAfter) {
        long seconds;
        try { seconds = Math.max(1, Math.min(3600, Long.parseLong(retryAfter.strip()))); }
        catch (RuntimeException invalid) { seconds = 60; }
        long delay = seconds * 1000;
        long deadline = now > Long.MAX_VALUE-delay ? Long.MAX_VALUE : now+delay;
        until.accumulateAndGet(deadline, Math::max);
    }
    static boolean query(String method,String path) {
        return method.equals("GET") && (path.startsWith("market/") || path.startsWith("market?") || path.equals("markets")
            || path.startsWith("markets?") || path.startsWith("orders/"));
    }
}
