package dev.doughbay.fabric.automation;

/**
 * Retry own-order reads with backoff, without hiding them behind a long auction watch.
 *
 * <p>The first two failures are retried at once, because a lost click is the
 * usual cause and the order house is where the stock comes from. From the
 * third the order house is taken to be down, as it was for hours on
 * 2026-10-10 and again with the server at its fullest on 2026-10-11, and the
 * gaps lengthen to four minutes. During those gaps the desk steps aside: the
 * session lists what it holds, reprices and watches the auction house
 * instead of standing at a page that is not coming.
 */
final class OrderDeskRecovery {
    /** Failures before the order house is treated as down rather than as having dropped a click. */
    static final int DOWN_AFTER = 3;
    private int failures;
    private long retryAt;

    void failed(long now) {
        failures = Math.min(5, failures + 1);
        retryAt = now + switch (failures) {
            case 1 -> 5_000L;
            case 2 -> 15_000L;
            case 3 -> 60_000L;
            case 4 -> 120_000L;
            default -> 240_000L;
        };
    }

    /** Between tries at an order house that is down: nothing else should be kept waiting on it. */
    boolean steppedAside(long now) {
        return failures >= DOWN_AFTER && now < retryAt;
    }

    void recovered() { failures = 0; retryAt = 0; }
    boolean pending() { return failures > 0; }
    boolean ready(long now) { return pending() && now >= retryAt; }
    long remainingSeconds(long now) { return Math.max(0, (retryAt - now + 999) / 1000); }

    static boolean navigationFailure(String detail) {
        if (detail == null) return false;
        String text = detail.toLowerCase(java.util.Locale.ROOT);
        return text.contains("your orders did not open") || text.contains("your orders could not be clicked")
                || text.contains("no your orders control") || text.contains("no progress at stage 0")
                || text.contains("no progress at stage 1 of") || text.contains("orderstage=0")
                || text.contains("orderstage=1,") || text.contains("order house did not open")
                // Opening the order row failed before Collect Items or any stock transfer.
                // Fully delivered rows cannot gain newer deliveries to rearm an exhausted stock budget.
                || text.contains("edit order did not open") || text.contains("collect items did not open")
                || text.contains("owned order rows changed before collection");
    }
}
