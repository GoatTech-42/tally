package dev.doughbay.fabric;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HiveSplitTest {
    private static final String[] ITEMS = {"diamond", "emerald_block", "wither_skeleton_skull", "white_stained_glass",
            "glass_bottle", "golden_carrot", "black_concrete", "sea_lantern", "bone_block", "tnt", "coal", "gunpowder",
            "cooked_beef", "experience_bottle", "deepslate_diamond_ore", "blue_stained_glass", "hopper", "observer",
            "ender_chest", "golden_apple", "cobweb", "anvil", "beacon", "netherite_scrap"};

    @BeforeEach
    void forget() {
        HiveSplit.reset();
    }

    @Test
    void everyMarketHasExactlyOneAccountBuyingInIt() {
        List<String> two = HiveSplit.held(List.of("TraderOne", "seller_two"), 1_000);
        assertEquals(List.of("seller_two", "traderone"), two);
        int first = 0;
        for (String item : ITEMS) {
            boolean a = HiveSplit.mine("minecraft:" + item, "TraderOne", two, 0);
            boolean b = HiveSplit.mine(item, "Seller_Two", two, 0);
            // One of them and never both, whichever way the item or the name is written.
            assertTrue(a != b, item);
            if (a) first++;
        }
        // Neither is left with nothing to trade.
        assertTrue(first >= 6 && first <= ITEMS.length - 6, "one account got " + first + " of " + ITEMS.length);
        // Three accounts: still exactly one each.
        List<String> three = HiveSplit.held(List.of("TraderOne", "seller_two", "ThirdOne"), 1_000);
        for (String item : ITEMS) {
            int owners = 0;
            for (String who : three) if (HiveSplit.mine(item, who, three, 0)) owners++;
            assertEquals(1, owners, item);
        }
    }

    @Test
    void anAccountAloneOrNotYetCountedBuysEverywhere() {
        List<String> alone = HiveSplit.held(List.of("TraderOne"), 1_000);
        for (String item : ITEMS) assertTrue(HiveSplit.mine(item, "TraderOne", alone, 0));
        List<String> two = HiveSplit.held(List.of("TraderOne", "seller_two"), 2_000);
        // Its own name not known, or not on the roster yet: it does not stand aside for a split it is not part of.
        for (String item : ITEMS) {
            assertTrue(HiveSplit.mine(item, "", two, 0));
            assertTrue(HiveSplit.mine(item, "Newcomer", two, 0));
        }
    }

    @Test
    void anAccountThatRestartsKeepsItsMarketsForTenMinutes() {
        HiveSplit.held(List.of("TraderOne", "seller_two"), 0);
        // The sibling is gone from the ledger for a minute: its markets are still its own.
        List<String> during = HiveSplit.held(List.of("TraderOne"), 60_000);
        assertEquals(2, during.size());
        // Ten minutes on and it has not come back: the one left takes everything.
        List<String> after = HiveSplit.held(List.of("TraderOne"), HiveSplit.HOLD_MILLIS + 61_000);
        assertEquals(List.of("traderone"), after);
        for (String item : ITEMS) assertTrue(HiveSplit.mine(item, "TraderOne", after, 0));
    }

    @Test
    void anotherSeedDealsTheMarketsOutDifferently() {
        List<String> two = HiveSplit.held(List.of("TraderOne", "seller_two"), 1_000);
        int moved = 0;
        for (String item : ITEMS) {
            if (HiveSplit.mine(item, "TraderOne", two, 0) != HiveSplit.mine(item, "TraderOne", two, 7)) moved++;
        }
        assertTrue(moved > 0);
        assertFalse(moved == ITEMS.length && ITEMS.length % 2 == 1);
    }
}
