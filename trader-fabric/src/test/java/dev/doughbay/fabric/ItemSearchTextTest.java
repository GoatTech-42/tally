package dev.doughbay.fabric;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ItemSearchTextTest {
    @Test void ordinaryLettersDigitsAndSpacesBuildTheExpectedSearch() {
        assertEquals("Cooked Chicken", ItemSearchText.forItem("minecraft:cooked_chicken", "Cooked Chicken"));
        assertEquals("Disc Fragment 5", ItemSearchText.forItem("minecraft:disc_fragment_5", "Disc Fragment 5"));
        assertEquals("Block of Iron", ItemSearchText.forItem("minecraft:iron_block", "Block of Iron"));
    }
    @Test void reportedFoodDecorationsAreRefusedNotStrippedOrSent() {
        for (String label : new String[]{"Cooked Chicken \ud83c\udf57\ud83c\udf57", "Steak \ue001\ue001", "\u00a7aSteak", "Chicken\ufe0f", "Chicken\u200d"})
            assertThrows(IllegalArgumentException.class, () -> ItemSearchText.forItem("minecraft:cooked_chicken", label));
    }
    @Test void everyCharacterOutsideTheExplicitAsciiPolicyIsRefused() {
        for (String label : new String[]{"Chicken\nhelp", "Chicken\rhelp", "Chicken\tmore", "Chicken\u0000", "Chicken\u007f", "Chicken; help", "Chicken /pay", "Bottle o' Enchanting", "Disc Fragment - 5", "Chicken_1", "Chicken\ud800", "\u9e21\u8089"})
            assertThrows(IllegalArgumentException.class, () -> ItemSearchText.forItem("minecraft:cooked_chicken", label));
    }
    @Test void missingNamesHaveNoIdentifierFallback() {
        for (String label : new String[]{"", "  ", null})
            assertThrows(IllegalArgumentException.class, () -> ItemSearchText.forItem("minecraft:cooked_chicken", label));
    }
    @Test void malformedIdentifiersRemainRefused() {
        for (String id : new String[]{"minecraft:cooked_chicken\nhelp", "minecraft:Cooked_Chicken", "chicken", "minecraft:chicken;pay"})
            assertThrows(IllegalArgumentException.class, () -> ItemSearchText.forItem(id, "Chicken"));
    }
    @Test void sharedCommandPolicyAlsoRefusesUnicodeAndSpecialCharacters() {
        assertThrows(IllegalArgumentException.class, () -> OutgoingCommandText.requireValid("pay ExamplePlayer 123\u00a7"));
        assertFalse(OutgoingCommandText.valid("msg ExamplePlayer caf\u00e9"));
        assertFalse(OutgoingCommandText.valid("pay Example_Player 123"));
        assertFalse(OutgoingCommandText.valid("minecraft:msg ExamplePlayer hello"));
        assertFalse(OutgoingCommandText.valid("pay ExamplePlayer 1.5"));
        assertTrue(OutgoingCommandText.valid("pay ExamplePlayer 123"));
    }
}
