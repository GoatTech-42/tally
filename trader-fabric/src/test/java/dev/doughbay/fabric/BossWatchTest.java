package dev.doughbay.fabric;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BossWatchTest {
    @Test
    void aWitherIsKnownByItsBarWhateverItIsCalled() {
        assertEquals("Wither", BossWatch.wither(List.of(new BossWatch.Bar("Wither", true))));
        // Renamed with a name tag: the bar still dims the sky.
        assertEquals("Bob", BossWatch.wither(List.of(new BossWatch.Bar("Bob", true))));
        // A server that stops its bars dimming the sky still calls it what it is.
        assertEquals("WITHER", BossWatch.wither(List.of(new BossWatch.Bar(" WITHER ", false))));
        assertEquals("Wither", BossWatch.wither(List.of(new BossWatch.Bar("", true))));
    }

    @Test
    void theServersOwnBarsAreNotOne() {
        assertNull(BossWatch.wither(List.of(new BossWatch.Bar("Restart in 5 minutes", false),
                new BossWatch.Bar("Ender Dragon", false))));
        assertNull(BossWatch.wither(List.of()));
        assertNull(BossWatch.wither(null));
        assertNull(BossWatch.wither(Arrays.asList((BossWatch.Bar) null)));
        // Found among others.
        assertEquals("Wither", BossWatch.wither(List.of(new BossWatch.Bar("Double XP", false),
                new BossWatch.Bar("Wither", true))));
    }

    @Test
    void aBarThatFollowsThePlayerIsGivenUpOn() {
        assertFalse(BossWatch.givesUp(0));
        assertFalse(BossWatch.givesUp(BossWatch.MAX_ESCAPES - 1));
        assertTrue(BossWatch.givesUp(BossWatch.MAX_ESCAPES));
    }
}
