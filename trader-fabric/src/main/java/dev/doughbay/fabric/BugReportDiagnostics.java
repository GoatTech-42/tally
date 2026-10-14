package dev.doughbay.fabric;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/** Exports only known event counts and a fixed state name, never log messages. */
public final class BugReportDiagnostics {
    private BugReportDiagnostics() { }
    private static final Pattern STATE = Pattern.compile("DoughBay automation \\[(SCANNING|BID_DESK|PREPARING_LIST|LISTING|MONITORING|COOLDOWN|PAUSED|STOPPED|IDLE|BUYING)\\]");
    private static final Map<String, Pattern> EVENTS = new LinkedHashMap<>();
    static {
        EVENTS.put("SQLite errors", Pattern.compile("SQLiteException|SQLITE_BUSY|SQLITE_LOCKED"));
        EVENTS.put("Uncompleted desk actions", Pattern.compile("did not complete: ABORTED"));
        EVENTS.put("Disconnect notices", Pattern.compile("Client disconnected with reason:|GoNuts connection lost:"));
        EVENTS.put("Evasion notices", Pattern.compile("DoughBay Evasion:.*escaping"));
        EVENTS.put("Recovered purchase pauses", Pattern.compile("PAUSED: Recovered purchase"));
    }
    public static String read(Path path) throws IOException {
        // Bound both IO and memory. Never read settings, databases, command lines or older logs.
        try (RandomAccessFile file = new RandomAccessFile(path.toFile(), "r")) {
            long start = Math.max(0, file.length() - 262144);
            file.seek(start);
            byte[] bytes = new byte[(int) Math.min(262144, file.length() - start)];
            file.readFully(bytes);
            String text = new String(bytes, StandardCharsets.UTF_8);
            if (start > 0) { int newline = text.indexOf('\n'); text = newline < 0 ? "" : text.substring(newline + 1); }
            return summarize(text);
        }
    }
    static String summarize(String text) {
        String state = "Unknown";
        var matcher = STATE.matcher(text);
        while (matcher.find()) state = matcher.group(1);
        StringBuilder out = new StringBuilder("Diagnostic sample: last 256 KiB of current log (not a time window)\nLast recorded state: ").append(state);
        EVENTS.forEach((label, pattern) -> out.append('\n').append(label).append(": ").append(pattern.matcher(text).results().count()));
        return out.toString();
    }
}
