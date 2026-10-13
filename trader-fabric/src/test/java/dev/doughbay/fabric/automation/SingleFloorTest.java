package dev.doughbay.fabric.automation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SingleFloorTest {
    @Test
    void aSingleThatEarnsUnderTheFloorIsNotSoldOneAtATime() {
        // Ender chests: 104 for 405,600 is 3,900 each, and a single goes for 5,500. 1,600 apiece.
        assertTrue(AutomationSessionController.singleEarnsTooLittle(5_500, 405_600, 104, 5_000));
        // Emerald blocks: 8,700 each and a single at 23,100. Well over.
        assertFalse(AutomationSessionController.singleEarnsTooLittle(23_100, 556_800, 64, 5_000));
        // Exactly at the floor is enough.
        assertFalse(AutomationSessionController.singleEarnsTooLittle(8_900, 3_900 * 64, 64, 5_000));
        assertTrue(AutomationSessionController.singleEarnsTooLittle(8_899, 3_900 * 64, 64, 5_000));
    }

    @Test
    void withNoFloorSetNothingChanges() {
        assertFalse(AutomationSessionController.singleEarnsTooLittle(5_500, 405_600, 104, 0));
        // Even a single that would sell at a loss: that is another rule's business.
        assertFalse(AutomationSessionController.singleEarnsTooLittle(100, 405_600, 104, 0));
        assertFalse(AutomationSessionController.singleEarnsTooLittle(5_500, 405_600, 0, 5_000));
    }
}
