package dev.doughbay.fabric;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Shared visual language for the cockpit, HUD, and notification cards. */
final class UiTheme {
    static int GOLD = 0xFFF4CD7A;
    static int TEAL = 0xFF56DCCB;
    static final int TEXT = 0xFFF0EEE7;
    static final int SECONDARY = 0xFFB6C2CD;
    static final int MUTED = 0xFF8799AA;
    static int BACKGROUND = 0xFA0C121B;
    static int SURFACE = 0xF2182230;
    static int INSET = 0xF0101823;
    static int EDGE = 0xFF2C3B4C;
    static int HOVER = 0xFF243747;
    static final int GOOD = 0xFF77DEB0;
    static final int WARN = 0xFFF3B576;
    static final int BAD = 0xFFFF858C;

    private UiTheme() { }

    static void apply(Appearance.Theme theme) {
        switch (theme) {
            case CLASSIC -> { GOLD=0xFFF4CD7A; TEAL=0xFF56DCCB; BACKGROUND=0xFA0C121B; SURFACE=0xF2182230; INSET=0xF0101823; EDGE=0xFF2C3B4C; HOVER=0xFF243747; }
            case MIDNIGHT -> { GOLD=0xFFC1A7FF; TEAL=0xFF88CAFF; BACKGROUND=0xFA111020; SURFACE=0xF2222039; INSET=0xF0171629; EDGE=0xFF45405F; HOVER=0xFF393253; }
            case MINT -> { GOLD=0xFF9FE5BE; TEAL=0xFF7FE0DF; BACKGROUND=0xFA0C1917; SURFACE=0xF2182C28; INSET=0xF010211E; EDGE=0xFF325349; HOVER=0xFF29483F; }
            case ROSE -> { GOLD=0xFFFFB4CC; TEAL=0xFFFFD3AE; BACKGROUND=0xFA1C101A; SURFACE=0xF230202E; INSET=0xF0251522; EDGE=0xFF583A50; HOVER=0xFF493046; }
        }
    }
    static boolean reducedMotion() { return Tuning.get("hud.reduced_motion") >= 0.5; }

    static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
        if (w <= 0 || h <= 0) return;
        rounded(g, x + 2, y + 3, w, h, 7, 0x50000000);
        rounded(g, x, y, w, h, 7, EDGE);
        rounded(g, x + 1, y + 1, w - 2, h - 2, 6, color);
    }

    static void rounded(GuiGraphicsExtractor g, int x, int y, int w, int h, int radius, int color) {
        if (w <= 0 || h <= 0) return;
        int r = Math.max(0, Math.min(radius, Math.min(w, h) / 2));
        g.fill(x, y + r, x + w, y + h - r, color);
        for (int row = 0; row < r; row++) {
            double dy = r - row - 0.5;
            int inset = (int) Math.ceil(r - Math.sqrt(r * r - dy * dy));
            g.fill(x + inset, y + row, x + w - inset, y + row + 1, color);
            g.fill(x + inset, y + h - row - 1, x + w - inset, y + h - row, color);
        }
    }

    static String fit(Font font, String value, int width) {
        if (value == null || width <= 0) return "";
        if (font.width(value) <= width) return value;
        String end = "…";
        if (font.width(end) > width) return "";
        return font.plainSubstrByWidth(value, width - font.width(end)) + end;
    }
}
