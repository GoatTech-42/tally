package dev.doughbay.fabric.staffwatch;

import dev.doughbay.core.execution.ExecutionResult;
import java.util.function.Supplier;

/** Retains an existing running session across automatic reconnects, until safe resume or cancellation. */
final class ReconnectResume {
    private boolean pending;
    private long startedAt, readySince, nextAttempt;
    private long unexpectedDisconnectAt;

    void noteUnexpectedDisconnect(boolean ownedRunningSession, long now) {
        unexpectedDisconnectAt = ownedRunningSession ? now : 0;
    }

    void automaticRetryScheduled(long now) {
        // A manual connection failure also gets automatic connection retries.
        // Only a recent, explicitly unexpected loss may carry trading intent.
        boolean owned = unexpectedDisconnectAt > 0 && now >= unexpectedDisconnectAt
                && now - unexpectedDisconnectAt <= 30_000;
        unexpectedDisconnectAt = 0;
        arm(owned, now);
    }

    void arm(boolean ownedRunningSession, long now) {
        if (!ownedRunningSession || pending) return;
        pending = true;
        startedAt = now;
        readySince = 0;
        nextAttempt = 0;
    }

    boolean pending() { return pending; }
    long startedAt() { return startedAt; }
    void cancel() { pending = false; readySince = 0; nextAttempt = 0; unexpectedDisconnectAt = 0; }

    /** ready includes fresh world proof and completion of every connection/cooldown owner. */
    ExecutionResult tick(long now, boolean ready, boolean sessionMayResume,
                         Supplier<ExecutionResult> resume) {
        return tick(now, ready, sessionMayResume, false, resume);
    }

    ExecutionResult tick(long now, boolean ready, boolean sessionMayResume,
                         boolean sessionStillRunning, Supplier<ExecutionResult> resume) {
        if (!pending) return null;
        // The connection retry can be scheduled before the controller sees
        // the disconnected world and pauses. Keep the captured intent across
        // that tick; a genuine Stop still cancels below. If no pause was
        // needed and the world is ready, trading has already recovered.
        if (sessionStillRunning) {
            if (ready) cancel();
            else readySince = 0;
            return null;
        }
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
