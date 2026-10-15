package dev.doughbay.fabric;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/** Anonymous grouping contract proposed for schema 3; not an identity or a credential. */
public record AutomaticReportMetadata(AutomaticReportDetector.Failure kind,
                                      AutomaticReportDetector.Signature signature, String fingerprint) {
    public AutomaticReportMetadata {
        if (kind == null || signature == null || fingerprint == null || !fingerprint.matches("[a-f0-9]{64}"))
            throw new IllegalArgumentException("Invalid automatic-report metadata");
        boolean matches = switch (kind) {
            case PERSISTENCE_WRITER_FAILED -> signature == AutomaticReportDetector.Signature.PERSISTENCE_GENERIC_FAILURE;
            case AUTOMATIC_RECOVERY_STALLED -> signature == AutomaticReportDetector.Signature.EVASION_RESUME_STALLED;
            case DURABLE_WRITE_REJECTED -> signature == AutomaticReportDetector.Signature.IMMUTABLE_POSITION_IDENTITY_CONFLICT
                    || signature == AutomaticReportDetector.Signature.RETAIL_CHILD_ID_COLLISION
                    || signature == AutomaticReportDetector.Signature.CHECKPOINT_REJECTED;
        };
        if (!matches)
            throw new IllegalArgumentException("Failure and signature do not match");
    }
    public static AutomaticReportMetadata of(String build, String minecraft, AutomaticReportDetector.Candidate candidate) {
        if (build == null || !build.matches("[0-9A-Za-z.+_-]{1,40}")
                || minecraft == null || !minecraft.matches("[0-9A-Za-z.+_-]{1,40}"))
            throw new IllegalArgumentException("Invalid automatic-report build");
        try {
            String canonical = "gonuts-auto-v1\n" + build + "\n" + minecraft + "\n"
                + candidate.failure().name() + "\n" + candidate.signature().name();
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(canonical.getBytes(StandardCharsets.UTF_8)));
            return new AutomaticReportMetadata(candidate.failure(), candidate.signature(), hash);
        } catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    public String description() {
        if (kind == AutomaticReportDetector.Failure.AUTOMATIC_RECOVERY_STALLED)
            return "[automatic] Critical runtime failure: AUTOMATIC_RECOVERY_STALLED"
                    + "\nSignature: EVASION_RESUME_STALLED"
                    + "\nAfter an automatic escape and all-clear wait, repeated resume attempts remained blocked"
                    + " for at least five additional minutes despite a connected, authorized world, idle driver"
                    + " and durable healthy persistence. Manual pauses, unresolved exposure, active danger,"
                    + " restart/transfer holds and normal market waits are excluded."
                    + "\nThis report does not authorize bypassing recovery checks or trading limits.";
        return "[automatic] Critical runtime failure: " + kind.name()
            + "\nSignature: " + signature.name()
            + "\nA requested trading session remained blocked by this persistence failure for at least 120 seconds."
            + " The detector previously observed healthy persistence in this connection."
            + " Manual stops, intentional recovery and disconnected clients are excluded."
            + "\nThis report records a critical failure; it does not authorize a patch or prove its cause.";
    }
    public String expected() { return kind == AutomaticReportDetector.Failure.AUTOMATIC_RECOVERY_STALLED
            ? "Resume the previously requested trading session safely after the automatic escape, preserving exposure and spending limits."
            : "Persist verified transaction checkpoints, or recover safely without rewriting transaction history or resetting spending limits."; }
}
