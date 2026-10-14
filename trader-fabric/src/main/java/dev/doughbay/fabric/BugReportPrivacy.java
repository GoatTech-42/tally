package dev.doughbay.fabric;

/** Local filtering of user-written report fields. Raw log text is never exported. */
public final class BugReportPrivacy {
    private BugReportPrivacy() { }
    public static String clean(String value) {
        String text = value == null ? "" : value;
        text = text.replaceAll("(?i)(?:https?://|www\\.)\\S+", "[URL removed]");
        text = text.replaceAll("(?im)(?:authorization|bearer|token|password|secret|api[_ -]?key|webhook|cookie)\\s*[:= ]\\s*[^\\r\\n]+", "[credential removed]");
        text = text.replaceAll("(?i)[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}", "[email removed]");
        text = text.replaceAll("(?i)\\b[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\b", "[UUID removed]");
        text = text.replaceAll("\\b(?:\\d{1,3}\\.){3}\\d{1,3}(?::\\d+)?\\b", "[IP removed]");
        text = text.replaceAll("(?i)(?<!\\w)(?:[0-9a-f]{0,4}:){2,}[0-9a-f:]+(?:%\\w+)?", "[IP removed]");
        text = text.replaceAll("(?i)\\b(?:[a-z0-9-]+\\.)+[a-z]{2,}(?::\\d+)?\\b", "[address removed]");
        text = text.replaceAll("(?i)[a-z]:[\\\\/][^\\r\\n]+|\\\\\\\\[^\\r\\n]+|/(?:home|Users|var|tmp)/[^\\r\\n]+", "[path removed]");
        text = text.replaceAll("[A-Za-z0-9_+/=-]{32,}", "[long identifier removed]");
        text = text.replaceAll("(?m)^.*\\[CHAT\\].*$", "[chat removed]");
        text = text.replaceAll("[\\p{Cntrl}&&[^\\n\\t]]", "");
        return text.substring(0, Math.min(1500, text.length())).strip();
    }
}
