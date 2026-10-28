package dev.doughbay.fabric;

import dev.doughbay.fabric.staffwatch.WatchConfig;
import dev.doughbay.fabric.staffwatch.util.NameFilter;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;

/** A dedicated warning lane, also drawn over menus after the connection closes. */
public final class StaffWatchAlert {
    private static final long DISPLAY_NANOS = 10_000_000_000L;
    private static String watcher = "";
    private static long shownAt;

    private StaffWatchAlert() { }

    public static void register() {
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("doughbay", "staff_warning"),
                (g, delta) -> {
                    if (Minecraft.getInstance().screen == null) render(g);
                });
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) ->
                ScreenEvents.afterExtract(screen).register((s, g, x, y, delta) -> render(g)));
    }

    public static void show(String name, int others) {
        String shown = NameFilter.apply(name == null ? "Staff" : name);
        String next = shown + (others > 0 ? " +" + others : "");
        // Two detectors can report the same event; keep a single banner and lifetime.
        if (active() && watcher.equals(next)) return;
        watcher = next;
        shownAt = System.nanoTime();
    }

    public static boolean active() {
        return WatchConfig.get().enabled && shownAt != 0
                && System.nanoTime() - shownAt < DISPLAY_NANOS;
    }

    private static void render(GuiGraphics g) {
        if (!active()) return;
        var font = Minecraft.getInstance().font;
        int width = Math.min(310, g.guiWidth() - 24);
        int x = (g.guiWidth() - width) / 2;
        int y = dev.doughbay.fabric.staffwatch.gui.HudRenderer.getInstance().alertTop(g.guiHeight());
        UiTheme.panel(g, x, y, width, 65, UiTheme.BACKGROUND);
        g.fill(x + 9, y + 1, x + width - 9, y + 3, UiTheme.BAD);
        g.drawCenteredString(font, "STAFF WATCH ALERT", g.guiWidth() / 2, y + 10, UiTheme.BAD);
        g.drawCenteredString(font, UiTheme.fit(font, watcher, width - 24), g.guiWidth() / 2, y + 25, UiTheme.TEXT);
        g.drawCenteredString(font, "Possible watcher detected", g.guiWidth() / 2, y + 39, UiTheme.SECONDARY);
        double remaining = 1.0 - (System.nanoTime() - shownAt) / (double) DISPLAY_NANOS;
        g.fill(x + 12, y + 57, x + width - 12, y + 59, UiTheme.EDGE);
        g.fill(x + 12, y + 57, x + 12 + (int) ((width - 24) * remaining), y + 59, UiTheme.BAD);
    }
}
