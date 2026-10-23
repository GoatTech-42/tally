package dev.doughbay.fabric;

import java.util.function.BooleanSupplier;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Readable recovery instructions; all actual actions still use the controller's gates. */
final class RecoveryHelpScreen extends GoNutsScreen {
    private final Screen parent;
    private final String instructions, confirmation;
    private final Runnable confirm;
    private final BooleanSupplier stillAllowed;
    private net.minecraft.client.gui.components.Button primary;
    private int scroll, maxScroll;

    RecoveryHelpScreen(Screen parent, String instructions, String confirmation,
                       Runnable confirm, BooleanSupplier stillAllowed) {
        super(Component.literal("Check interrupted trade"));
        this.parent = parent;
        this.instructions = instructions;
        this.confirmation = confirmation;
        this.confirm = confirm;
        this.stillAllowed = stillAllowed;
    }

    private int bodyWidth() { return Math.min(560, width - 24); }
    @Override protected void init() {
        DoughBayClient.automationSessionController().setPanelOpen(true);
        int w = bodyWidth(), x = (width - w) / 2, half = (w - 8) / 2;
        addRenderableWidget(CockpitButton.builder(Component.literal("Back to Trade"), b -> onClose())
                .bounds(x, height - 32, half, 22).build());
        primary = CockpitButton.builder(Component.literal(confirmation), b -> {
            if (scroll >= maxScroll && stillAllowed.getAsBoolean()) confirm.run();
        }).bounds(x + half + 8, height - 32, w - half - 8, 22).build();
        primary.active = false;
        addRenderableWidget(primary);
    }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float dt) {
        g.fill(0, 0, width, height, UiTheme.BACKGROUND);
        int w = bodyWidth(), x = (width - w) / 2;
        g.text(font, "GoNuts - Check interrupted trade", x, 14, UiTheme.TEXT);
        int bottom = height - 60, y = 40 - scroll;
        g.enableScissor(x, 36, x + w, bottom);
        for (String paragraph : instructions.split("\n")) {
            for (var line : font.split(Component.literal(paragraph), w)) {
                g.text(font, line, x, y, UiTheme.TEXT);
                y += 13;
            }
            y += 10;
        }
        maxScroll = Math.max(0, y + scroll - bottom);
        g.disableScissor();
        boolean allowed = stillAllowed.getAsBoolean();
        primary.active = scroll >= maxScroll && allowed;
        g.text(font, scroll < maxScroll ? "Scroll down to read all steps before confirming."
                : allowed ? "Confirm only what you checked. Your limits stay in place."
                : "No confirmation available. Follow the steps above, then return to Trade.",
                x, height - 51, UiTheme.MUTED);
        super.extractRenderState(g, mx, my, dt);
    }

    @Override public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        scroll = Math.max(0, Math.min(maxScroll, scroll - (int) (dy * 28)));
        return true;
    }
    @Override public void onClose() { minecraft.gui.setScreen(parent); }
    @Override public void removed() {
        DoughBayClient.automationSessionController().setPanelOpen(false);
        super.removed();
    }
}
