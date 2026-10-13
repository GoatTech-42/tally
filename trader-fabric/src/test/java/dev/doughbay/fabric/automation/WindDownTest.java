package dev.doughbay.fabric.automation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WindDownTest {
    @Test
    void aWindDownIsOverWhenNothingIsHeldListedQueuedOrOnOrder() {
        assertTrue(AutomationSessionController.windDownFinished(0, 0, 0, true, true, 0));
        // One listing still up, one stack still to list, one step still to take: each keeps it going.
        assertFalse(AutomationSessionController.windDownFinished(1, 0, 0, true, true, 0));
        assertFalse(AutomationSessionController.windDownFinished(0, 1, 0, true, true, 0));
        assertFalse(AutomationSessionController.windDownFinished(0, 0, 1, true, true, 0));
        // An order still open is money still out and goods still coming.
        assertFalse(AutomationSessionController.windDownFinished(0, 0, 0, true, true, 2));
    }

    @Test
    void ordersNobodyHasLookedAtSinceItBeganAreNotTakenForClosed() {
        // The list of orders is empty only because Your Orders has not been read yet.
        assertFalse(AutomationSessionController.windDownFinished(0, 0, 0, true, false, 0));
        // A player who does not use the order house at all has no orders to wait on.
        assertTrue(AutomationSessionController.windDownFinished(0, 0, 0, false, false, 0));
        assertFalse(AutomationSessionController.windDownFinished(3, 0, 0, false, false, 0));
    }
}
