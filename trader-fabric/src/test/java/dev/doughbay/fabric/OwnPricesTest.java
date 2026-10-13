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
        OwnPrices.Parsed list = OwnPrices.parse("diamond 120 80, emerald 50, , redstone 10 20 x99, coal 5 9 hello, "
                + "iron_ingot 30 45, iron ingot 31 46, 80 120");
        // The one good entry still stands.
        assertEquals(List.of("iron_ingot"), List.copyOf(list.entries().keySet()));
        assertEquals(30, list.entries().get("iron_ingot").buy());
        assertEquals(6, list.problems().size());
        assertTrue(list.problems().get(0).contains("above the buy price"));
        assertTrue(list.problems().get(1).contains("\"emerald 50\""));
        assertTrue(list.problems().get(2).contains("at most 15 stacks"));
        assertTrue(list.problems().get(4).contains("twice"));
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
