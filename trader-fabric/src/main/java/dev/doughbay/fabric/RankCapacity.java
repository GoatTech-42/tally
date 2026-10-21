package dev.doughbay.fabric;

import java.util.List;
import java.util.regex.Pattern;

/** Evidence from the owning account's server menus, never market rows or hive totals. */
final class RankCapacity {
    private static final Pattern LABEL = Pattern.compile(
            "(?i)^(?:auction slots|order slots|slots|orders|auctions|listings)(?: used)?\\s*:\\s*(\\d+)\\s*/\\s*(18|45|90)\\s*$");

    static int explicitTotal(List<String> serverControlLines) {
        int found = 0;
        for (String line : serverControlLines) {
            var match = LABEL.matcher(line.strip());
            if (!match.matches()) continue;
            try {
                int used = Integer.parseInt(match.group(1)), total = Integer.parseInt(match.group(2));
                if (used > total || (found != 0 && total != found)) return 0;
                found = total;
            } catch (NumberFormatException invalid) { return 0; }
        }
        return found;
    }

    record PageCapacity(int slots, boolean exact) { }
    static PageCapacity auctionPage(List<AutomatedExecutionDriver.OwnListingCell> cells, int pageIndex) {
        if (pageIndex == 0) {
            int small = AutomatedExecutionDriver.rankSlotsFromPage(cells);
            if (small == 18) return new PageCapacity(18, true);
            if (cells.size() == 45 && AutomatedExecutionDriver.ownCells(cells) == 45)
                return new PageCapacity(45, false); // First page alone cannot distinguish 45 from 90.
        } else if (pageIndex == 1) {
            if (AutomatedExecutionDriver.lockedPage(cells)) return new PageCapacity(45, true);
            if (cells.size() == 45 && AutomatedExecutionDriver.ownCells(cells) == 45)
                return new PageCapacity(90, true);
        }
        return new PageCapacity(0, false);
    }

    static boolean apply(PageCapacity evidence) {
        if (Tuning.get("slots.auto_detect") < 0.5) return false;
        int capacity = evidence.slots();
        if ((capacity != 18 && capacity != 45 && capacity != 90)
                || (!evidence.exact() && Math.max(Tuning.get("slots.max"), Tuning.get("orders.bid_slots")) > capacity)) return false;
        if (Math.round(Tuning.get("slots.max")) == capacity
                && Math.round(Tuning.get("orders.bid_slots")) == capacity) return false;
        Tuning.learnAccountCapacity(capacity);
        DoughBayClient.LOGGER.info("DoughBay: server menu confirmed {} auction and order slots for this account", capacity);
        return true;
    }
}
