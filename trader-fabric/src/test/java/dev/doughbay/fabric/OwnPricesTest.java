package dev.doughbay.fabric;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OwnPricesTest {
    @Test
    void aListIsReadAsItemBuyPriceAndSellPrice() {
        OwnPrices.Parsed list = OwnPrices.parse("diamond 80 120, golden apple 4k 5.2k x3; minecraft:ender_chest: 2400 -> 6400 2");
        assertEquals(List.of(), list.problems());
        assertEquals(new OwnPrices.Entry("diamond", 80, 120, 0), list.entries().get("diamond"));
        assertEquals(new OwnPrices.Entry("golden_apple", 4_000, 5_200, 3), list.entries().get("golden_apple"));
        assertEquals(new OwnPrices.Entry("ender_chest", 2_400, 6_400, 2), list.entries().get("ender_chest"));
        // In the order they were written, which is the order they are ordered in.
        assertEquals(List.of("diamond", "golden_apple", "ender_chest"), List.copyOf(list.entries().keySet()));
    }

    @Test
    void theWordsPeopleWriteRoundThePricesAreReadPast() {
        OwnPrices.Parsed list = OwnPrices.parse("Emerald Block buy 9k sell 10.5k x 4\nbeacon = 1.2m > 1.5m");
        assertEquals(List.of(), list.problems());
        assertEquals(new OwnPrices.Entry("emerald_block", 9_000, 10_500, 4), list.entries().get("emerald_block"));
        assertEquals(new OwnPrices.Entry("beacon", 1_200_000, 1_500_000, 0), list.entries().get("beacon"));
    }

    @Test
    void anEntryThatWouldLoseMoneyOrCannotBeReadIsLeftOutAndSaidSo() {
        OwnPrices.Parsed list = OwnPrices.parse("diamond 120 80, emerald 50, , redstone 10 20 x9999, coal 5 9 hello, "
                + "iron_ingot 30 45, iron ingot 31 46, 80 120");
        // The one good entry still stands.
        assertEquals(List.of("iron_ingot"), List.copyOf(list.entries().keySet()));
        assertEquals(30, list.entries().get("iron_ingot").buy());
        assertEquals(6, list.problems().size());
        assertTrue(list.problems().get(0).contains("above the buy price"));
        assertTrue(list.problems().get(1).contains("\"emerald 50\""));
        assertTrue(list.problems().get(2).contains("redstone 10 20 x9999"));
        // Up to 999 can be written: for something that does not stack it is how many an order asks for.
        assertEquals(200, OwnPrices.parse("totem of undying 40k 50k x200").entries().get("totem_of_undying").stacks());
        assertTrue(list.problems().get(4).contains("twice"));
    }

    @Test
    void theWordSinglesSellsAnItemOneAtATime() {
        OwnPrices.Parsed list = OwnPrices.parse("observer 500 900 x15 singles, diamond 80 120, hopper 2k 3k peel");
        assertEquals(List.of(), list.problems());
        assertEquals(new OwnPrices.Entry("observer", 500, 900, 15, true), list.entries().get("observer"));
        assertEquals(false, list.entries().get("diamond").singles());
        assertEquals(new OwnPrices.Entry("hopper", 2_000, 3_000, 0, true), list.entries().get("hopper"));
        // Only as a word after the item. First in an entry it is taken for a name, and no item has it:
        // the order desk says so when it comes to order it.
        assertEquals(false, OwnPrices.parse("singles 5 9").entries().get("singles").singles());
    }

    @Test
    void anOrderCanFollowTheTopOfTheBookAndStopAtATotalOrABudget() {
        OwnPrices.Parsed list = OwnPrices.parse(
                "observer top+100 600 900 x15 singles total 1000, totem of undying top max 45k 50k budget 2m, diamond 80 120");
        assertEquals(List.of(), list.problems());
        OwnPrices.Entry observer = list.entries().get("observer");
        assertEquals(new OwnPrices.Entry("observer", 600, 900, 15, true, 100, 1000, 0), observer);
        assertTrue(observer.top());
        OwnPrices.Entry totem = list.entries().get("totem_of_undying");
        assertEquals(new OwnPrices.Entry("totem_of_undying", 45_000, 50_000, 0, false, 0, 0, 2_000_000), totem);
        // An entry with none of it is an order at the price written, with no limit of its own.
        OwnPrices.Entry diamond = list.entries().get("diamond");
        assertEquals(false, diamond.top());
        assertEquals(0, diamond.total());
        // A changed price or total is a new entry as far as counting what was bought goes.
        assertEquals(false, observer.signature().equals(OwnPrices.parse("observer top+100 600 900 total 2000")
                .entries().get("observer").signature()));

        // "total" and "budget" each want a number after them, and top goes before the prices.
        assertEquals(1, OwnPrices.parse("observer 600 900 total").problems().size());
        assertEquals(1, OwnPrices.parse("observer 600 900 budget lots").problems().size());
        assertEquals(1, OwnPrices.parse("observer 600 900 top").problems().size());
    }

    @Test
    void aBidOverTheTopOrderNeverPassesTheMostThePlayerWillPay() {
        // 100 over the best order, as asked.
        assertEquals(550, OwnPrices.topBid(450, 100, 600));
        // One step, a hundredth and at least one, when no amount was given.
        assertEquals(455, OwnPrices.topBid(450, 0, 600));
        assertEquals(11, OwnPrices.topBid(10, 0, 600));
        // Being top would cost more than the most: an order under the best one is not filled, so none.
        assertEquals(0, OwnPrices.topBid(550, 100, 600));
        assertEquals(600, OwnPrices.topBid(500, 100, 600));
        // Nobody else is ordering it: there is nothing to be over.
        assertEquals(600, OwnPrices.topBid(0, 100, 600));
    }

    @Test
    void anEmptyListPricesNothing() {
        assertTrue(OwnPrices.parse("").entries().isEmpty());
        assertTrue(OwnPrices.parse(null).entries().isEmpty());
        assertTrue(OwnPrices.parse(" , ; ").problems().isEmpty());
    }

    @Test
    void anItemIsFoundByItsIdWithOrWithoutTheNamespaceAndNeverByAVariant() {
        String before = Tuning.text(OwnPrices.KEY);
        try {
            Tuning.setText(OwnPrices.KEY, "diamond 80 120");
            assertNotNull(OwnPrices.of("minecraft:diamond"));
            assertEquals(120, OwnPrices.of("diamond").sell());
            assertNull(OwnPrices.of("minecraft:emerald"));
            // An enchanted or damaged copy is a different thing from the plain item the price was set for.
            assertNull(OwnPrices.of("minecraft:diamond#abc"));
            assertNull(OwnPrices.of(null));
            // The list is read again when it is changed, with nothing to restart.
            Tuning.setText(OwnPrices.KEY, "emerald 50 70");
            assertNull(OwnPrices.of("minecraft:diamond"));
            assertEquals(50, OwnPrices.of("minecraft:emerald").buy());
            Tuning.setText(OwnPrices.KEY, "");
            assertNull(OwnPrices.of("minecraft:emerald"));
        } finally {
            Tuning.setText(OwnPrices.KEY, before);
        }
    }
}
