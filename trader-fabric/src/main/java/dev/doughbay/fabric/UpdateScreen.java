package dev.doughbay.fabric;

import dev.doughbay.fabric.automation.AutomationSessionController;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Util;

import java.net.URI;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * The notice that a newer build is out, as something to answer rather than a
 * line of chat that scrolls away: open the download page, turn on
 * auto-update, or leave it for later. Under it, what the build changes. The
 * same screen shows what an update that has just gone in changed, and the
 * notes of the last few builds on request.
 *
 * <p>It is only ever shown in front of the mod's own screen, when the player
 * has opened that. It never opens by itself: a screen appearing unasked would
 * stop the walk onto the server and take the mouse from someone playing.
 */
public final class UpdateScreen extends GoNutsScreen {
    /** What this is showing. */
    enum Mode {
        /** A newer build is out or downloaded. */
        UPDATE,
        /** The same, with nothing newer out, so it can be looked at. */
        SAMPLE,
        /** A newer build has just gone in: what it changed. */
        CHANGED,
        /** The notes of the last few builds, asked for from the About tab. */
        HISTORY
    }

    private static final long SAMPLE_AGE_MILLIS = 20 * 60_000L;
    private static final int HISTORY_ENTRIES = 6;
    private static final AutomationSessionController SESSION = DoughBayClient.automationSessionController();

    private record Line(FormattedCharSequence text, int color, int indent, int gapAbove) { }

    private final Screen parent;
    private final Mode mode;
    private boolean answered;
    private String shown = "";

    UpdateScreen(Screen parent, Mode mode) {
        super(Component.literal("Tally update"));
        this.parent = parent;
        this.mode = mode;
    }

    private int panelWidth() {
        return Math.min(440, this.width - 24);
    }

    private static boolean autoOn() {
        return Tuning.get("update.auto") >= 0.5;
    }

    private boolean aboutAnUpdate() {
        return mode == Mode.UPDATE || mode == Mode.SAMPLE;
    }

    /** What the notice is about. A sample shows what this player would be shown for a real one. */
    private UpdateCheck.State state() {
        if (mode == Mode.SAMPLE) return autoOn() ? UpdateCheck.State.READY : UpdateCheck.State.NEWER;
        return UpdateCheck.status().state();
    }

    private String title() {
        if (!aboutAnUpdate()) return "What's new in Tally";
        return alert().title() + (mode == Mode.SAMPLE ? "  (preview)" : "");
    }

    private UpdateCheck.Alert alert() {
        return UpdateCheck.explain(state(), autoOn(), mode == Mode.SAMPLE ? SAMPLE_AGE_MILLIS
                : System.currentTimeMillis() - UpdateCheck.status().uploadedAt());
    }

    /** The builds whose notes belong here, the newest first. */
    private List<Changelog.Entry> entries() {
        List<Changelog.Entry> history = UpdateCheck.history();
        return switch (mode) {
            case UPDATE -> UpdateCheck.news();
            case SAMPLE -> history.subList(0, Math.min(1, history.size()));
            case CHANGED -> UpdateCheck.changed();
            case HISTORY -> history.subList(0, Math.min(HISTORY_ENTRIES, history.size()));
        };
    }

