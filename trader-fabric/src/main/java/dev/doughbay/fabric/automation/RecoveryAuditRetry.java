package dev.doughbay.fabric.automation;

/** A failed read must not permanently prevent this or a later position's reconciliation. */
final class RecoveryAuditRetry {
    private long positionId = -1;
    private long nextAttempt;

    boolean ready(long position, long now) {
        return position != positionId || now >= nextAttempt;
    }

    void attempt(long position, long now) {
        positionId = position;
        nextAttempt = now + 15_000;
    }

    void reset() { positionId = -1; nextAttempt = 0; }
}
