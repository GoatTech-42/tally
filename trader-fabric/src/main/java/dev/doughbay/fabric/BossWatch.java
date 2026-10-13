package dev.doughbay.fabric;

import java.util.List;
import java.util.Locale;

/**
 * Tells a wither's boss bar from any other bar on the screen, and says when
 * running from one has stopped making sense.
 *
 * <p>Players spawn withers where random teleports land. One blast or a few
 * skulls kill an unarmoured account in seconds, with nobody in sight: the bot
 * died four seconds after a teleport holding 127 skulls of its own, and its
 * guard, which only looks for players, reported "nobody the client can see".
 * The bar is the earliest sign there is.
 */
final class BossWatch {
    /** One bar: what it says, and whether it dims the sky, which the wither's does. */
    record Bar(String name, boolean darkensScreen) {
    }

    /** Escapes this close together are one escape still landing, not two. */
    static final long RETRY_MILLIS = 15_000L;
    /** This many escapes inside the window and the bar is still up: it is not a wither that is here. */
    static final int MAX_ESCAPES = 3;
    static final long WINDOW_MILLIS = 5 * 60_000L;
    static final long IGNORE_MILLIS = 30 * 60_000L;

    private BossWatch() {
    }

    /**
     * The name on the bar of a wither among these, or null when there is none.
     * A wither's bar dims the sky and is the only ordinary one that does; a
     * wither somebody has renamed still does, and one a server has stopped
     * from dimming is still called a wither.
     */
    static String wither(List<Bar> bars) {
        if (bars == null) return null;
        for (Bar bar : bars) {
            if (bar == null) continue;
            String name = bar.name() == null ? "" : bar.name().strip();
            if (bar.darkensScreen() || name.toLowerCase(Locale.ROOT).contains("wither")) {
                return name.isEmpty() ? "Wither" : name;
            }
        }
        return null;
    }

    /**
     * Whether to stop reacting to the bar for a while: it has been run from
     * {@link #MAX_ESCAPES} times in five minutes and is still showing. A bar
     * that follows the player across the map is the server's own, an event or
     * a notice, and teleporting away from it for ever would be worse than
     * the wither.
     */
    static boolean givesUp(int escapesInWindow) {
        return escapesInWindow >= MAX_ESCAPES;
    }
}