    /** Everything written on the panel under its title, wrapped to its width. */
    private List<Line> lines() {
        int width = panelWidth() - 24;
        List<Line> out = new ArrayList<>();
        if (aboutAnUpdate()) {
            List<String> paragraphs = alert().paragraphs();
            for (int i = 0; i < paragraphs.size(); i++) {
                boolean first = true;
                for (FormattedCharSequence line : this.font.split(Component.literal(paragraphs.get(i)), width)) {
                    out.add(new Line(line, i == 0 ? UiTheme.TEXT : UiTheme.SECONDARY, 0, first && i > 0 ? 5 : 0));
                    first = false;
                }
            }
        }
        List<Changelog.Entry> entries = entries();
        if (entries.isEmpty()) {
            if (!aboutAnUpdate()) {
                out.add(new Line(Component.literal("No notes came with this build.").getVisualOrderText(),
                        UiTheme.MUTED, 0, 0));
            }
            return out;
        }
        if (aboutAnUpdate()) {
            out.add(new Line(Component.literal("WHAT'S NEW").getVisualOrderText(), UiTheme.GOLD, 0, 9));
        }
        ZoneId zone = ZoneId.systemDefault();
        boolean firstEntry = true;
        for (Changelog.Entry entry : entries) {
            out.add(new Line(Component.literal(entry.when(zone)).getVisualOrderText(), UiTheme.MUTED, 0,
                    firstEntry && !aboutAnUpdate() ? 0 : 6));
            firstEntry = false;
            for (String bullet : entry.lines()) {
                boolean first = true;
                for (FormattedCharSequence line : this.font.split(Component.literal(bullet), width - 10)) {
                    if (first) {
                        out.add(new Line(Component.literal("•").getVisualOrderText(), UiTheme.GOLD, 0, 2));
                        out.add(new Line(line, UiTheme.TEXT, 10, -11));
                    } else {
                        out.add(new Line(line, UiTheme.TEXT, 10, 0));
                    }
                    first = false;
                }
            }
        }
        return out;
    }

    private static int height(List<Line> lines) {
        int total = 0;
        for (Line line : lines) total += 11 + line.gapAbove();
        return total;
    }

    /** As tall as its words and its buttons need, and no taller than the window. */
    private int panelHeight() {
        return Math.min(33 + height(lines()) + 8 + 28, this.height - 24);
    }

    @Override
    protected void init() {
        SESSION.setPanelOpen(true);
        UpdateCheck.State state = state();
        shown = state + ":" + autoOn();
        int width = panelWidth();
        int left = (this.width - width) / 2;
        int y = (this.height - panelHeight()) / 2 + panelHeight() - 28;
        int gap = 6;
        if (!aboutAnUpdate() || state != UpdateCheck.State.NEWER) {
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
                SESSION.notePanelActivity();
                Tuning.set("update.auto", 1);
                rebuildWidgets();
            }).bounds(x, y, each, 20).build());
            x += each + gap;
        }
        addRenderableWidget(CockpitButton.builder(Component.literal(offerAuto ? "Later" : "OK"), b -> leave())
                .bounds(x, y, each, 20).build());
    }

    private void openPage() {
        SESSION.notePanelActivity();
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
        if (mode == Mode.UPDATE) UpdateCheck.acknowledge();
        this.minecraft.setScreen(parent);
    }

    @Override
    public void onClose() {
        if (!answered) leave();
    }

    @Override
    public void removed() {
        // The session holds for this as it does for the screen behind it;
        // that screen takes the hold back as it opens.
        SESSION.setPanelOpen(false);
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        // The download can finish, or auto-update be switched on, while this is open.
        String shape = state() + ":" + autoOn();
        if (!shape.equals(shown)) rebuildWidgets();
        graphics.fill(0, 0, this.width, this.height, 0xB0050505);
        int width = panelWidth();
        int height = panelHeight();
        int left = (this.width - width) / 2;
        int top = (this.height - height) / 2;
        UiTheme.panel(graphics, left, top, width, height, UiTheme.SURFACE);
        graphics.drawString(this.font, UiTheme.fit(this.font, title(), width - 24), left + 12, top + 11, UiTheme.GOLD);
        graphics.fill(left + 12, top + 24, left + width - 12, top + 25, UiTheme.EDGE);
        int y = top + 33;
        int limit = top + height - 36;
        List<Line> lines = lines();
        for (int i = 0; i < lines.size(); i++) {
            Line line = lines.get(i);
            y += line.gapAbove();
            // Room for this line and, unless it is the last, for the note that there is more.
            if (y + 11 > limit - (i < lines.size() - 1 ? 11 : 0) && line.gapAbove() >= 0) {
                graphics.drawString(this.font, "More on the download page and the About tab.", left + 12, y, UiTheme.MUTED);
                break;
            }
            graphics.drawString(this.font, line.text(), left + 12 + line.indent(), y, line.color());
            y += 11;
        }
        super.render(graphics, mouseX, mouseY, delta);
    }
}
