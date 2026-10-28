package dev.doughbay.fabric;

import java.util.List;
import java.util.Locale;

/** Presentation for unexpected disconnects only; never decides why a server stopped responding. */
public final class DisconnectAlertSummary {
    private DisconnectAlertSummary() { }
    public static String title(String reason) {
        if (reason != null && DisconnectRecovery.clickSpam(reason)) return "Tally — CLICK-SPAM DISCONNECT";
        String text = reason == null ? "" : reason.toLowerCase(Locale.ROOT);
        return text.contains("timed out") || text.contains("timeout")
                ? "Tally — CONNECTION TIMEOUT" : "Tally — CONNECTION LOST";
    }
    public static String explanation(String reason) {
        if (reason != null && DisconnectRecovery.clickSpam(reason)) return "The disconnect reason explicitly reports click spam.";
        return "The Minecraft connection was lost. The reported reason does not establish the underlying cause.";
    }
    public static List<String[]> fields(String server, String reason, String recovery, String next) {
        return List.of(new String[]{"What happened", explanation(reason)},
                new String[]{"Reported reason", reason == null || reason.isBlank() ? "Not supplied" : reason},
                new String[]{"Staff Watch trigger", "No — this is an unexpected connection loss, not a local Staff Watch detection."},
                new String[]{"Server", server}, new String[]{"Settings recovery", recovery},
                new String[]{"Next action", next});
    }
    public static String detail(String server, String reason, String recovery, String next) {
        return fields(server, reason, recovery, next).stream()
                .map(field -> field[0] + ": " + field[1]).collect(java.util.stream.Collectors.joining("\n"));
    }
}
