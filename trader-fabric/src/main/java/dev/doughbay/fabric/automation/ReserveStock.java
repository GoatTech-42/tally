package dev.doughbay.fabric.automation;

import dev.doughbay.fabric.AutomatedExecutionDriver.OwnOrderRow;
import java.util.*;

/** One selling batch and one uncollected batch. Rebuilt from native orders and durable stock, not timers. */
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

    /** Ordinal among finished matching rows, skipping old already-collected completed orders. */
    static int collectOrdinal(String item, long unit, int requested, List<OwnOrderRow> rows, Map<String,Integer> collected) {
        String key = item+"|"+unit+"|"+requested;
        long credit = collected.getOrDefault(key,0);
        int ordinal = 0;
        if (!book(item,rows,collected).valid()) return -1;
        for (var row : rows) {
            if (!row.itemId().equals(item) || row.unitPrice()!=unit || row.requested()!=requested) continue;
            long used = Math.min(credit,row.delivered()); credit -= used;
            if (row.remaining()!=0 && !row.alreadyCancelled()) continue;
            if (row.delivered()-used > 0) return ordinal;
            ordinal++;
        }
        return -1;
    }

    static String key(OwnOrderRow r) { return r.itemId()+"|"+r.unitPrice()+"|"+r.requested(); }

    static boolean mayPlace(Book book, long heldUnits, int batchUnits) {
        if (!book.valid() || heldUnits < 0 || batchUnits <= 0) return false;
        int limit = heldUnits > 0 ? 1 : 2;
        return book.batches() < limit && heldUnits+book.units()+batchUnits <= 2L*batchUnits;
    }

    static boolean mayCollect(long heldUnits, boolean finished) {
        return heldUnits == 0 && finished;
    }

    /** A reserve batch is never silently reduced to a part batch to fit a cap. */
    static int batchUnits(int stacks, int fullStack, long unit, long budget, long depthUnits, long heldAndOrdered) {
        if (stacks < 1 || fullStack <= 1 || fullStack > 64 || unit <= 0 || budget <= 0 || heldAndOrdered < 0) return 0;
        long units = (long)stacks*fullStack;
        if (units > 999 || units > budget/unit || (depthUnits >= 0 && heldAndOrdered+units > depthUnits)) return 0;
        return (int)units;
    }
}
