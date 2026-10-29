package dev.doughbay.fabric;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UpdateCheckBannerTopTest {
    private static final int HEIGHT = 82;   // largest banner: 22 + 5 lines*11 + 5
    private static final int GUI = 360;     // typical gui-scaled height

    // clearance: warning lane inactive vs active
    @Test void clearanceUnchangedWhenWarningInactive() {
        assertEquals(20, UpdateCheck.bannerClearance(20, false));
    }
    @Test void clearanceStacksBelowWarningWhenActive() {
        assertEquals(20 + StaffWatchAlert.PANEL_HEIGHT + 8, UpdateCheck.bannerClearance(20, true));
    }

    // rest top: never pushed back up into the clearance zone
    @Test void restsAtBaseWhenNoClearance() {
        assertEquals(UpdateCheck.BANNER_TOP, UpdateCheck.bannerTop(0));
    }
    @Test void smallClearanceStillRestsAtBase() {
        assertEquals(UpdateCheck.BANNER_TOP, UpdateCheck.bannerTop(5));
    }
    @Test void clearsStaffHudStackWhenShowing() {
        assertEquals(46, UpdateCheck.bannerTop(46));
    }
    @Test void boundaryClearanceEqualsBase() {
        assertEquals(UpdateCheck.BANNER_TOP, UpdateCheck.bannerTop(UpdateCheck.BANNER_TOP));
    }

    // fit policy: drawn whole or not at all
    @Test void fitsWithRoomToSpare() {
        assertTrue(UpdateCheck.bannerFits(46, HEIGHT, GUI));
    }
    @Test void fitsExactlyAtBottomBound() {
        int clearance = GUI - UpdateCheck.BANNER_TOP - HEIGHT;
        assertTrue(UpdateCheck.bannerFits(clearance, HEIGHT, GUI));
    }
    @Test void yieldsWhenClearancePushesPastBottom() {
        assertFalse(UpdateCheck.bannerFits(300, HEIGHT, GUI));
    }
    @Test void yieldsWhenViewportShorterThanBanner() {
        assertFalse(UpdateCheck.bannerFits(0, HEIGHT, 60));
    }
    @Test void warningActiveClearanceStillFitsNormalViewport() {
        int clearance = UpdateCheck.bannerClearance(46, true);
        assertTrue(UpdateCheck.bannerFits(clearance, HEIGHT, GUI));
    }
    // banner-only defer on the unmeasured transition frame (critical surfaces untouched)
    @Test void bannerDefersWhileStackUnmeasured() {
        assertTrue(UpdateCheck.bannerDefers(true, 12, HEIGHT, GUI));
    }
    @Test void bannerDoesNotDeferWhenMeasuredAndFitting() {
        assertFalse(UpdateCheck.bannerDefers(false, 46, HEIGHT, GUI));
    }
    @Test void bannerDefersWhenNotFittingEvenIfMeasured() {
        assertTrue(UpdateCheck.bannerDefers(false, 300, HEIGHT, GUI));
    }
}
