package dev.doughbay.fabric.automation;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RecoveryListingEvidenceTest {
    @Test void keylessRecoveryNeedsTwoLaterCompleteAuditsAndAnotherFreshRecheck() {
        var e = new RecoveryListingEvidence(); e.begin(100);
        assertFalse(e.accept(101, 200, true, 0, true, false));
        assertTrue(e.accept(201, 300, true, 0, true, false));
        assertEquals(300, e.readyAt());
        assertNotNull(e.finalBlocker(301, 100));
        assertTrue(e.accept(301, 400, true, 0, true, false));
        assertEquals(300, e.readyAt(), "later rechecks must not move the two-check boundary");
        assertNull(e.finalBlocker(450, 100));
        assertNotNull(e.finalBlocker(501, 100));
        assertNotNull(e.finalBlocker(399, 100));
    }
    @Test void partialUnreadableMatchingOrInventoryEvidenceCannotClearAHold() {
        for (int failure = 0; failure < 4; failure++) {
            var e = new RecoveryListingEvidence(); e.begin(100);
            e.accept(101, 200, true, 0, true, false);
            assertFalse(e.accept(201, 300, failure != 0, failure == 1 ? 1 : 0,
                    failure != 2, failure == 3));
            assertEquals(0, e.readyAt());
            assertNotNull(e.finalBlocker(301, 100));
            assertFalse(e.accept(301, 400, true, 0, true, false));
            assertTrue(e.accept(401, 500, true, 0, true, false));
        }
    }
    @Test void cachedPreConfirmationAndOverlappingAuditsAreNotNewEvidence() {
        var e = new RecoveryListingEvidence(); e.begin(100);
        assertFalse(e.accept(100, 200, true, 0, true, false));
        assertFalse(e.accept(101, 200, true, 0, true, false));
        assertFalse(e.accept(199, 300, true, 0, true, false));
        assertNotNull(e.finalBlocker(301, 100));
        e.begin(0);
        assertFalse(e.accept(301, 400, true, 0, true, false));
    }
}
