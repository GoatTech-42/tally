package dev.doughbay.fabric;

import java.util.List;
import java.util.Locale;

/** Ordinary failed-join routing through the same regional doors as restart recovery. */
public final class JoinRecovery {
    private JoinRecovery() { }

    public static boolean networkFailure(String reason) {
        String text = reason == null ? "" : reason.toLowerCase(Locale.ROOT);
        // A login, enforcement or intentional refusal is never a reason to change regions.
        for (String refusal : List.of("banned", "ban appeal", "kicked", "invalid session", "authentication",
                "not authenticated", "whitelist", "not whitelisted", "already connected", "already logged",
                "staff watch", "evasion", "quitting", "gonuts restart")) {
            if (text.contains(refusal)) return false;
        }
        return text.contains("timed out") || text.contains("timeout") || text.contains("connection refused")
                || text.contains("connection reset") || text.contains("no route to host")
                || text.contains("network is unreachable") || text.contains("unknown host")
                || text.contains("unknownhostexception") || text.contains("unresolved address");
    }

    public static String next(String attempted, String configuredRegions, String reason) {
        if (!dev.doughbay.fabric.staffwatch.DonutServer.matches(attempted) || !networkFailure(reason)) return attempted;
        var regions = RestartPlan.hops(configuredRegions, attempted);
        // Keep the custom restart route order, rotating after the failed regional door.
        // RestartPlan omits the failed host. The default list therefore needs the full
        // network order to locate the following region rather than always choosing east.
        var all = RestartPlan.hops(configuredRegions, "donutsmp.net");
        int current = -1;
        for (int i = 0; i < all.size(); i++) if (RestartPlan.host(all.get(i)).equals(RestartPlan.host(attempted))) current = i;
        if (current >= 0 && all.size() > 1) return all.get((current + 1) % all.size());
        return regions.isEmpty() ? attempted : regions.getFirst();
    }
}
