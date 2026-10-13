package dev.doughbay.fabric;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MobWatchTest {
    @Test
    void aMobInSightWithinTheDistanceIsRunFrom() {
        assertTrue(MobWatch.threatens(35, 50, true));
        assertTrue(MobWatch.threatens(50, 50, true));
        assertFalse(MobWatch.threatens(51, 50, true));
        // Switched off.
        assertFalse(MobWatch.threatens(3, 0, true));
    }

    @Test
    void aMobOutOfSightOnlyCountsOnceItIsClose() {
        // In a cave thirty blocks down: within the distance, and no threat.
        assertFalse(MobWatch.threatens(30, 50, false));
        // Round the corner.
        assertTrue(MobWatch.threatens(MobWatch.CLOSE_BLOCKS, 50, false));
        assertTrue(MobWatch.threatens(4, 50, false));
        // A small distance still means what it says.
        assertFalse(MobWatch.threatens(9, 6, false));
    }

    @Test
    void theOnesThatLeaveYouAloneAreLeftAlone() {
        assertTrue(MobWatch.attacksUnprovoked("zombie"));
        assertTrue(MobWatch.attacksUnprovoked("creeper"));
        assertTrue(MobWatch.attacksUnprovoked("skeleton"));
        assertFalse(MobWatch.attacksUnprovoked("enderman"));
        assertFalse(MobWatch.attacksUnprovoked("zombified_piglin"));
        assertFalse(MobWatch.attacksUnprovoked(null));
    }

    @Test
    void aNightFullOfMobsIsNotANightOfTeleporting() {
        assertFalse(MobWatch.givesUp(0));
        assertFalse(MobWatch.givesUp(MobWatch.MAX_ESCAPES - 1));
        assertTrue(MobWatch.givesUp(MobWatch.MAX_ESCAPES));
    }
}
