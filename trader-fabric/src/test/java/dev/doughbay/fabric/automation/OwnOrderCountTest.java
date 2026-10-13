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
    void aTotalAndABudgetEachSayHowManyMoreMayBeOrdered() {
        // Neither set: no limit of the entry's own.
        assertEquals(Long.MAX_VALUE, AutomationSessionController.ownAllowance(0, 0, 500, 40_000, 80));
        // 1,000 wanted and 640 bought: 360 to go, and none once they are all in.
        assertEquals(360, AutomationSessionController.ownAllowance(1_000, 0, 640, 51_200, 80));
        assertEquals(0, AutomationSessionController.ownAllowance(1_000, 0, 1_000, 80_000, 80));
        assertEquals(0, AutomationSessionController.ownAllowance(1_000, 0, 1_024, 81_920, 80));
        // A budget of 100,000 with 60,000 spent buys 500 more at 80 each.
        assertEquals(500, AutomationSessionController.ownAllowance(0, 100_000, 750, 60_000, 80));
        // Both: whichever runs out first.
        assertEquals(360, AutomationSessionController.ownAllowance(1_000, 100_000, 640, 51_200, 80));
        assertEquals(125, AutomationSessionController.ownAllowance(1_000, 61_200, 640, 51_200, 80));
    }

    @Test
    void hundredsOfSomethingThatDoesNotStackCanBeOnOneOrder() {
        // 200 totems at 40,000 each, with the money for them.
        assertEquals(200, AutomationSessionController.ownOrderCount(1, 200, 40_000, 10_000_000));
        // Money for 50 of them: 50 are ordered.
        assertEquals(50, AutomationSessionController.ownOrderCount(1, 200, 40_000, 2_000_000));
        // Still under a thousand, so the order house shows the count exactly.
        assertEquals(999, AutomationSessionController.ownOrderCount(1, 999, 10, Long.MAX_VALUE / 4));
        // Something that stacks is held to fifteen stacks whatever is written after the x.
        assertEquals(960, AutomationSessionController.ownOrderCount(64, 200, 10, Long.MAX_VALUE / 4));
    }

    @Test
    void somethingThatDoesNotStackIsOrderedByTheOne() {
        assertEquals(4, AutomationSessionController.ownOrderCount(1, 4, 1_000_000, 10_000_000));
        assertEquals(2, AutomationSessionController.ownOrderCount(1, 4, 1_000_000, 2_500_000));
        assertEquals(0, AutomationSessionController.ownOrderCount(1, 0, 1_000_000, 10_000_000));
        assertEquals(0, AutomationSessionController.ownOrderCount(64, 3, 0, 10_000_000));
    }
}
