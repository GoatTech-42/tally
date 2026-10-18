package dev.doughbay.fabric.hosted;

/** Transport failures pause the entire pump, rather than retrying registration every second. */
final class CommunityServiceRetry {
    private long until;
    private int failures;
    boolean ready(long now) { return now >= until; }
    void success() { failures = 0; }
    long fail(long now, long requestedDelay) {
        long delay = Math.max(Math.min(3_600_000, requestedDelay),
                Math.min(600_000, 10_000L << Math.min(6, failures++)));
        until = now + delay;
        return until;
    }
    static String reason(Exception error) {
        if (error instanceof java.net.http.HttpTimeoutException
                || error instanceof java.util.concurrent.TimeoutException) return "request timed out";
        if (error instanceof javax.net.ssl.SSLException) return "secure connection failed";
        if (error instanceof java.io.IOException) return "connection failed";
        return "response or local storage unavailable";
    }
}
