package dev.doughbay.fabric;

import dev.doughbay.core.model.Position;
import dev.doughbay.core.model.PositionStatus;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

/**
 * What each item has done for the player, worked out from the ledger of
 * positions: every row here is something the bot really bought and sold, at
 * the prices it was really bought and sold for.
 *
 * <p>Profit ranks an item by what it brought in. The other figures say how:
 * how often it sells, what a sale is worth, what the money spent on it
 * returned, and how long a sale takes. The one that ties them together is
 * profit per slot-hour. Auction slots are what a player runs out of, so an
 * item is worth what it earns for each hour it holds one: a small profit that
 * sells in ten minutes beats a large one that sits for six hours.
 */
public final class ItemBoard {
    /**
     * The least slot time one sale is counted as. A stack that sells the
     * second it is listed did still cost the slot the time it takes to list
     * and collect, and without a floor a handful of such sales would put an
     * item's rate in the billions.
     */
    static final long SALE_FLOOR_MILLIS = 60_000L;

    private ItemBoard() { }

    /** The stretch of time a board covers. */
    public enum Range {
        TODAY("Today"), DAY("24 h"), WEEK("7 days"), ALL("All time");

        public final String label;

        Range(String label) {
            this.label = label;
        }

        public Range next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    /** What the rows are put in order by. */
    public enum Sort {
        PROFIT("Profit"), SALES("Sales"), PER_SALE("Per sale"), RETURN("Return"), SLOT_HOUR("Per slot-hour"),
        SELLS_IN("Sells in"), WIN_RATE("Win rate"), TIED_UP("Tied up"), TREND("Trend");

        public final String label;

        Sort(String label) {
            this.label = label;
        }

        public Sort next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    /**
     * One item over the board's range.
     *
     * @param cost             what the sold stock was bought for
     * @param revenue          what it sold for
     * @param slotMillis       how long its sales held an auction slot, each counted as at least a minute
     * @param medianSellMillis the middle time from listing to sale; 0 when none was timed
     * @param singleSales      sales of one item at a time, against {@code lotSales} of stacks and part stacks
     * @param open             listings and stock in hand right now, whatever the range
     * @param before           profit over the stretch of the same length just before this one
     * @param compared         whether there is such a stretch to compare with
     * @param bestHour         the hour of the day it earned most in, 0 to 23; -1 with no sales
     */
    public record Row(String itemId, String name, int sales, long units, long cost, long revenue, long profit,
                      int wins, int losses, long best, long worst, long slotMillis, long medianSellMillis,
                      int singleSales, long singleProfit, int lotSales, long lotProfit,
                      int open, long openCost, long oldestOpenMillis, long lastSoldAt,
                      long before, boolean compared, int bestHour) {
        public long perSale() {
            return sales == 0 ? 0 : Math.round(profit / (double) sales);
        }

        /** Profit as a percentage of the money that was spent to make it. */
        public double returnPercent() {
            return cost <= 0 ? 0 : profit * 100.0 / cost;
        }

        public long perSlotHour() {
            return slotMillis <= 0 ? 0 : Math.round(profit / (slotMillis / 3_600_000.0));
        }

        public double winRate() {
            return sales == 0 ? 0 : wins * 100.0 / sales;
        }

        /** Profit against the stretch before; 0 when there is none to compare with. */
        public long change() {
            return compared ? profit - before : 0;
        }
    }

    /**
     * @param from   where the range starts, in epoch milliseconds; 0 for all time
     * @param byHour profit by hour of the day across every item, 24 of them
     */
    public record Board(Range range, long builtAt, long from, List<Row> rows, int sales, long profit, long cost,
                        int open, long openCost, long[] byHour) {
        public static Board empty(Range range) {
            return new Board(range, 0, 0, List.of(), 0, 0, 0, 0, 0, new long[24]);
        }

        public double returnPercent() {
            return cost <= 0 ? 0 : profit * 100.0 / cost;
        }

        public Row row(String itemId) {
            if (itemId == null) return null;
            for (Row row : rows) if (row.itemId().equals(itemId)) return row;
            return null;
        }

        /**
         * The rows in order. {@code best} puts the most of whatever it is
         * first; for the time a sale takes, the best is the shortest. Items
         * with nothing sold in the range come last either way.
         */
        public List<Row> sorted(Sort sort, boolean best) {
            Comparator<Row> order = switch (sort) {
                case PROFIT -> Comparator.comparingLong(Row::profit);
                case SALES -> Comparator.comparingInt(Row::sales);
                case PER_SALE -> Comparator.comparingLong(Row::perSale);
                case RETURN -> Comparator.comparingDouble(Row::returnPercent);
                case SLOT_HOUR -> Comparator.comparingLong(Row::perSlotHour);
                // Quicker is better, and an item with no timed sale has no time at all.
                case SELLS_IN -> Comparator.comparingLong((Row r) ->
                        r.medianSellMillis() <= 0 ? Long.MIN_VALUE : -r.medianSellMillis());
                case WIN_RATE -> Comparator.comparingDouble(Row::winRate);
                case TIED_UP -> Comparator.comparingLong(Row::openCost);
                case TREND -> Comparator.comparingLong(Row::change);
            };
            if (best) order = order.reversed();
            Comparator<Row> unsoldLast = Comparator.comparing((Row r) -> r.sales() == 0 && sort != Sort.TIED_UP);
            List<Row> out = new ArrayList<>(rows);
            out.sort(unsoldLast.thenComparing(order).thenComparing(Row::name));
            return out;
        }
    }

