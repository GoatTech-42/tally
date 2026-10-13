package dev.doughbay.fabric;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemListsTest {
    private interface Body {
        void run();
    }

    private static void keeping(Body body) {
        String allow = Tuning.text("items.allow");
        String deny = Tuning.text("items.deny");
        String tracked = Tuning.text("watch.tracked");
        double managed = Tuning.get("watch.tracked_managed");
        try {
            body.run();
        } finally {
            Tuning.setText("items.allow", allow);
            Tuning.setText("items.deny", deny);
            Tuning.setText("watch.tracked", tracked);
            Tuning.set("watch.tracked_managed", managed);
        }
    }

    @Test
    void aListCoversAnItemByItsNameOrByTheEndOfIt() {
        Set<String> list = Set.of("boots", "diamond");
        assertTrue(ItemLists.listMatches(list, "diamond"));
        assertTrue(ItemLists.listMatches(list, "netherite_boots"));
        // The end of the name after an underscore, not any name that happens to end the same way.
        assertFalse(ItemLists.listMatches(list, "diamond_block"));
        assertFalse(ItemLists.listMatches(list, "reboots"));
        assertFalse(ItemLists.listMatches(Set.of(), "diamond"));
        // With an equals sign the entry is that one item: plain glass, and not the stained glass that ends in it.
        Set<String> exact = Set.of("=glass", "boots");
        assertTrue(ItemLists.listMatches(exact, "glass"));
        assertFalse(ItemLists.listMatches(exact, "white_stained_glass"));
        assertFalse(ItemLists.listMatches(exact, "glass_bottle"));
        assertTrue(ItemLists.listMatches(exact, "iron_boots"));
        assertTrue(Tuning.tokenCovers("glass", "white_stained_glass"));
        assertFalse(Tuning.tokenCovers("=glass", "white_stained_glass"));
    }

    @Test
    void anItemIsOnOneListOrTheOtherNeverBoth() {
        keeping(() -> {
            Tuning.setText("items.allow", "Diamond; minecraft:emerald");
            Tuning.setText("items.deny", "mace");
            ItemLists.exclude(List.of("minecraft:emerald", "elytra", ""));
            assertEquals(Set.of("diamond"), Tuning.itemSet("items.allow"));
            assertEquals(Set.of("mace", "emerald", "elytra"), Tuning.itemSet("items.deny"));
            // What was there is kept in the order it was written, with the new ones after it.
            assertEquals("mace, emerald, elytra", Tuning.text("items.deny"));

            ItemLists.include(List.of("mace", "Golden Apple"));
            assertEquals("diamond, mace, golden_apple", Tuning.text("items.allow"));
            assertEquals("emerald, elytra", Tuning.text("items.deny"));

            ItemLists.clear(List.of("diamond", "elytra", "never_listed"));
            assertEquals("mace, golden_apple", Tuning.text("items.allow"));
            assertEquals("emerald", Tuning.text("items.deny"));
        });
    }

    @Test
    void theWatchListStartsAsTheConfigFilesAndIsItsOwnFromTheFirstEdit() {
        keeping(() -> {
            Tuning.set("watch.tracked_managed", 0);
            Tuning.setText("watch.tracked", "");
            List<String> config = List.of("minecraft:diamond", "minecraft:gold_ingot");
            assertEquals(Set.of("diamond", "gold_ingot"), ItemLists.tracked(config));

            // The first edit takes the config's list along, so tracking one more does not lose the rest.
            ItemLists.track(List.of("beacon"), true, config);
            assertEquals(1, Tuning.get("watch.tracked_managed"));
            assertEquals("diamond, gold_ingot, beacon", Tuning.text("watch.tracked"));

            // From then on the list is the whole truth, down to empty.
            ItemLists.track(List.of("diamond", "gold_ingot", "beacon"), false, config);
            assertEquals(Set.of(), ItemLists.tracked(config));
            assertEquals(Set.of(), ItemLists.tracked());
        });
    }
}
