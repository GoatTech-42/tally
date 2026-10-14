package dev.doughbay.fabric;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.ToDoubleFunction;

/** Pure policy: only an explicit click-spam kick changes pacing, never a network fault. */
public final class DisconnectRecovery {
    private DisconnectRecovery() { }

    public static boolean intentional(String reason) {
        String r = reason.strip().toLowerCase(Locale.ROOT);
        return r.startsWith("staff watch:") || r.equals("gonuts evasion") || r.equals("gonuts restart")
                || r.equals("disconnected") || r.equals("quitting");
    }

    public static boolean clickSpam(String reason) {
        return reason.toLowerCase(Locale.ROOT).matches("(?s).*\\bclick[\\s_-]*spam\\b.*");
    }

    public static Map<String, Double> slower(ToDoubleFunction<String> current) {
        Map<String, Double> next = new LinkedHashMap<>();
        next.put("pace.human_steps", 1.0);
        next.put("pace.fast", 0.0);
        increase(next, current, "click_gap_min_sec", .65, 1.5);
        increase(next, current, "click_gap_max_sec", 1.25, 3.0);
        increase(next, current, "command_min_sec", 2.0, 4.0);
        increase(next, current, "command_max_sec", 3.5, 7.0);
        increase(next, current, "step_min_sec", .9, 2.0);
        increase(next, current, "step_max_sec", 2.0, 4.0);
        increase(next, current, "react_min_sec", .8, 1.5);
        increase(next, current, "react_max_sec", 1.8, 3.0);
        return next;
    }

    private static void increase(Map<String, Double> next, ToDoubleFunction<String> current,
                                 String suffix, double floor, double cap) {
        String key = "pace." + suffix;
        double old = current.applyAsDouble(key);
        // Never undo a user's more conservative value, even if it exceeds our cap.
        next.put(key, Math.max(old, Math.min(cap, Math.max(floor, old * 1.4))));
    }
}
