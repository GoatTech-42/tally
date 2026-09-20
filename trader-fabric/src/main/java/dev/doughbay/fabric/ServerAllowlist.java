package dev.doughbay.fabric;

import java.util.Collection;

/**
 * Whether the server the client is on is one the player has listed.
 *
 * <p>The lists hold hosts, and the match used to be exact. DonutSMP moves a
 * player who picks a region onto that region's own address, so a session
 * allowed on {@code donutsmp.net} woke up on {@code west.donutsmp.net} and
 * refused every command: paused, and no Resume would take. A name under a
 * listed host belongs to whoever owns that host, so it is the same server for
 * this purpose. Nothing else is widened: a different domain, or one that only
 * ends in the same letters, is still refused.
 */
public final class ServerAllowlist {
    private ServerAllowlist() { }

    public static boolean allows(Collection<String> listed, String server) {
        if (listed == null || server == null || server.isBlank()) return false;
        if (listed.contains(server)) return true;
        for (String host : listed) {
            // A bare word ("singleplayer", "localhost") or a top-level name has
            // no subdomains worth trusting.
            if (host == null || host.indexOf('.') <= 0) continue;
            if (server.endsWith("." + host)) return true;
        }
        return false;
    }
}
