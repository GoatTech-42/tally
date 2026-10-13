package dev.doughbay.fabric;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlipEditsTest {
    private static final String LIST = "observer 2600 20000 x15 singles, obsidian top+100 250 900 total 1000";

    @Test
    void aNewSellPriceChangesThatAndNothingElse() {
        FlipEdits.Result r = FlipEdits.sell(LIST, "minecraft:observer", "25k");
        assertTrue(r.ok());
        assertEquals("observer 2600 25000 x15 singles, obsidian top+100 250 900 total 1000", r.listText());
        assertEquals("Observer now sells at 25,000 each (was 20,000)", r.message());
        // However it is typed.
        assertEquals("observer 2600 22500 x15 singles, obsidian top+100 250 900 total 1000",
                FlipEdits.sell(LIST, "Observer", " $22,500 ").listText());
    }

    @Test
    void aPriceThatCannotBeRightChangesNothing() {
        // Under what it is bought for: the bot would stop pricing it at all.
        FlipEdits.Result under = FlipEdits.sell(LIST, "observer", "2000");
        assertFalse(under.ok());
        assertEquals(LIST, under.listText());
        assertEquals("The sell price has to be above the buy price (2,600)", under.message());
        assertFalse(FlipEdits.sell(LIST, "observer", "lots").ok());
        assertFalse(FlipEdits.sell(LIST, "observer", "").ok());
        assertFalse(FlipEdits.sell(LIST, "observer", "20000").ok());
        // Not on the list.
        assertEquals("Diamond is not on your Flips list", FlipEdits.sell(LIST, "diamond", "5000").message());
        assertFalse(FlipEdits.buy(LIST, "observer", "20000").ok());
    }

    @Test
    void aNewBuyPriceKeepsHowTheOrderIsPlaced() {
        FlipEdits.Result r = FlipEdits.buy(LIST, "obsidian", "300");
        assertTrue(r.ok());
        assertEquals("observer 2600 20000 x15 singles, obsidian top+100 300 900 total 1000", r.listText());
        assertEquals("Obsidian is now bought at up to 300 each (was 250)", r.message());
    }

    @Test
    void anEntryTypedOutIsAddedOrPutInPlaceOfTheOld() {
        FlipEdits.Result added = FlipEdits.add(LIST, "white stained glass 1150 1500");
        assertTrue(added.ok());
        assertEquals(LIST + ", white_stained_glass 1150 1500", added.listText());
        assertEquals("White stained glass added: buy 1,150, sell 1,500 each", added.message());
        FlipEdits.Result changed = FlipEdits.add(LIST, "obsidian 200 800");
        assertEquals("observer 2600 20000 x15 singles, obsidian 200 800", changed.listText());
        assertTrue(changed.message().startsWith("Obsidian changed"));
        // Onto an empty list.
        assertEquals("obsidian 250 900", FlipEdits.add("", "obsidian 250 900").listText());
        // What cannot be read is said, and the list is left alone.
        FlipEdits.Result bad = FlipEdits.add(LIST, "obsidian 900 250");
        assertFalse(bad.ok());
        assertEquals(LIST, bad.listText());
        assertFalse(FlipEdits.add(LIST, "  ").ok());
    }

    @Test
    void anItemIsTakenOff() {
        FlipEdits.Result r = FlipEdits.remove(LIST, "observer");
        assertTrue(r.ok());
        assertEquals("obsidian top+100 250 900 total 1000", r.listText());
        assertFalse(FlipEdits.remove(LIST, "diamond").ok());
    }
}
