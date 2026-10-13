package dev.doughbay.fabric;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The judgements behind getting through a server restart, kept free of the
 * game so they can be tested: what counts as the world being back, and which
 * addresses a client may step through on the way.
 */
final class RestartPlan {
    /** After being parked, before leaving: the stop the session made has to land first. */
    static final long ARM_MILLIS = 4_000L;
    /** How long to stand on the side door before going back to the front one. */
    static final long DWELL_MILLIS = 5_000L;
    /** How long the world has to hold still before it is believed. */
    static final long SETTLE_MILLIS = 12_000L;
    static final long CONNECT_TIMEOUT_MILLIS = 45_000L;
    static final long HOME_RETRY_MILLIS = 20_000L;
    /** How long after the parking notice the world the client is in is taken to be the waiting room. */
    static final long PARKED_MILLIS = 1_500L;
    /** After that, before /leave: the stop the session made has to land first. */
    static final long LEAVE_ARM_MILLIS = 2_000L;

    private RestartPlan() {
    }

    /** The line the proxy sends when it has parked a player for a restart. */
    static boolean saysLimbo(String line) {
        return line != null && line.toLowerCase(Locale.ROOT).contains("connected to proxy limbo");
    }

    /** An address as it is compared: lower case, no port. */
    static String host(String address) {
        if (address == null) return "";
        String a = address.strip().toLowerCase(Locale.ROOT);
        int colon = a.lastIndexOf(':');
        if (colon > 0 && a.indexOf(':') == colon && a.substring(colon + 1).matches("\\d{1,5}")) a = a.substring(0, colon);
        return a;
    }

    /** Whether two addresses are doors of one network: the same last two labels of a name. */
    static boolean sameSite(String a, String b) {
        String x = site(host(a));
        return !x.isEmpty() && x.equals(site(host(b)));
    }

    private static String site(String host) {
        if (host.isEmpty() || !host.matches(".*[a-z].*")) return "";
        String[] labels = host.split("\\.");
        if (labels.length < 2) return "";
        return labels[labels.length - 2] + "." + labels[labels.length - 1];
    }

    /**
     * The side doors to try, in order. What the player listed, or east and
     * west of the home address when they listed nothing. Only ever another
     * door of the same network, and never the home address itself: a typing
     * mistake must not send an account to some other server.
     */
    static List<String> hops(String configured, String home) {
        String h = host(home);
        List<String> out = new ArrayList<>();
        if (site(h).isEmpty()) return out;
        List<String> wanted = new ArrayList<>();
        if (configured != null) {
            for (String token : configured.split("[,;\\s]+")) {
                if (!token.isBlank()) wanted.add(token.strip().toLowerCase(Locale.ROOT));
            }
        }
        if (wanted.isEmpty()) {
            wanted.add("east." + site(h));
            wanted.add("west." + site(h));
        }
        for (String w : wanted) {
            if (out.size() >= 4) break;
            if (host(w).equals(h) || !sameSite(w, h)) continue;
            boolean seen = false;
            for (String o : out) if (host(o).equals(host(w))) seen = true;
            if (!seen) out.add(w);
        }
        return out;
    }

    /**
     * Whether a player parked for a restart has been put back. The proxy says
     * when it parks someone and never says when it lets them go, so the proof
     * is what a world has and the waiting room has not: the client is no longer
     * in the world it was parked in, the money sidebar is
     * showing, and it has stayed that way.
     */
    static boolean backFromLimbo(boolean movedOn, long levelChangedAt, boolean balanceShown, long now) {
        return movedOn && balanceShown && now - levelChangedAt >= SETTLE_MILLIS;
    }

    /** How long a world reached with /leave has to hold before it is acted in. */
    static final long LEAVE_SETTLE_MILLIS = 4_000L;
    /** How long /leave is given to put the client in a world. */
    static final long LEAVE_TIMEOUT_MILLIS = 90_000L;

    /**
     * Whether /leave has taken the client out of the waiting room: it is in
     * another world, and still there a few seconds on. The balance on the
     * sidebar is not asked for here. The one time this ran for real the
     * client was put in another region within a second, with a sidebar and a
     * tab list full of players, and the step sat for ninety seconds waiting
     * on a balance it did not read. What /leave leads to is a world.
     */
    static boolean worldAfterLeave(boolean movedOn, long levelChangedAt, long now) {
        return movedOn && now - levelChangedAt >= LEAVE_SETTLE_MILLIS;
    }

    /** Whether a fresh join of the home address found the world there: nothing said about a restart since, and the sidebar up. */
    static boolean homeProven(long joinedAt, long lastNoticeAt, boolean balanceShown, long now) {
        return joinedAt > 0 && lastNoticeAt < joinedAt && balanceShown && now - joinedAt >= SETTLE_MILLIS;
    }
}
