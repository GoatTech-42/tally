package dev.doughbay.fabric;

import java.util.Optional;

/** Critical runtime facts only. Log counters and lack of sales never trigger this detector. */
public final class AutomaticReportDetector {
    public enum Failure { DURABLE_WRITE_REJECTED, PERSISTENCE_WRITER_FAILED, AUTOMATIC_RECOVERY_STALLED }
    public enum Signature { IMMUTABLE_POSITION_IDENTITY_CONFLICT, RETAIL_CHILD_ID_COLLISION,
        CHECKPOINT_REJECTED, PERSISTENCE_GENERIC_FAILURE, EVASION_RESUME_STALLED }
    public record Observation(boolean optedIn, boolean connected, boolean tradingRequested,
                              boolean intentionalRecovery, boolean persistenceReady,
                              boolean refusedWrite, boolean writerFailed, Signature signature,
                              boolean automaticRecoveryStalled) {
        public Observation(boolean optedIn, boolean connected, boolean tradingRequested,
                           boolean intentionalRecovery, boolean persistenceReady,
                           boolean refusedWrite, boolean writerFailed, Signature signature) {
            this(optedIn, connected, tradingRequested, intentionalRecovery, persistenceReady,
                    refusedWrite, writerFailed, signature, false);
        }
    }
    public record Candidate(Failure failure, Signature signature) { }
    public static final long PERSISTENCE_GRACE_MILLIS = 120_000;
    public static final long RECOVERY_GRACE_MILLIS = 300_000;
    private boolean healthyInThisConnection;
    private Candidate pending;
    private long firstObserved;

    /** now must be monotonic elapsed milliseconds, not the wall clock. */
    public Optional<Candidate> observe(Observation observation, long now) {
        if (!observation.connected()) {
            healthyInThisConnection = false; clear(); return Optional.empty();
        }
        if (observation.persistenceReady() && !observation.refusedWrite() && !observation.writerFailed())
            healthyInThisConnection = true;
        boolean stalledRecovery = observation.automaticRecoveryStalled()
                && observation.persistenceReady() && !observation.refusedWrite() && !observation.writerFailed();
        if (!observation.optedIn() || !observation.tradingRequested()
                || observation.intentionalRecovery() && !stalledRecovery || !healthyInThisConnection) {
            clear(); return Optional.empty();
        }
        Failure failure = stalledRecovery ? Failure.AUTOMATIC_RECOVERY_STALLED
            : observation.refusedWrite() ? Failure.DURABLE_WRITE_REJECTED
            : observation.writerFailed() ? Failure.PERSISTENCE_WRITER_FAILED : null;
        if (failure == null) { clear(); return Optional.empty(); }
        Signature signature = stalledRecovery ? Signature.EVASION_RESUME_STALLED : observation.signature();
        if (signature == null || failure == Failure.PERSISTENCE_WRITER_FAILED)
            signature = failure == Failure.DURABLE_WRITE_REJECTED
                ? Signature.CHECKPOINT_REJECTED : Signature.PERSISTENCE_GENERIC_FAILURE;
        Candidate next = new Candidate(failure, signature);
        if (pending == null || pending.failure() != failure) {
            pending = next; firstObserved = now;
        } else if (pending.signature() == Signature.CHECKPOINT_REJECTED
                && signature != Signature.CHECKPOINT_REJECTED) {
            // Preserve a useful typed cause even if later successful writes overwrite status.detail.
            pending = next;
        }
        long grace = failure == Failure.AUTOMATIC_RECOVERY_STALLED
                ? RECOVERY_GRACE_MILLIS : PERSISTENCE_GRACE_MILLIS;
        return now - firstObserved >= grace ? Optional.of(pending) : Optional.empty();
    }
    private void clear() { pending = null; firstObserved = 0; }

    /** Only known constant causes leave the process; never copy a worker's raw exception message. */
    public static Signature classifyRefusal(String detail) {
        if (detail != null && detail.contains("position id conflicts with a different immutable position identity"))
            return Signature.IMMUTABLE_POSITION_IDENTITY_CONFLICT;
        if (detail != null && detail.contains("Retail child position id is already in use"))
            return Signature.RETAIL_CHILD_ID_COLLISION;
        return Signature.CHECKPOINT_REJECTED;
    }
}
