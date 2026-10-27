package dev.doughbay.fabric.staffwatch.gui;
import dev.doughbay.fabric.staffwatch.config.Surface;
public final class GuiPalette { public static int abgr(int c){return c&0xFF00FF00|(c&255)<<16|(c>>>16&255);} public static int fill(Surface s){return abgr(0xDC101010);} }
