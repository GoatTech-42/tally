package dev.doughbay.fabric.automation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ListCapTest {
    @Test
    void aPriceTheBotWorkedOutNeverGoesUpAtMoreThanThreeTimesCost() {
        assertEquals(717_000, AutomationSessionController.cappedListTarget(42_680_000, 239_000, false));
        assertEquals(600_000, AutomationSessionController.cappedListTarget(600_000, 239_000, false));
        // No cost on record: nothing to hold it against.
        assertEquals(42_680_000, AutomationSessionController.cappedListTarget(42_680_000, 0, false));
    }

    @Test
    void aPriceThePlayerSetGoesUpAsSet() {
        // Ordered at 2,600 to sell one at a time at 20,000: nearly eight times cost, and what was asked for.
        assertEquals(20_000, AutomationSessionController.cappedListTarget(20_000, 2_600, true));
        assertEquals(2_560_000, AutomationSessionController.cappedListTarget(2_560_000, 16_000, true));
    }
}
