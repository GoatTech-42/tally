package dev.doughbay.fabric.web;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Writes the web panel's generated HTML to disk so the real page can be
 * rendered and reviewed outside the game. Off in normal builds:
 * <pre>GONUTS_RENDER_PANEL=build/panel ./gradlew :trader-fabric:test --tests "*PanelHtmlExport*" --rerun</pre>
 * The export is the static page: fonts, logo and live /api data only exist
 * when the panel is served from a running client.
 */
@EnabledIfEnvironmentVariable(named = "GONUTS_RENDER_PANEL", matches = ".+")
class PanelHtmlExport {
    @Test
    void writePanelPages() throws Exception {
        Path out = Path.of(System.getenv("GONUTS_RENDER_PANEL"));
        Files.createDirectories(out);
        Files.writeString(out.resolve("app.html"), Pages.APP);
        Files.writeString(out.resolve("login.html"), Pages.login(""));
        assertTrue(Files.size(out.resolve("app.html")) > 1000, "app page should be substantial");
    }
}
