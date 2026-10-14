package dev.doughbay.fabric;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TuningKeysTest {
    @Test
    void noTwoSettingsShareAName() {
        // Two settings under one name: the later one's limits win without a
        // word, and a distance of 50 was cut to the other's 24.
        Set<String> seen = new HashSet<>();
        for (Tuning.Setting s : Tuning.SETTINGS) {
            assertTrue(seen.add(s.key()), "two settings are called " + s.key());
        }
    }

    @Test
    void theMobDistanceReachesFifty() {
        Tuning.Setting mobs = Tuning.SETTINGS.stream()
                .filter(s -> s.key().equals("evasion.mob_blocks")).findFirst().orElseThrow();
        assertTrue(mobs.max() >= 50, "limit is " + mobs.max());
        assertEquals(0, mobs.defaultValue(), 0.0);
    }
}
