package dev.doughbay.fabric.automation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PackSweepSkipTest {
    @Test
    void aStackTheBotMayNotTradeIsPassedOver() {
        // Four granite in the pack of someone trading one item of their own.
        assertTrue(AutomationSessionController.bookingRefuses(false, false, false));
        // One the bot gave up on, even though the item is allowed.
        assertTrue(AutomationSessionController.bookingRefuses(true, true, false));
        assertTrue(AutomationSessionController.bookingRefuses(true, false, true));
    }

    @Test
    void whatItMaySellIsStillSold() {
        assertFalse(AutomationSessionController.bookingRefuses(false, true, false));
        // Not allowed any more, but marked to be sold off: you own it, so sell it.
        assertFalse(AutomationSessionController.bookingRefuses(false, false, true));
    }
}
