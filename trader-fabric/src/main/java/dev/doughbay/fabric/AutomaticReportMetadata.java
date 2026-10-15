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
        if (kind == AutomaticReportDetector.Failure.PERSISTENCE_WRITER_FAILED
                && signature != AutomaticReportDetector.Signature.PERSISTENCE_GENERIC_FAILURE
                || kind == AutomaticReportDetector.Failure.DURABLE_WRITE_REJECTED
                && signature == AutomaticReportDetector.Signature.PERSISTENCE_GENERIC_FAILURE)
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
        return "[automatic] Critical runtime failure: " + kind.name()
            + "\nSignature: " + signature.name()
            + "\nA requested trading session remained blocked by this persistence failure for at least 120 seconds."
            + " The detector previously observed healthy persistence in this connection."
            + " Manual stops, intentional recovery and disconnected clients are excluded."
            + "\nThis report records a critical failure; it does not authorize a patch or prove its cause.";
    }
    public String expected() { return "Persist verified transaction checkpoints, or recover safely without rewriting transaction history or resetting spending limits."; }
}
