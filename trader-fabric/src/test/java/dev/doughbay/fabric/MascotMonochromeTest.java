package dev.doughbay.fabric;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The mascot face draws in the same achromatic house style; Tally is the default look. */
class MascotMonochromeTest {

    private static Set<Integer> colorsDrawn(MascotAnimation.Pose pose) {
        Set<Integer> colors = new HashSet<>();
        MascotFace.draw(new MascotFace.Canvas() {
            @Override public void fill(int left, int top, int right, int bottom, int color) { colors.add(color); }
            @Override public void clip(int left, int top, int right, int bottom) { }
            @Override public void unclip() { }
        }, pose);
        return colors;
    }

    @Test
    void faceDrawsOnlyAchromaticColors() {
        MascotAnimation.Pose[] poses = {
                new MascotAnimation.Pose(0, 0, 1, 1, 0, 0.5, 0, 1, 0, 0, 0, -1),
                new MascotAnimation.Pose(0.5, -0.5, 1, 0.05, 0.8, 1, 1, 1.2, 0, 0, 0, -1),
                new MascotAnimation.Pose(0, 0, 1, 1, 0, 0, 0, 1, 1, 0.3, 0.7, -1),
                new MascotAnimation.Pose(0, 0, 1, 1, 0, 0, 0, 1, 0, 0, 0, 1.5),
        };
        for (MascotAnimation.Pose pose : poses) {
            Set<Integer> colors = colorsDrawn(pose);
            assertTrue(!colors.isEmpty(), "pose must draw something");
            for (int color : colors) {
                int r = (color >> 16) & 0xFF, g = (color >> 8) & 0xFF, b = color & 0xFF;
                assertTrue(r == g && g == b,
                        "face color must be achromatic: " + Integer.toHexString(color));
            }
        }
    }

    @Test
    void moneyReelBranchIsExercisedAndAchromatic() {
        // money=1 suppresses the eyes entirely, so only the $-reels draw; an
        // empty recording here would mean the reel branch silently vanished.
        Set<Integer> colors = colorsDrawn(new MascotAnimation.Pose(0, 0, 1, 1, 0, 0, 0, 1, 1, 0.3, 0.7, -1));
        assertTrue(colors.size() >= 3, "reel branch must draw the $ marks: " + colors);
        for (int color : colors) {
            int r = (color >> 16) & 0xFF, g = (color >> 8) & 0xFF, b = color & 0xFF;
            assertTrue(r == g && g == b, "reel ink must be achromatic: " + Integer.toHexString(color));
        }
    }

    @Test
    void tallyMarkDrawsOnlyAchromaticInk() {
        for (long clock : new long[]{0, 800, 1600, 2400, 3200, 4800}) {
            Set<Integer> colors = new HashSet<>();
            MascotCharacters.draw(new MascotFace.Canvas() {
                @Override public void fill(int left, int top, int right, int bottom, int color) { colors.add(color); }
                @Override public void clip(int left, int top, int right, int bottom) { }
                @Override public void unclip() { }
            }, Appearance.Character.TALLY, clock, false);
            assertTrue(!colors.isEmpty(), "tally mark must draw something");
            for (int color : colors) {
                int r = (color >> 16) & 0xFF, g = (color >> 8) & 0xFF, b = color & 0xFF;
                assertTrue(r == g && g == b,
                        "tally mark must be achromatic: " + Integer.toHexString(color));
            }
        }
    }

    @Test
    void tallyIsTheDefaultThemeAndMascot(@TempDir Path directory) {
        try {
            Appearance.load(directory);
            assertEquals(Appearance.Theme.TALLY, Appearance.theme());
            assertEquals(Appearance.Character.TALLY, Appearance.mascot());
        } finally {
            Appearance.load(directory);
            UiTheme.apply(Appearance.Theme.CLASSIC);
        }
    }
}
