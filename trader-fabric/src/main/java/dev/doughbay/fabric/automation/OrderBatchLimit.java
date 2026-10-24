package dev.doughbay.fabric.automation;

/** Shared automatic-order limits. Zero preserves the existing limit of the selected trading mode. */
final class OrderBatchLimit {
    private OrderBatchLimit() {}

    static int effective(int configured, boolean reserve) {
        if (configured < 0 || configured > 90) return 0;
        return configured == 0 ? (reserve ? 2 : 1) : configured;
    }

    static boolean hasRoom(ReserveStock.Book book, int limit) {
        return book.valid() && book.batches() >= 0 && book.units() >= 0
                && limit >= 1 && limit <= 90 && book.batches() < limit;
    }

    /** A second order consumes the same observed depth budget, rather than receiving another copy of it. */
    static boolean fitsDepth(ReserveStock.Book book, long held, int proposed, long depth) {
        if (!book.valid() || book.units() < 0 || held < 0 || proposed <= 0) return false;
        return depth < 0 || held <= depth && book.units() <= depth-held
                && proposed <= depth-held-book.units();
    }

    static int collectionUnits(long held, int shelfUnits, int perStack, int freeSlots) {
        if (held < 0 || shelfUnits <= 0 || perStack < 1 || freeSlots <= 0) return 0;
        long vacant = Math.max(0, (long)shelfUnits-held);
        return (int)(Math.min(vacant/perStack, freeSlots)*perStack);
    }
}
