package dev.doughbay.fabric.automation;

/** Balances replenishment against due reprices; never changes prices, limits or action pacing. */
final class StockFlowScheduler {
    static final long MAX_REPRICE_DEFERRAL_MILLIS = 60_000;
    private int deskActions;
    private long waitingSince = -1;

    boolean deskFirst(boolean repriceDue, int occupiedAndPromised, int capacity, long now) {
        if (!repriceDue) {
            waitingSince = -1;
            deskActions = 0;
            return true;
        }
        if (waitingSince < 0) waitingSince = now;
        // More replenishment turns with a mostly empty book; retain the existing
        // two-turn rotation once at least half the usable capacity is occupied.
        int turns = capacity > 0 && (long) occupiedAndPromised * 2 < capacity ? 4 : 2;
        return deskActions < turns && now - waitingSince < MAX_REPRICE_DEFERRAL_MILLIS;
    }

    void deskActionStarted() { deskActions = Math.min(4, deskActions + 1); }
    void repriceStarted() { deskActions = 0; waitingSince = -1; }
}
