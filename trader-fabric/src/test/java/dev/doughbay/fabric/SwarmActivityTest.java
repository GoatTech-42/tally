package dev.doughbay.fabric;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SwarmActivityTest {
    @Test void onlyRecentRealEventsAnimateAndDirectionsDiffer() {
        assertEquals(0, SwarmActivity.radius(0, 1000, false, false));
        assertEquals(0, SwarmActivity.radius(1000, 999, false, false));
        assertEquals(0, SwarmActivity.radius(1000, 3400, true, false));
        assertTrue(SwarmActivity.radius(1000, 1500, false, false) > SwarmActivity.radius(1000, 1100, false, false));
        assertTrue(SwarmActivity.radius(1000, 1500, true, false) < SwarmActivity.radius(1000, 1100, true, false));
        assertEquals(SwarmActivity.radius(1000, 1500, false, true), SwarmActivity.radius(1000, 1100, false, true));
    }
}
