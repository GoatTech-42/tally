package dev.doughbay.fabric;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class NameMaskTest {
    private static boolean[] bits(String pattern) {
        boolean[] out = new boolean[pattern.length()];
        for (int i = 0; i < out.length; i++) out[i] = pattern.charAt(i) == 'x';
        return out;
    }

    @Test
    void theNameIsFoundWhereverItSitsInTheLine() {
        assertArrayEquals(bits("xxxxx"), NameMask.hiddenChars("steve", "steve"));
        assertArrayEquals(bits("...xxxxx.."), NameMask.hiddenChars(">> Steve <", "steve"));
        // Twice on one line, and case does not matter.
        assertArrayEquals(bits("xxxxx.xxxxx"), NameMask.hiddenChars("STEVE/steve", "Steve"));
    }

    @Test
    void aLineWithoutTheNameIsLeftAlone() {
        assertNull(NameMask.hiddenChars("$ 140M", "steve"));
        assertNull(NameMask.hiddenChars("stev", "steve"));
        assertNull(NameMask.hiddenChars("anything", ""));
        assertNull(NameMask.hiddenChars(null, "steve"));
    }

    @Test
    void aPlainStringHasTheNameWrappedInTheScrambleCodeAndNothingElseTouched() {
        String k = "§k";
        String r = "§r";
        assertEquals(k + "Steve" + r + " ++ $267.00m", NameMask.maskIn("Steve ++ $267.00m", "steve"));
        assertEquals("paid " + k + "steve" + r + " and " + k + "STEVE" + r, NameMask.maskIn("paid steve and STEVE", "Steve"));
        assertEquals("$ 140M", NameMask.maskIn("$ 140M", "steve"));
    }

    @Test
    void aStringDrawnThroughTwoLayersIsNotWrappedTwice() {
        String once = NameMask.maskIn("Steve ++ $267.00m", "steve");
        assertEquals(once, NameMask.maskIn(once, "steve"));
    }
}
