package dev.doughbay.fabric;

import dev.doughbay.core.model.Position;
import dev.doughbay.core.model.PositionStatus;
import dev.doughbay.core.model.StackBucket;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemBoardTest {
    private static final ZoneId UTC = ZoneId.of("UTC");
    private static final long NOW = Instant.parse("2026-10-11T15:00:00Z").toEpochMilli();
    private static final long MINUTE = 60_000L;
    private static final long HOUR = 3_600_000L;
    private static long ids = 1;

    /** A sale: bought for {@code cost}, listed {@code heldFor} before it sold {@code ago} before now. */
    private static Position sold(String item, int quantity, long cost, long sale, long ago, long heldFor) {
        long closed = NOW - ago;
        return new Position(ids++, "REAL", item, StackBucket.of(quantity), quantity, cost, sale,
                closed - heldFor - MINUTE, closed - heldFor, closed, sale, sale - cost, PositionStatus.SOLD);
    }

    private static Position listed(String item, int quantity, long cost, long listedAgo) {
        return new Position(ids++, "REAL", item, StackBucket.of(quantity), quantity, cost, cost * 2,
                NOW - listedAgo - MINUTE, NOW - listedAgo, 0, 0, Double.NaN, PositionStatus.LISTED);
    }

    private static ItemBoard.Board board(List<Position> positions, ItemBoard.Range range) {
        return ItemBoard.build(positions, NOW, range, UTC, id -> id.replace("minecraft:", ""));
    }

    @Test
    void anItemIsAddedUpFromItsSales() {
        List<Position> positions = List.of(
                sold("minecraft:tnt", 64, 100_000, 160_000, 2 * HOUR, 30 * MINUTE),
                sold("minecraft:tnt", 64, 100_000, 140_000, 3 * HOUR, 90 * MINUTE),
                sold("minecraft:tnt", 64, 100_000, 90_000, 4 * HOUR, 60 * MINUTE),
                // The same item under a key with its parts on the end is the same item.
                sold("minecraft:tnt#abc", 1, 2_000, 5_000, 5 * HOUR, 10 * MINUTE));
        ItemBoard.Row tnt = board(positions, ItemBoard.Range.DAY).row("minecraft:tnt");
        assertNotNull(tnt);
        assertEquals("tnt", tnt.name());
        assertEquals(4, tnt.sales());
        assertEquals(193, tnt.units());
        assertEquals(302_000, tnt.cost());
        assertEquals(395_000, tnt.revenue());
        assertEquals(93_000, tnt.profit());
        assertEquals(3, tnt.wins());
        assertEquals(1, tnt.losses());
        assertEquals(60_000, tnt.best());
        assertEquals(-10_000, tnt.worst());
        assertEquals(23_250, tnt.perSale());
        assertEquals(75.0, tnt.winRate());
        assertEquals(93_000 * 100.0 / 302_000, tnt.returnPercent(), 1e-9);
        // The middle of 10, 30, 60 and 90 minutes, taking the upper of the two in the middle.
        assertEquals(60 * MINUTE, tnt.medianSellMillis());
        // 190 minutes of slot time for 93,000.
        assertEquals(190 * MINUTE, tnt.slotMillis());
        assertEquals(Math.round(93_000 / (190 / 60.0)), tnt.perSlotHour());
        assertEquals(1, tnt.singleSales());
        assertEquals(3_000, tnt.singleProfit());
        assertEquals(3, tnt.lotSales());
        assertEquals(90_000, tnt.lotProfit());
        // 13:00 UTC is the hour its best sale closed in.
        assertEquals(13, tnt.bestHour());
    }

    @Test
    void aSaleThatClosedAtOnceStillCostsTheSlotAMinute() {
        // Twenty singles each sold two seconds after listing: forty seconds of real slot time.
        List<Position> positions = new ArrayList<>();
        for (int i = 0; i < 20; i++) positions.add(sold("minecraft:diamond", 1, 5_000, 15_000, HOUR + i * MINUTE, 2_000));
        ItemBoard.Row diamond = board(positions, ItemBoard.Range.DAY).row("minecraft:diamond");
        assertEquals(20 * MINUTE, diamond.slotMillis());
        assertEquals(600_000, diamond.perSlotHour());
        assertEquals(2_000, diamond.medianSellMillis());
    }

    @Test
    void theRangeDecidesWhichSalesCountAndWhatTheyAreComparedWith() {
        List<Position> positions = List.of(
                sold("minecraft:tnt", 64, 100_000, 150_000, 2 * HOUR, 10 * MINUTE),      // today and the last day
                sold("minecraft:tnt", 64, 100_000, 130_000, 20 * HOUR, 10 * MINUTE),     // yesterday, inside 24 h
                sold("minecraft:tnt", 64, 100_000, 120_000, 30 * HOUR, 10 * MINUTE),     // the day before's stretch
                sold("minecraft:tnt", 64, 100_000, 110_000, 5 * 24 * HOUR, 10 * MINUTE)); // this week
        ItemBoard.Row day = board(positions, ItemBoard.Range.DAY).row("minecraft:tnt");
        assertEquals(2, day.sales());
        assertEquals(80_000, day.profit());
        assertTrue(day.compared());
        assertEquals(20_000, day.before());
        assertEquals(60_000, day.change());

        // Today began at midnight UTC, fifteen hours ago: one sale. It is set against yesterday
        // up to the same time of day, which holds the sale made at 09:00 and not the one at 19:00.
        ItemBoard.Row today = board(positions, ItemBoard.Range.TODAY).row("minecraft:tnt");
        assertEquals(1, today.sales());
        assertEquals(50_000, today.profit());
        assertEquals(20_000, today.before());

        ItemBoard.Row week = board(positions, ItemBoard.Range.WEEK).row("minecraft:tnt");
        assertEquals(4, week.sales());
        assertEquals(110_000, week.profit());

        ItemBoard.Row all = board(positions, ItemBoard.Range.ALL).row("minecraft:tnt");
        assertEquals(4, all.sales());
        assertFalse(all.compared());
        assertEquals(0, all.change());
    }

    @Test
    void stockOnTheShelfIsShownWhateverTheRange() {
        List<Position> positions = List.of(
                listed("minecraft:anvil", 1, 300_000, 5 * HOUR),
                listed("minecraft:anvil", 1, 300_000, 2 * HOUR),
                sold("minecraft:tnt", 64, 100_000, 150_000, 2 * HOUR, 10 * MINUTE),
                // Sold long ago and nothing up now: not on a board of the last day.
                sold("minecraft:sculk", 64, 10_000, 60_000, 10 * 24 * HOUR, 10 * MINUTE));
        ItemBoard.Board board = board(positions, ItemBoard.Range.DAY);
        ItemBoard.Row anvil = board.row("minecraft:anvil");
        assertEquals(0, anvil.sales());
        assertEquals(2, anvil.open());
        assertEquals(600_000, anvil.openCost());
        assertEquals(5 * HOUR, anvil.oldestOpenMillis());
        assertEquals(-1, anvil.bestHour());
        assertNull(board.row("minecraft:sculk"));
        assertEquals(1, board.sales());
        assertEquals(50_000, board.profit());
        assertEquals(2, board.open());
        assertEquals(600_000, board.openCost());
        assertEquals(50_000, board.byHour()[13]);
    }

    @Test
    void theRowsCanBePutInOrderByAnyOfIt() {
        List<Position> positions = List.of(
                // Big and slow.
                sold("minecraft:beacon", 1, 1_000_000, 1_400_000, 3 * HOUR, 6 * HOUR),
                // Small and quick, twice.
                sold("minecraft:diamond", 1, 5_000, 15_000, 2 * HOUR, 2 * MINUTE),
                sold("minecraft:diamond", 1, 5_000, 15_000, 1 * HOUR, 2 * MINUTE),
                // A loss.
                sold("minecraft:dirt", 64, 10_000, 4_000, 4 * HOUR, 30 * MINUTE),
                // Nothing sold, money sitting.
                listed("minecraft:anvil", 1, 300_000, 5 * HOUR));
        ItemBoard.Board board = board(positions, ItemBoard.Range.DAY);
        assertEquals(List.of("beacon", "diamond", "dirt", "anvil"), names(board.sorted(ItemBoard.Sort.PROFIT, true)));
        // Worst first, and what has not sold at all still comes last.
        assertEquals(List.of("dirt", "diamond", "beacon", "anvil"), names(board.sorted(ItemBoard.Sort.PROFIT, false)));
        assertEquals("diamond", board.sorted(ItemBoard.Sort.SALES, true).get(0).name());
        // 20,000 in four minutes of slot against 400,000 in six hours.
        assertEquals("diamond", board.sorted(ItemBoard.Sort.SLOT_HOUR, true).get(0).name());
        assertEquals("diamond", board.sorted(ItemBoard.Sort.RETURN, true).get(0).name());
        // The quickest to sell is the best of "sells in".
        assertEquals(List.of("diamond", "dirt", "beacon", "anvil"), names(board.sorted(ItemBoard.Sort.SELLS_IN, true)));
        assertEquals("anvil", board.sorted(ItemBoard.Sort.TIED_UP, true).get(0).name());
    }

    private static List<String> names(List<ItemBoard.Row> rows) {
        return rows.stream().map(ItemBoard.Row::name).toList();
    }
}