    private static final class Tally {
        int sales;
        long units;
        long cost;
        long revenue;
        long profit;
        int wins;
        int losses;
        long best = Long.MIN_VALUE;
        long worst = Long.MAX_VALUE;
        long slotMillis;
        final List<Long> sellTimes = new ArrayList<>();
        int singleSales;
        long singleProfit;
        int lotSales;
        long lotProfit;
        int open;
        long openCost;
        long oldestOpenAt = Long.MAX_VALUE;
        long lastSoldAt;
        long before;
        final long[] byHour = new long[24];
    }

    static String baseKey(String itemKey) {
        if (itemKey == null) return "unknown";
        int hash = itemKey.indexOf('#');
        return hash >= 0 ? itemKey.substring(0, hash) : itemKey;
    }

    static long profitOf(Position p) {
        return Double.isFinite(p.realizedProfit()) ? Math.round(p.realizedProfit()) : p.salePrice() - p.purchasePrice();
    }

    /** Where {@code range} starts. Today starts at the player's own midnight. */
    static long start(Range range, long now, ZoneId zone) {
        return switch (range) {
            case TODAY -> Instant.ofEpochMilli(now).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli();
            case DAY -> now - 24 * 3_600_000L;
            case WEEK -> now - 7 * 24 * 3_600_000L;
            case ALL -> 0;
        };
    }

    /**
     * @param names an item's id to the name it is shown by
     */
    public static Board build(List<Position> positions, long now, Range range, ZoneId zone, UnaryOperator<String> names) {
        long from = start(range, now, zone);
        boolean compared = range != Range.ALL;
        // The stretch before, of the same length: yesterday up to this time of day, for today.
        long beforeTo = range == Range.TODAY ? now - 24 * 3_600_000L : from;
        long beforeFrom = range == Range.TODAY ? from - 24 * 3_600_000L : from - (now - from);
        Map<String, Tally> tallies = new LinkedHashMap<>();
        long[] byHour = new long[24];
        for (Position p : positions) {
            String item = baseKey(p.itemKey());
            if (p.status() == PositionStatus.LISTED || p.status() == PositionStatus.PURCHASED) {
                Tally t = tallies.computeIfAbsent(item, k -> new Tally());
                t.open++;
                t.openCost += Math.max(0, p.purchasePrice());
                long since = p.listedAt() > 0 ? p.listedAt() : p.purchasedAt();
                if (since > 0) t.oldestOpenAt = Math.min(t.oldestOpenAt, since);
                continue;
            }
            if (p.status() != PositionStatus.SOLD || p.closedAt() <= 0 || p.closedAt() > now) continue;
            long profit = profitOf(p);
            if (compared && p.closedAt() >= beforeFrom && p.closedAt() < beforeTo) {
                tallies.computeIfAbsent(item, k -> new Tally()).before += profit;
            }
            if (p.closedAt() < from) continue;
            Tally t = tallies.computeIfAbsent(item, k -> new Tally());
            t.sales++;
            t.units += Math.max(0, p.quantity());
            t.cost += Math.max(0, p.purchasePrice());
            t.revenue += Math.max(0, p.salePrice());
            t.profit += profit;
            if (profit > 0) t.wins++;
            else if (profit < 0) t.losses++;
            t.best = Math.max(t.best, profit);
            t.worst = Math.min(t.worst, profit);
            if (p.listedAt() > 0 && p.closedAt() >= p.listedAt()) {
                long took = p.closedAt() - p.listedAt();
                t.sellTimes.add(took);
                t.slotMillis += Math.max(SALE_FLOOR_MILLIS, took);
            } else {
                t.slotMillis += SALE_FLOOR_MILLIS;
            }
            if (p.quantity() <= 1) {
                t.singleSales++;
                t.singleProfit += profit;
            } else {
                t.lotSales++;
                t.lotProfit += profit;
            }
            t.lastSoldAt = Math.max(t.lastSoldAt, p.closedAt());
            int hour = Instant.ofEpochMilli(p.closedAt()).atZone(zone).getHour();
            t.byHour[hour] += profit;
            byHour[hour] += profit;
        }

        List<Row> rows = new ArrayList<>();
        int sales = 0;
        long profit = 0;
        long cost = 0;
        int open = 0;
        long openCost = 0;
        for (Map.Entry<String, Tally> e : tallies.entrySet()) {
            Tally t = e.getValue();
            // Something that only sold in the stretch before is not on this board.
            if (t.sales == 0 && t.open == 0) continue;
            Collections.sort(t.sellTimes);
            int bestHour = -1;
            for (int h = 0; h < 24; h++) {
                if (t.byHour[h] > 0 && (bestHour < 0 || t.byHour[h] > t.byHour[bestHour])) bestHour = h;
            }
            String name = names == null ? e.getKey() : names.apply(e.getKey());
            rows.add(new Row(e.getKey(), name == null || name.isBlank() ? e.getKey() : name, t.sales, t.units, t.cost,
                    t.revenue, t.profit, t.wins, t.losses, t.sales == 0 ? 0 : t.best, t.sales == 0 ? 0 : t.worst,
                    t.slotMillis, t.sellTimes.isEmpty() ? 0 : t.sellTimes.get(t.sellTimes.size() / 2),
                    t.singleSales, t.singleProfit, t.lotSales, t.lotProfit, t.open, t.openCost,
                    t.open == 0 || t.oldestOpenAt == Long.MAX_VALUE ? 0 : Math.max(0, now - t.oldestOpenAt),
                    t.lastSoldAt, t.before, compared, bestHour));
            sales += t.sales;
            profit += t.profit;
            cost += t.cost;
            open += t.open;
            openCost += t.openCost;
        }
        return new Board(range, now, from, List.copyOf(rows), sales, profit, cost, open, openCost, byHour);
    }
}
