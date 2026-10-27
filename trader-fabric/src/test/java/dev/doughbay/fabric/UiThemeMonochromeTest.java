package dev.doughbay.fabric;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tally house style: the palette is provably achromatic (R == G == B for every
 * theme color) and keeps WCAG-measurable contrast tiers on a dark background.
 * Status is carried by brightness steps plus words/signs, never by hue.
 */
class UiThemeMonochromeTest {

    @AfterEach
    void restoreClassic() {
        UiTheme.apply(Appearance.Theme.CLASSIC);
    }

    private static int r(int argb) { return (argb >> 16) & 0xFF; }
    private static int g(int argb) { return (argb >> 8) & 0xFF; }
    private static int b(int argb) { return argb & 0xFF; }

    private static void assertAchromatic(String name, int argb) {
        assertEquals(r(argb), g(argb), name + " red/green differ: " + Integer.toHexString(argb));
        assertEquals(g(argb), b(argb), name + " green/blue differ: " + Integer.toHexString(argb));
    }

    private static double luminance(int argb) {
        double sum = 0;
        double[] weights = {0.2126, 0.7152, 0.0722};
        int[] channels = {r(argb), g(argb), b(argb)};
        for (int i = 0; i < 3; i++) {
            double c = channels[i] / 255.0;
            sum += weights[i] * (c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4));
        }
        return sum;
    }

    private static double contrast(int foreground, int background) {
        double lf = luminance(foreground), lb = luminance(background);
        double lighter = Math.max(lf, lb), darker = Math.min(lf, lb);
        return (lighter + 0.05) / (darker + 0.05);
    }

    @Test
    void tallyThemeIsFullyAchromatic() {
        UiTheme.apply(Appearance.Theme.TALLY);
        assertAchromatic("GOLD", UiTheme.GOLD);
        assertAchromatic("TEAL", UiTheme.TEAL);
        assertAchromatic("TEXT", UiTheme.TEXT);
        assertAchromatic("SECONDARY", UiTheme.SECONDARY);
        assertAchromatic("MUTED", UiTheme.MUTED);
        assertAchromatic("BACKGROUND", UiTheme.BACKGROUND);
        assertAchromatic("SURFACE", UiTheme.SURFACE);
        assertAchromatic("INSET", UiTheme.INSET);
        assertAchromatic("EDGE", UiTheme.EDGE);
        assertAchromatic("HOVER", UiTheme.HOVER);
        assertAchromatic("GOOD", UiTheme.GOOD);
        assertAchromatic("WARN", UiTheme.WARN);
        assertAchromatic("BAD", UiTheme.BAD);
    }

    @Test
    void tallyThemeKeepsReadableContrastTiers() {
        UiTheme.apply(Appearance.Theme.TALLY);
        assertTrue(contrast(UiTheme.TEXT, UiTheme.BACKGROUND) >= 7.0,
                "TEXT on BACKGROUND must reach WCAG AAA: " + contrast(UiTheme.TEXT, UiTheme.BACKGROUND));
        assertTrue(contrast(UiTheme.SECONDARY, UiTheme.BACKGROUND) >= 4.5,
                "SECONDARY on BACKGROUND must reach WCAG AA: " + contrast(UiTheme.SECONDARY, UiTheme.BACKGROUND));
        assertTrue(contrast(UiTheme.MUTED, UiTheme.SURFACE) >= 3.0,
                "MUTED on SURFACE must stay legible: " + contrast(UiTheme.MUTED, UiTheme.SURFACE));
        assertTrue(contrast(UiTheme.EDGE, UiTheme.BACKGROUND) >= 1.25,
                "panel EDGE must stay visible on BACKGROUND: " + contrast(UiTheme.EDGE, UiTheme.BACKGROUND));
        assertTrue(contrast(UiTheme.GOLD, UiTheme.SURFACE) >= 4.5,
                "accent on SURFACE must reach WCAG AA: " + contrast(UiTheme.GOLD, UiTheme.SURFACE));
    }

    @Test
    void statusTiersAreDistinctBrightnessSteps() {
        UiTheme.apply(Appearance.Theme.TALLY);
        assertTrue(r(UiTheme.GOOD) > r(UiTheme.WARN), "GOOD must read brighter than WARN");
        assertTrue(r(UiTheme.WARN) > r(UiTheme.BAD), "WARN must read brighter than BAD");
        assertTrue(contrast(UiTheme.BAD, UiTheme.BACKGROUND) >= 3.0,
                "even the dimmest status stays legible: " + contrast(UiTheme.BAD, UiTheme.BACKGROUND));
    }

    @Test
    void sharedTextColorsAreAchromaticInEveryTheme() {
        for (Appearance.Theme theme : Appearance.Theme.values()) {
            UiTheme.apply(theme);
            assertAchromatic("TEXT/" + theme, UiTheme.TEXT);
            assertAchromatic("SECONDARY/" + theme, UiTheme.SECONDARY);
            assertAchromatic("MUTED/" + theme, UiTheme.MUTED);
            assertAchromatic("GOOD/" + theme, UiTheme.GOOD);
            assertAchromatic("WARN/" + theme, UiTheme.WARN);
            assertAchromatic("BAD/" + theme, UiTheme.BAD);
        }
    }
}
