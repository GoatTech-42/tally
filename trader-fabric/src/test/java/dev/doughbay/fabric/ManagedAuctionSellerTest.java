package dev.doughbay.fabric;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ManagedAuctionSellerTest {
    @Test void excludesBothAccountsWhenTheyShareAnItemMarket() {
        assertTrue(managed(List.of("Seller: CODY_RAVES")));
        assertTrue(managed(List.of("Seller: hangyDangler")));
        assertFalse(managed(List.of("Seller: OtherTrader")));
    }

    @Test void recognizedOwnershipFormatsAreCovered() {
        for (String field : List.of("Seller name: hangyDangler", "Owner = hangyDangler",
                "Listed by hangyDangler", "Sold by: hangyDangler")) {
            assertTrue(managed(List.of(field)), field);
        }
    }

    @Test void usernameMentionDoesNotMakeAnotherTradersListingOurs() {
        assertFalse(managed(List.of("A gift from cody_raves", "Seller: OtherTrader")));
        assertFalse(managed(List.of("Seller: cody_raves_extra")));
        assertFalse(managed(List.of("Price: $500", "hangyDangler")));
    }

    @Test void localAccountIsExcludedWithoutAMultiClientRoster() {
        assertTrue(AutomatedExecutionDriver.managedAuctionSeller(
                List.of("Seller: cody_raves"), "cody_raves", seller -> false));
        assertFalse(AutomatedExecutionDriver.managedAuctionSeller(null, "cody_raves", seller -> false));
    }

    private static boolean managed(List<String> tooltip) {
        return AutomatedExecutionDriver.managedAuctionSeller(tooltip,
                "cody_raves", seller -> seller.equalsIgnoreCase("hangyDangler"));
    }
}
