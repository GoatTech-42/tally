package dev.doughbay.fabric.automation;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OrderDeskRecoveryTest {
    @Test void aFailedReadKeepsPriorityButDoesNotRetryEveryTick() {
        var recovery = new OrderDeskRecovery();
        assertFalse(recovery.pending());
        recovery.failed(1000);
        assertTrue(recovery.pending());
        assertFalse(recovery.ready(5999));
        assertTrue(recovery.ready(6000));
        assertEquals(1, recovery.remainingSeconds(5999));
    }

    @Test void repeatedFailuresBackOffToOneAttemptEveryFourMinutes() {
        var recovery = new OrderDeskRecovery();
        recovery.failed(1000);
        recovery.failed(6000);
        assertFalse(recovery.ready(20999));
        assertTrue(recovery.ready(21000));
        long now = 21000;
        for (long gap : new long[] {60_000, 120_000, 240_000, 240_000, 240_000}) {
            recovery.failed(now);
            assertFalse(recovery.ready(now + gap - 1));
            assertTrue(recovery.ready(now + gap));
            now += gap;
        }
    }

    @Test void anOrderHouseThatIsDownIsNotStoodAt() {
        var recovery = new OrderDeskRecovery();
        // The first two failures are a lost click as likely as not: everything waits for the retry.
        recovery.failed(1000);
        assertFalse(recovery.steppedAside(2000));
        recovery.failed(6000);
        assertFalse(recovery.steppedAside(7000));
        // The third is an order house that is down. Until the next try the session works on.
        recovery.failed(21000);
        assertTrue(recovery.steppedAside(21001));
        assertTrue(recovery.steppedAside(80999));
        // When the try comes due the desk has the floor again.
        assertFalse(recovery.steppedAside(81000));
        assertTrue(recovery.ready(81000));
        // And one page that comes ends it.
        recovery.recovered();
        assertFalse(recovery.steppedAside(81001));
    }

    @Test void aSuccessfulReadReleasesTheWatchAndResetsBackoff() {
        var recovery = new OrderDeskRecovery();
        recovery.failed(1000);
        recovery.failed(6000);
        recovery.recovered();
        assertFalse(recovery.pending());
        assertFalse(recovery.ready(100000));
        recovery.failed(100000);
        assertTrue(recovery.ready(105000));
    }

    @Test void navigationFailuresDoNotUseUpItemCollectionAttempts() {
        for (String detail : new String[]{
                "ABORTED: No progress at stage 0 of OWN_ORDERS on orders (page 1)",
                "ABORTED: Your Orders did not open (page: orders (page 1))",
                "No phase progress; orderStage=1, ticks=500",
                "ABORTED: Edit Order did not open (page: Loading order...)",
                "ABORTED: Collect Items did not open (page: Orders)",
                "ABORTED: Owned order rows changed before collection; refreshing Your Orders"}) {
            assertTrue(OrderDeskRecovery.navigationFailure(detail));
        }
        assertFalse(OrderDeskRecovery.navigationFailure("Nothing arrived from the order"));
        assertFalse(OrderDeskRecovery.navigationFailure("Edit Order shows no collect control"));
        assertFalse(OrderDeskRecovery.navigationFailure("The collect page keeps its stacks after 40 clicks"));
        assertFalse(OrderDeskRecovery.navigationFailure("The order was not on Your Orders"));
        assertFalse(OrderDeskRecovery.navigationFailure("No progress at stage 3 of COLLECT_ORDER"));
        assertFalse(OrderDeskRecovery.navigationFailure(null));
    }
}
