package dev.doughbay.fabric;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RivalsFromFollowsTest {
    private static final long NOW = 1_800_000_000_000L;
    private static final long MIN = 60_000L;
    private static final String WART = "minecraft:nether_wart";

    private static Follows.Seen seen(long minutesAgo, String who, String kind, int quantity, String item, long price) {
        return new Follows.Seen(NOW - minutesAgo * MIN, who, kind, quantity, item, price);
    }

    private static RivalIntel.FollowBuild build(List<Follows.Seen> seen) {
        return RivalIntel.fromFollows(seen, Set.of(WART), "me", NOW, Set.of(), 3, 25, 60);
    }

    /** Orders 192 at 391 each, lists 64s at 86,000, orders again a quarter of an hour on: the pattern seen live. */
    private static List<Follows.Seen> restockingRival() {
        List<Follows.Seen> out = new ArrayList<>();
        out.add(seen(60, "traderone", "ordered", 192, WART, 75_000));
        for (int i = 0; i < 6; i++) out.add(seen(50 - i * 3, "traderone", "listed", 64, WART, 86_000));
        out.add(seen(20, "traderone", "ordered", 192, WART, 75_000));
        return out;
    }

    @Test
    void anOrderAndALaterListingOfTheSameItemIsAFlipAtTheOrdersPriceEach() {
        RivalIntel.FollowBuild build = build(restockingRival());
        assertEquals(1, build.rivals().size());
        RivalIntel.Rival rival = build.rivals().get(0);
        assertEquals("traderone", rival.name());
        assertEquals(6, rival.sales(), "listings, shown as such");
        assertEquals(1, rival.overlap());
        assertEquals(6, rival.flips().size());
        RivalIntel.Flip flip = rival.flips().get(0);
        assertEquals(25_000, flip.buyPrice(), "64 at 75,000 / 192 each");
        assertEquals(86_000, flip.listPrice());
        assertEquals(0, flip.soldPrice(), "nothing is known to have sold");
    }

    @Test
    void aMarketTheRivalKeepsReorderingIsOfferedForShadowingAndSaysItIsAsks() {
        RivalIntel.FollowBuild build = build(restockingRival());
        assertEquals(1, build.askShadows().size());
        RivalIntel.ShadowMarket market = build.askShadows().get(0);
        assertEquals(WART, market.itemId());
        assertEquals(64, market.count());
        assertEquals(25_000, market.medianBuy());
        assertEquals(86_000, market.medianSale());
        assertTrue(market.fromAsks());
    }

    @Test
    void oneOrderIsNotProofTheStockMoves() {
        List<Follows.Seen> once = new ArrayList<>(restockingRival());
        once.remove(once.size() - 1);
        // Enough activity to be profiled, but never seen to restock.
        once.add(seen(49, "traderone", "listed", 64, WART, 86_000));
        RivalIntel.FollowBuild build = build(once);
        assertEquals(1, build.rivals().size());
        assertTrue(build.askShadows().isEmpty());
    }

    @Test
    void aPlayerWhoOnlyComesAndGoesIsNotARivalAndWeAreNotOurOwn() {
        List<Follows.Seen> seen = new ArrayList<>();
        for (int i = 0; i < 20; i++) seen.add(seen(i, ".bedrockbuyer", i % 2 == 0 ? "joined" : "left", 0, "", 0));
        for (Follows.Seen s : restockingRival()) {
            seen.add(new Follows.Seen(s.at(), "me", s.kind(), s.quantity(), s.itemId(), s.price()));
        }
        RivalIntel.FollowBuild build = build(seen);
        assertTrue(build.rivals().isEmpty());
        assertFalse(build.askShadows().stream().anyMatch(m -> true));
    }

    @Test
    void anItemTheClientCouldNotNameIsLeftOut() {
        List<Follows.Seen> seen = new ArrayList<>();
        seen.add(seen(60, "traderone", "ordered", 64, "", 10_000));
        for (int i = 0; i < 9; i++) seen.add(seen(50 - i, "traderone", "listed", 64, "", 90_000));
        assertTrue(build(seen).rivals().isEmpty());
    }
}
