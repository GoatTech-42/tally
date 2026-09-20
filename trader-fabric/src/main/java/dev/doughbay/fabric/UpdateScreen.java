package dev.doughbay.fabric;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Util;

import java.net.URI;

/**
 * The notice that a newer build is out, as something to answer rather than a
 * line of chat that scrolls away: open the download page, turn on
 * auto-update, or leave it for later.
 *
 * <p>It is only ever shown in front of the mod's own screen, when the player
 * has opened that. It never opens by itself: a screen appearing unasked would
 * stop the walk onto the server and take the mouse from someone playing.
 */
final class UpdateScreen extends Screen {
    private static final long SAMPLE_AGE_MILLIS = 20 * 60_000L;

    private final Screen parent;
    /** A look at the notice with nothing newer out: it says so, and answers nothing. */
    private final boolean sample;
    private boolean answered;
    private String shown = "";

    UpdateScreen(Screen parent, boolean sample) {
        super(Component.literal("GoNuts update"));
        this.parent = parent;
        this.sample = sample;
    }

    private int panelWidth() {
        return Math.min(400, this.width - 24);
    }

    /** As tall as its words and its buttons need, and no taller. */
    private int panelHeight() {
        int text = 0;
        for (String paragraph : alert().paragraphs()) {
            text += this.font.split(Component.literal(paragraph), panelWidth() - 24).size() * 11 + 5;
        }
        return Math.min(33 + text + 6 + 28, this.height - 24);
    }

    private UpdateCheck.Alert alert() {
        return UpdateCheck.explain(state(), autoOn(), sample ? SAMPLE_AGE_MILLIS
                : System.currentTimeMillis() - UpdateCheck.status().uploadedAt());
    }

    private static boolean autoOn() {
        return Tuning.get("update.auto") >= 0.5;
    }

    /** What the notice is about. A sample shows what this player would be shown for a real one. */
    private UpdateCheck.State state() {
        if (sample) return autoOn() ? UpdateCheck.State.READY : UpdateCheck.State.NEWER;
        return UpdateCheck.status().state();
    }

    @Override
    protected void init() {
        UpdateCheck.State state = state();
        shown = state + ":" + autoOn();
        int width = panelWidth();
        int left = (this.width - width) / 2;
        int y = (this.height - panelHeight()) / 2 + panelHeight() - 28;
        int gap = 6;
        if (state != UpdateCheck.State.NEWER) {
            addRenderableWidget(CockpitButton.builder(Component.literal("OK"), b -> leave())
                    .bounds(left + (width - 90) / 2, y, 90, 20).build());
            return;
        }
        boolean offerAuto = !autoOn();
        int count = offerAuto ? 3 : 2;
        int each = (width - 20 - gap * (count - 1)) / count;
        int x = left + 10;
        addRenderableWidget(CockpitButton.builder(Component.literal("Open download page"), b -> openPage())
                .bounds(x, y, each, 20).build());
        x += each + gap;
        if (offerAuto) {
            addRenderableWidget(CockpitButton.builder(Component.literal("Turn on auto-update"), b -> {
                Tuning.set("update.auto", 1);
                UpdateCheck.checkNow();
                rebuildWidgets();
            }).bounds(x, y, each, 20).build());
            x += each + gap;
        }
        addRenderableWidget(CockpitButton.builder(Component.literal(offerAuto ? "Later" : "OK"), b -> leave())
                .bounds(x, y, each, 20).build());
    }

    private void openPage() {
        String page = UpdateCheck.status().page();
        // Only ever the project's own pages, whatever the answer from GitHub said.
        if (page == null || !page.startsWith("https://github.com/")) page = UpdateCheck.PAGE;
        try {
            Util.getPlatform().openUri(URI.create(page));
        } catch (RuntimeException e) {
            DoughBayClient.LOGGER.warn("DoughBay update: the download page could not be opened ({})", e.toString());
        }
    }

    private void leave() {
        answered = true;
        if (!sample) UpdateCheck.acknowledge();
        this.minecraft.gui.setScreen(parent);
    }

    @Override
    public void onClose() {
        if (!answered) leave();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        // The download can finish, or auto-update be switched on, while this is open.
        UpdateCheck.State state = state();
        String shape = state + ":" + autoOn();
        if (!shape.equals(shown)) rebuildWidgets();
        graphics.fill(0, 0, this.width, this.height, 0xB0050910);
        int width = panelWidth();
        int height = panelHeight();
        int left = (this.width - width) / 2;
        int top = (this.height - height) / 2;
        UiTheme.panel(graphics, left, top, width, height, UiTheme.SURFACE);
        UpdateCheck.Alert alert = alert();
        graphics.text(this.font, UiTheme.fit(this.font, alert.title() + (sample ? "  (preview)" : ""), width - 24),
                left + 12, top + 11, UiTheme.GOLD);
        graphics.fill(left + 12, top + 24, left + width - 12, top + 25, UiTheme.EDGE);
        int y = top + 33;
        int limit = top + height - 34;
        for (int i = 0; i < alert.paragraphs().size(); i++) {
            for (FormattedCharSequence line : this.font.split(Component.literal(alert.paragraphs().get(i)), width - 24)) {
                if (y > limit - this.font.lineHeight) break;
                graphics.text(this.font, line, left + 12, y, i == 0 ? UiTheme.TEXT : UiTheme.SECONDARY);
                y += 11;
            }
            y += 5;
        }
        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }
}
