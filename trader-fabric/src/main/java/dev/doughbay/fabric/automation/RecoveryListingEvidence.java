package dev.doughbay.fabric.automation;

/** Chronology for explicitly requested, complete in-game own-listing recovery checks. */
final class RecoveryListingEvidence {
    private long boundary, lastCompleted, readyAt;
    private int cleanChecks;

    void begin(long at) { boundary = at; lastCompleted = 0; readyAt = 0; cleanChecks = 0; }

    boolean accept(long started, long completed, boolean complete, int unreadable,
                   boolean inventoryClear, boolean matchingListing) {
        if (boundary <= 0 || started <= Math.max(boundary, lastCompleted)
                || completed < started || !complete || unreadable != 0
                || !inventoryClear || matchingListing) {
            cleanChecks = 0; readyAt = 0;
            return false;
        }
        lastCompleted = completed;
        if (++cleanChecks == 2) readyAt = completed;
        return readyAt > 0;
    }

    long readyAt() { return readyAt; }
    String finalBlocker(long now, long maxAge) {
        if (readyAt <= 0 || lastCompleted <= readyAt) return "a later own-listings recheck is required";
        if (now < lastCompleted || now - lastCompleted > maxAge) return "the own-listings recheck is stale";
        return null;
    }
}
