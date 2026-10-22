package dev.doughbay.fabric.automation;

import dev.doughbay.fabric.AutomatedExecutionDriver.OwnOrderRow;
import java.util.*;

/** One selling shelf and one uncollected reserve. Rebuilt from native orders and durable stock. */
final class ReserveStock {
    record Book(int batches, long units, boolean valid) {}

    static Book book(String item, List<OwnOrderRow> rows, Map<String,Integer> collected) {
        Map<String,Long> credits = new HashMap<>();
        Map<String,Long> delivered = new HashMap<>();
        for (var r : rows) if (r.itemId().equals(item)) {
            if (r.requested() <= 0 || r.unitPrice() <= 0 || r.delivered() < 0 || r.delivered() > r.requested())
                return new Book(0,0,false);
            String k = key(r);
            delivered.merge(k,(long)r.delivered(),Long::sum);
            credits.put(k,(long)collected.getOrDefault(k,0));
        }
        for (var e : credits.entrySet())
            if (e.getValue() < 0 || e.getValue() > delivered.get(e.getKey())) return new Book(0,0,false);
        int batches = 0; long units = 0;
        for (var r : rows) if (r.itemId().equals(item)) {
            String k = key(r);
            long credit = Math.min(credits.get(k),r.delivered());
            credits.put(k,credits.get(k)-credit);
            long waiting = r.delivered()-credit;
            long remaining = r.alreadyCancelled() ? 0 : r.remaining();
            if (waiting+remaining > 0) { batches++; units += waiting+remaining; }
        }
        return new Book(batches,units,true);
    }

    /** Ordinal among matching rows, skipping already-collected deliveries, including partial orders. */
    static int collectOrdinal(String item, long unit, int requested, List<OwnOrderRow> rows, Map<String,Integer> collected) {
        return collectOrdinal(item,unit,requested,rows,collected,1);
    }

    static int collectOrdinal(String item, long unit, int requested, List<OwnOrderRow> rows, Map<String,Integer> collected, int minimumUnits) {
        String key = item+"|"+unit+"|"+requested;
        long credit = collected.getOrDefault(key,0);
        int ordinal = 0;
        if (!book(item,rows,collected).valid()) return -1;
        for (var row : rows) {
            if (!row.itemId().equals(item) || row.unitPrice()!=unit || row.requested()!=requested) continue;
            long used = Math.min(credit,row.delivered()); credit -= used;
            if (row.delivered()-used >= minimumUnits) return ordinal;
            ordinal++;
        }
        return -1;
    }

    static String key(OwnOrderRow r) { return r.itemId()+"|"+r.unitPrice()+"|"+r.requested(); }

    static boolean mayPlace(Book book, long heldUnits, int batchUnits) {
        if (!book.valid() || heldUnits < 0 || batchUnits <= 0) return false;
        return book.batches() < 2 && heldUnits+book.units()+batchUnits <= 2L*batchUnits;
    }

    /** Only whole stacks that fit the shelf and available auction/inventory slots are collected. */
    static int collectUnits(long heldUnits, int batchUnits, int fullStack, int freeSlots) {
        if (heldUnits < 0 || batchUnits <= 0 || fullStack <= 1 || freeSlots <= 0) return 0;
        long gap = Math.max(0,batchUnits-heldUnits);
        return (int)Math.min(gap/fullStack,freeSlots)*fullStack;
    }

    /** A reserve batch is never silently reduced to a part batch to fit a cap. */
    static int batchUnits(int stacks, int fullStack, long unit, long budget, long depthUnits, long heldAndOrdered) {
        if (stacks < 1 || fullStack <= 1 || fullStack > 64 || unit <= 0 || budget <= 0 || heldAndOrdered < 0) return 0;
        long units = (long)stacks*fullStack;
        if (units > 999 || units > budget/unit || (depthUnits >= 0 && heldAndOrdered+units > depthUnits)) return 0;
        return (int)units;
    }
}
