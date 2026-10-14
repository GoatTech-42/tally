package dev.doughbay.fabric;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BugReportLogTest {
    @Test void noPrivateMessageDataSurvivesRecognizedLines() {
        String raw = "[12:34:56] [Render thread/INFO]: DoughBay automation [PAUSED]: Owen123 at 124,45,789 token=secret\n"
            + "[12:34:57] [Render thread/WARN]: Client disconnected with reason: Owen123 192.0.2.1 https://secret.invalid C:\\Users\\Cody\\data\n"
            + "org.sqlite.SQLiteException: [SQLITE_BUSY] user@mail.invalid password=hunter2\n"
            + "\tat knot//dev.doughbay.fabric.BugReportTransport.send(BugReportTransport.java:42)\n"
            + "[12:34:58] [CHAT] DoughBay automation [BUYING]: Private chat\nUnrecognized line with medical/personal information";
        String log = BugReportLog.sanitize(raw);
        assertTrue(log.contains("state=PAUSED")); assertTrue(log.contains("SQLITE_BUSY")); assertTrue(log.contains("BugReportTransport.java:42"));
        for (String value : new String[]{"Owen123", "124,45", "secret", "192.0.2.1", "Cody", "mail.invalid", "hunter2", "BUYING", "medical"}) assertFalse(log.contains(value), value);
    }
    @Test void boundsExcerptAndOmitsForeignFrames() {
        String raw = "";
        for (int i=0;i<500;i++) raw += "\tat dev.doughbay.fabric.BugReportTransport.send(BugReportTransport.java:" + i + ")\n";
        raw += "\tat other.vendor.PersonalName.method(Name.java:123)\n";
        String log = BugReportLog.sanitize(raw);
        assertTrue(log.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 8192);
        assertTrue(log.lines().count() <= 102); assertFalse(log.contains("PersonalName"));
    }
}
