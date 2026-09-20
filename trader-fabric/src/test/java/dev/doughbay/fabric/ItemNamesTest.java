package dev.doughbay.fabric;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemNamesTest {
    private static void offers(String shown, String singular) {
        List<String> candidates = ItemNames.candidates(shown);
        assertTrue(candidates.contains(singular), shown + " should offer " + singular + " but offered " + candidates);
    }

    @Test
    void theNameItselfIsTriedFirst() {
        // "Glass" and "Oak Leaves" are item names, not plurals.
        assertEquals("Glass", ItemNames.candidates("Glass").get(0));
        assertEquals("Oak Leaves", ItemNames.candidates("Oak Leaves").get(0));
    }

    @Test
    void pluralsAsTheServerPrintsThemFindTheirItem() {
        // All seen in follow notices on 2026-10-10.
        offers("Ghast Tears", "Ghast Tear");
        offers("Light Weighted Pressure Plates", "Light Weighted Pressure Plate");
        offers("Redstone Repeaters", "Redstone Repeater");
        offers("Blocks of Gold", "Block of Gold");
        offers("Blocks of Raw Gold", "Block of Raw Gold");
        offers("Totems of Undying", "Totem of Undying");
        offers("Bottles o' Enchanting", "Bottle o' Enchanting");
        offers("Minecarts with Chests", "Minecart with Chest");
        offers("Carrots on a Stick", "Carrot on a Stick");
        offers("Compasses", "Compass");
        offers("Torches", "Torch");
        offers("Bookshelves", "Bookshelf");
        offers("Sweet Berries", "Sweet Berry");
    }
}
