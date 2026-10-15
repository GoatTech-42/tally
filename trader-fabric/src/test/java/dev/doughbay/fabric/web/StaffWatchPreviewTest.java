package dev.doughbay.fabric.web;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class StaffWatchPreviewTest {
    @Test void writesOfflinePanelPreviewWithStaffNavigationAndSafeTextRendering() throws Exception {
        assertTrue(Pages.APP.contains("Staff Watch"));assertTrue(Pages.APP.contains("openStaff"));
        assertTrue(Pages.APP.contains("d.summaries.map"));assertFalse(Pages.APP.contains("Possible following"));
        Path directory=Path.of("build/staff-watch-preview");Files.createDirectories(directory);
        Files.writeString(directory.resolve("index.html"),Pages.APP,StandardCharsets.UTF_8);
        try(var font=StaffWatchPreviewTest.class.getResourceAsStream("/doughbay/web/Monocraft.ttf")) {
            if(font!=null)Files.write(directory.resolve("font.ttf"),font.readAllBytes());
        }
    }
}
