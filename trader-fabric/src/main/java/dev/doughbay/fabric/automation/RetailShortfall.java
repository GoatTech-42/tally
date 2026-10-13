package dev.doughbay.fabric.automation;

import java.util.HashMap;
import java.util.Map;

/**
 * Notices a lot kept for selling one at a time that the book has as bigger
 * than what the inventory holds, and says when the difference has stood long
 * enough to be written off.
 *
 * <p>The peel waits for the recorded quantity before it touches the pack,
 * which is right for a moment: stock arrives, a click is corrected. It was
 * also where a lot stayed for good. 127 skulls recorded and 126 carried, and
 * for seven hours nothing of that market was listed or bid on, over one item.
 *
 * <p>What is in the pack is the truth once it has stopped changing, so the
 * count has to be the same on several looks in a row, minutes apart, before
 * anything is concluded. A pack with none of the item at all concludes
 * nothing: that is what a lobby or a half-loaded world looks like, and a lot
 * is never written off whole.
 */
final class RetailShortfall {
    static final int LOOKS = 6;
    static final long MILLIS = 3 * 60_000L;

    private record Seen(int available, long since, int looks) {
    }

    private final Map<Long, Seen> seen = new HashMap<>();

    /**
     * How many of a lot to write off now, or 0 to go on waiting.
     *
     * @param recorded  what the book says the lot is
     * @param available what the inventory holds that can be this lot's
     */
    int due(long positionId, int recorded, int available, long now) {
        if (available >= recorded || available < 1) {
            seen.remove(positionId);
            return 0;
        }
        Seen was = seen.get(positionId);
        if (was == null || was.available() != available) {
            seen.put(positionId, new Seen(available, now, 1));
            return 0;
        }
        Seen is = new Seen(available, was.since(), was.looks() + 1);
        if (is.looks() < LOOKS || now - is.since() < MILLIS) {
            seen.put(positionId, is);
            return 0;
        }
        seen.remove(positionId);
        return recorded - available;
    }

    /** The lot is whole, or gone: whatever was being counted against it is forgotten. */
    void clear(long positionId) {
        seen.remove(positionId);
    }
}
