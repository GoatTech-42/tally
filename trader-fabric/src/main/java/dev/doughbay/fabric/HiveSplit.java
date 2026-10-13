package dev.doughbay.fabric;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Which account of a hive a market belongs to, when the markets are split
 * between them.
 *
 * <p>Two accounts trading the same markets sell to the same buyers: each
 * takes half of what one would have sold, and they bid against each other in
 * the order house to do it. Split, every market has one account buying in it.
 * What an account already holds it still sells, whoever the market belongs to.
 *
 * <p>The split has to come out the same on every client without their talking
 * to each other, so it is arithmetic on the item's name and the list of
 * accounts, not a negotiation: the accounts in name order, and the item's hash
 * picks one. An account that goes away keeps its markets for ten minutes, so a
 * restart does not have its sibling move in and out again.
 */
public final class HiveSplit {
    static final long HOLD_MILLIS = 10 * 60_000L;
    private static final long REFRESH_MILLIS = 15_000L;
    private static final Map<String, Long> SEEN = new HashMap<>();
    private static List<String> roster = List.of();
    private static long rosterAt;

    private HiveSplit() {
    }

    /**
     * The accounts the markets are split between: everyone seen in the hive
     * in the last ten minutes, in name order. Asked of the ledger at most
     * every fifteen seconds; this is called for every market on every pass.
     */
    public static synchronized List<String> roster(Supplier<List<String>> alive, long now) {
        if (now - rosterAt < REFRESH_MILLIS && rosterAt != 0) return roster;
        List<String> seenNow;
        try {
            seenNow = alive.get();
        } catch (RuntimeException e) {
            seenNow = List.of();
        }
        roster = held(seenNow, now);
        rosterAt = now;
        return roster;
    }

    /** The accounts alive now together with those seen within the hold, in name order. */
    static synchronized List<String> held(List<String> alive, long now) {
        for (String name : alive) {
            if (name != null && !name.isBlank()) SEEN.put(name.toLowerCase(Locale.ROOT), now);
        }
        SEEN.values().removeIf(at -> now - at > HOLD_MILLIS);
        List<String> out = new ArrayList<>(SEEN.keySet());
        out.sort(null);
        return List.copyOf(out);
    }

    /** Forgets who has been seen; the tests start from nothing. */
    static synchronized void reset() {
        SEEN.clear();
        roster = List.of();
        rosterAt = 0;
    }

    /**
     * Whether a market is this account's to buy in. Always, when there is no
     * sibling, when this account is not on the roster yet, or when its name is
     * not known: a market nobody buys in is worse than one two accounts do.
     *
     * @param seed turns the split over when it has come out lopsided
     */
    public static boolean mine(String itemId, String me, List<String> roster, int seed) {
        if (itemId == null || me == null || me.isBlank() || roster.size() < 2) return true;
        String self = me.toLowerCase(Locale.ROOT);
        if (!roster.contains(self)) return true;
        return roster.get(owner(itemId, roster.size(), seed)).equals(self);
    }

    /** The place on the roster a market falls to. */
    static int owner(String itemId, int accounts, int seed) {
        String bare = Tuning.normalizeItem(itemId);
        int h = bare.hashCode() * 31 + seed * 0x9E3779B1;
        h ^= h >>> 16;
        h *= 0x45D9F3B;
        h ^= h >>> 15;
        return Math.floorMod(h, Math.max(1, accounts));
    }
}
