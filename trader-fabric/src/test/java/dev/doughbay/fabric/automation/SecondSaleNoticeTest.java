package dev.doughbay.fabric.automation;

import dev.doughbay.core.text.DonutSaleMessageParser.SaleNotice;
import org.junit.jupiter.api.Test;

import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Two sales of identical stacks close together: one is the watched listing's, the other a parked one's. */
class SecondSaleNoticeTest {
    private static SaleNotice bought(String buyer) {
        return new SaleNotice(buyer, "Bone Block", OptionalInt.empty(), 44_900, 44_999);
    }

    @Test
    void theFirstNoticeToMatchIsTheWatchedListingsOwn() {
        assertTrue(AutomationSessionController.ownNotice(null, bought("first_buyer")));
    }

    @Test
    void theSameNoticeSeenAgainIsStillItsOwn() {
        assertTrue(AutomationSessionController.ownNotice(bought("first_buyer"), bought("first_buyer")));
    }

    @Test
    void aSecondBuyerAtTheSamePriceBoughtAnotherStack() {
        // Not the watched listing's notice, and not a reason to stop: it goes to a parked listing.
        assertFalse(AutomationSessionController.ownNotice(bought("first_buyer"), bought("second_buyer")));
    }
}
