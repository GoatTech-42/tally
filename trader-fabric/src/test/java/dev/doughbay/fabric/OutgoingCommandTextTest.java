package dev.doughbay.fabric;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class OutgoingCommandTextTest {
    @Test void everyUtf16CharacterIsCheckedAgainstTheExactOwnerAllowlist() {
        String allowed=" ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        for(int cp=0;cp<=Character.MAX_VALUE;cp++) {
            char c=(char)cp;
            assertEquals(allowed.indexOf(c)>=0,OutgoingCommandText.valid("cmd "+c),"Character "+cp);
        }
        assertFalse(OutgoingCommandText.valid("cmd \ud83c\udf57"));
    }
    @Test void emptyMissingAndWhitespaceOnlyAreNotCommands() {
        assertFalse(OutgoingCommandText.valid(null));assertFalse(OutgoingCommandText.valid(""));
        assertFalse(OutgoingCommandText.valid("   "));assertFalse(OutgoingCommandText.valid("\t"));
        assertTrue(OutgoingCommandText.valid("ah search Cooked Chicken 123"));
    }
}
