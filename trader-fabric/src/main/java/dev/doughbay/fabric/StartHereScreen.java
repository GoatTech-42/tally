package dev.doughbay.fabric;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import java.net.URI;

/** Reopenable beginner entry point. Navigation alone never enables trading. */
final class StartHereScreen extends GoNutsScreen {
    private final DoughBayScreen parent;
    private String message = "";
    StartHereScreen(DoughBayScreen parent) {
        super(Component.literal("GoNuts - Start here")); this.parent = parent;
    }
    private int bodyWidth() { return Math.min(600, width - 24); }
    private int left() { return (width - bodyWidth()) / 2; }
    private int top() { return Math.max(12, (height - 282) / 2); }
    private int row(int index) { return top() + 58 + index * (height < 285 ? 28 : 34); }
    private Button button(String label, int y, Button.OnPress action) {
        return addRenderableWidget(CockpitButton.builder(Component.literal(label), action)
                .bounds(left(), y, bodyWidth(), 25).build());
    }
    @Override protected void init() {
        DoughBayClient.automationSessionController().setPanelOpen(true);
        int y = top();
        var setup = button("1  SET UP TRADING - START HERE", row(0),
                b -> minecraft.setScreen(new QuickStartScreen(parent, this)));
        ((CockpitButton)setup).primary();
        setInitialFocus(setup);
        button("2  Open Trade - status, Start, Stop and recovery", row(1),
                b -> parent.openBeginnerTarget("autopilot", this));
        button("Existing releases (public support has ended)", row(2),
                b -> Util.getPlatform().openUri(URI.create("https://github.com/cody-raves/Gonuts/releases/latest")));
        button("Show menus + hotbar", row(3), b -> {
            try { BeginnerHelp.showGameUi(); message = "Menus and hotbar are visible. Trading settings were kept."; }
            catch (java.io.IOException e) { message = "Could not save display settings. Open Settings and try again."; }
        });
        int half = (bodyWidth() - 6) / 2;
        int footer = Math.min(height - 31, y + 234);
        addRenderableWidget(CockpitButton.builder(Component.literal("More tutorials"),
                b -> minecraft.setScreen(new TutorialScreen(parent, this, false)))
                .bounds(left(), footer, half, 23).build());
        addRenderableWidget(CockpitButton.builder(Component.literal("Back to GoNuts"), b -> onClose())
                .bounds(left() + half + 6, footer, half, 23).build());
    }
    @Override public void extractRenderState(GuiGraphics g, int mx, int my, float dt) {
        g.fill(0, 0, width, height, UiTheme.BACKGROUND);
        int x = left(), y = top(), w = bodyWidth();
        g.drawString(font, "NEW TO GONUTS? START HERE", x, y, UiTheme.GOLD);
        g.drawString(font, UiTheme.fit(font, "Join DonutSMP, choose your limits, then review and Start. No code needed.", w), x, y + 23, UiTheme.TEXT);
        g.drawString(font, UiTheme.fit(font, "Reopen this page: B > Settings > Help & setup.", w), x, y + 42, UiTheme.MUTED);
        int lineY = row(3) + 32;
        for (var line : font.split(Component.literal(message.isBlank()
                ? "Trading is called Trade. If it is paused or blocked, open Trade to see why."
                : message), w)) { g.drawString(font, line, x, lineY, UiTheme.TEXT); lineY += 12; }
        super.extractRenderState(g, mx, my, dt);
    }
    @Override public void onClose() { parent.endTutorialNavigation(); minecraft.setScreen(parent); }
    @Override public void removed() { DoughBayClient.automationSessionController().setPanelOpen(false); super.removed(); }
    @Override public boolean isPauseScreen() { return false; }
}
