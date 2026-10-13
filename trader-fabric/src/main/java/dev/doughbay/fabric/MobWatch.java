package dev.doughbay.fabric;

import java.util.Set;

/**
 * Which hostile mobs are worth running from, and when running from them has
 * stopped making sense.
 *
 * <p>A random teleport lands an unarmoured, motionless account in the open,
 * and whatever is near walks up and hits it: 4.5 a blow from a zombie, dead
 * in under ten seconds. The bot died three times in a day that way while its
 * guard watched for players. A mob is a threat from much further off than it
 * can hit from, because it is coming.
 *
 * <p>Not every mob within the distance counts. The ground under any spot is
 * full of them, in caves that never see the sun, and they cannot get up; a
 * mob the player has no line of sight to is only counted once it is close.
 * And some are only hostile when provoked.
 */
final class MobWatch {
    /** Inside this, a mob counts whether or not it can be seen: it may be digging distance, or round a corner. */
    static final double CLOSE_BLOCKS = 10;
    /** Inside this, there is no time to let a trade finish. */
    static final double URGENT_BLOCKS = 12;
    /** One escape is given this long to land before a mob can call for another. */
    static final long RETRY_MILLIS = 20_000L;
    /** This many mob escapes inside the window means mobs are everywhere tonight. */
    static final int MAX_ESCAPES = 5;
    static final long WINDOW_MILLIS = 10 * 60_000L;
    /** How long the distance is ignored after that; a hit still sends the bot away at once. */
    static final long IGNORE_MILLIS = 10 * 60_000L;
    /** How long a mob still far off lets a trade in flight finish. */
    static final long PATIENCE_MILLIS = 6_000L;

    /** Hostile by the game's list, but left alone they leave you alone. */
    private static final Set<String> ONLY_IF_PROVOKED = Set.of("enderman", "zombified_piglin");

    private MobWatch() {
    }

    /** Whether a mob of this kind attacks unprovoked. */
    static boolean attacksUnprovoked(String kind) {
        return kind != null && !ONLY_IF_PROVOKED.contains(kind);
    }

    /**
     * Whether a hostile mob at this distance is one to run from.
     *
     * @param reach   the player's setting, in blocks; 0 for off
     * @param visible whether there is a clear line between the player and the mob
     */
    static boolean threatens(double distance, double reach, boolean visible) {
        if (reach <= 0 || distance > reach) return false;
        return visible || distance <= CLOSE_BLOCKS;
    }

    /** Whether so many mob escapes have just been made that another would only be one more. */
    static boolean givesUp(int escapesInWindow) {
        return escapesInWindow >= MAX_ESCAPES;
    }
}
