package dev.doughbay.fabric;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import java.net.URI;
import java.util.concurrent.CompletableFuture;

/** Review-first reporting. No network request happens before the user opens GitHub. */
public final class BugReportScreen extends Screen {
    private final Screen parent;
    private EditBox happened, expected;
    private String happenedDraft = "", expectedDraft = "", report = "", status = "";
    private boolean collecting;
    private net.minecraft.client.gui.components.Button sendButton;
    public BugReportScreen(Screen parent) { super(Component.literal("Report a GoNuts bug")); this.parent = parent; }
    private int left() { return (width - panelWidth()) / 2; }
    private int panelWidth() { return Math.min(580, width - 24); }
    @Override protected void init() {
        DoughBayClient.automationSessionController().setPanelOpen(true);
        int x = left() + 12, w = panelWidth() - 24;
        happened = new EditBox(font, x, 46, w, 20, Component.literal("What happened?"));
        happened.setMaxLength(1000); happened.setValue(happenedDraft); happened.setResponder(v -> { happenedDraft = v; invalidate(); });
        expected = new EditBox(font, x, 83, w, 20, Component.literal("What should happen?"));
        expected.setMaxLength(1000); expected.setValue(expectedDraft); expected.setResponder(v -> { expectedDraft = v; invalidate(); });
        happened.active = !collecting; expected.active = !collecting;
        addRenderableWidget(happened); addRenderableWidget(expected);
        addRenderableWidget(CockpitButton.builder(Component.literal("Prepare filtered report"), b -> prepare()).bounds(x, 111, 158, 20).build());
        sendButton = CockpitButton.builder(Component.literal("Copy + open GitHub"), b -> {
            if (report.isEmpty() || collecting) return;
            minecraft.keyboardHandler.setClipboard(report);
            Util.getPlatform().openUri(URI.create("https://github.com/cody-raves/Gonuts/issues/new?template=bug-report.yml"));
            status = "Paste the reviewed report into GitHub, then submit there.";
        }).bounds(x + 164, 111, 145, 20).build();
        sendButton.active = !report.isEmpty() && !collecting; addRenderableWidget(sendButton);
        addRenderableWidget(CockpitButton.builder(Component.literal("Back"), b -> onClose()).bounds(x + w - 60, 111, 60, 20).build());
    }
    private void invalidate() { report = ""; if (sendButton != null) sendButton.active = false; }
    private void prepare() {
        if (collecting) return;
        if (happenedDraft.isBlank() || expectedDraft.isBlank()) { status = "Describe the problem and expected behavior first."; return; }
        collecting = true; report = ""; status = "Preparing locally; nothing is uploaded."; rebuildWidgets();
        String description = BugReportPrivacy.clean(happenedDraft), expectation = BugReportPrivacy.clean(expectedDraft);
        CompletableFuture.supplyAsync(() -> {
            try {
                String version = FabricLoader.getInstance().getModContainer("doughbay").orElseThrow().getMetadata().getVersion().getFriendlyString();
                return "GoNuts bug report\nBuild: " + BugReportPrivacy.clean(version) + "\nMinecraft: 26.2\n\nWhat happened:\n" + description
                    + "\n\nExpected:\n" + expectation + "\n\n" + BugReportDiagnostics.read(FabricLoader.getInstance().getGameDir().resolve("logs/latest.log"));
            } catch (Exception e) { return ""; }
        }).thenAccept(result -> minecraft.execute(() -> {
            collecting = false; report = result;
            status = result.isEmpty() ? "Report preparation failed. Sending is blocked; no raw-log fallback." : "Review below. Remove any private details you typed before sharing.";
            rebuildWidgets();
        }));
    }
    @Override public void onClose() { minecraft.gui.setScreen(parent); }
    @Override public void removed() { DoughBayClient.automationSessionController().setPanelOpen(false); }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        g.fill(0, 0, width, height, 0xE0050910);
        int x = left() + 12, w = panelWidth() - 24;
        g.text(font, "REPORT A BUG", x, 12, UiTheme.GOLD);
        g.text(font, "GitHub issues are public. Raw logs are never included.", x, 23, UiTheme.MUTED);
        g.text(font, "What happened? (Do not include names or private chat)", x, 33, UiTheme.TEXT);
        g.text(font, "What did you expect?", x, 70, UiTheme.TEXT);
        g.text(font, UiTheme.fit(font, status, w), x, 139, UiTheme.SECONDARY);
        int y = 157;
        for (var line : font.split(Component.literal(report), w)) {
            if (y > height - 16) { g.text(font, "Full report can be reviewed in GitHub before submission.", x, height - 12, UiTheme.MUTED); break; }
            g.text(font, line, x, y, UiTheme.TEXT); y += 10;
        }
        super.extractRenderState(g, mx, my, delta);
    }
}
