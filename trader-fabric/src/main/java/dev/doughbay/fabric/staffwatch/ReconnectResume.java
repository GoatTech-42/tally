package dev.doughbay.fabric.staffwatch;

import dev.doughbay.core.execution.ExecutionResult;
import java.util.function.Supplier;

/** Retains ownership of an automatic staff departure until safe resume or explicit cancellation. */
final class ReconnectResume {
    private boolean pending;
    private long startedAt, readySince, nextAttempt;

    void arm(boolean ownedRunningSession, long now) {
        if (!ownedRunningSession || pending) return;
        pending = true;
        startedAt = now;
        readySince = 0;
        nextAttempt = 0;
    }

    boolean pending() { return pending; }
    long startedAt() { return startedAt; }
    void cancel() { pending = false; readySince = 0; nextAttempt = 0; }

    /** ready includes fresh world proof and completion of every connection/cooldown owner. */
    ExecutionResult tick(long now, boolean ready, boolean sessionMayResume,
                         Supplier<ExecutionResult> resume) {
        if (!pending) return null;
        if (!sessionMayResume) { cancel(); return null; }
        if (!ready) { readySince = 0; return null; }
        if (readySince == 0) { readySince = now; return null; }
        if (now - readySince < 15_000 || now < nextAttempt) return null;
        ExecutionResult result = resume.get();
        if (result.ok()) cancel();
        else nextAttempt = now + 30_000; // A temporary refusal never drops ownership.
        return result;
    }
}
