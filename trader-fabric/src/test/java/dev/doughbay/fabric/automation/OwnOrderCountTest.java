package dev.doughbay.fabric.automation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OwnOrderCountTest {
    @Test
    void anOrderIsTheStacksAskedForWhileTheMoneyAllows() {
        // Three stacks of 64 at 80 each is 15,360; there is room for it.
        assertEquals(192, AutomationSessionController.ownOrderCount(64, 3, 80, 1_000_000));
        // Room for two stacks only: fewer stacks rather than no order.
        assertEquals(128, AutomationSessionController.ownOrderCount(64, 3, 80, 11_000));
        // Not even one stack fits, so nothing is ordered; never a part stack.
        assertEquals(0, AutomationSessionController.ownOrderCount(64, 3, 80, 5_000));
    }

    @Test
    void anOrderStaysUnderAThousandSoTheOrderHouseShowsItsCountExactly() {
        assertEquals(960, AutomationSessionController.ownOrderCount(64, 15, 10, Long.MAX_VALUE / 4));
        assertEquals(960, AutomationSessionController.ownOrderCount(64, 40, 10, Long.MAX_VALUE / 4));
        assertEquals(992, AutomationSessionController.ownOrderCount(16, 99, 10, Long.MAX_VALUE / 4));
    }

    @Test
    void somethingThatDoesNotStackIsOrderedByTheOne() {
        assertEquals(4, AutomationSessionController.ownOrderCount(1, 4, 1_000_000, 10_000_000));
        assertEquals(2, AutomationSessionController.ownOrderCount(1, 4, 1_000_000, 2_500_000));
        assertEquals(0, AutomationSessionController.ownOrderCount(1, 0, 1_000_000, 10_000_000));
        assertEquals(0, AutomationSessionController.ownOrderCount(64, 3, 0, 10_000_000));
    }
}
