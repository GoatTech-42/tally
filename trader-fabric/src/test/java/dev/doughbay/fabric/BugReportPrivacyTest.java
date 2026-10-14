package dev.doughbay.fabric;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BugReportPrivacyTest {
    @Test void removesSecretsAddressesAndPaths() {
        String raw = "token=super-secret\nhttps://discord.com/api/webhooks/private\n192.168.0.8:25565\n2001:db8::1234\nuser@example.com\nC:\\Users\\Alice\\private.txt\n/home/alice/private\nwest.donutsmp.net\n12345678-1234-1234-1234-123456789abc";
        String clean = BugReportPrivacy.clean(raw);
        for (String privateValue : new String[]{"super-secret", "discord.com", "192.168", "2001:db8", "user@example", "Alice", "alice", "donutsmp", "123456789abc"})
            assertFalse(clean.contains(privateValue), privateValue);
    }
    @Test void rejectsRawChatAndLongOpaqueIdentifiers() {
        String clean = BugReportPrivacy.clean("[CHAT] Alice whispered a secret\nAbcdefghijklmnopqrstuvwxyz12345678901234567890");
        assertFalse(clean.contains("whispered")); assertFalse(clean.contains("Abcdef"));
    }
    @Test void diagnosticsNeverExportArbitraryLogContent() {
        String summary = BugReportDiagnostics.summarize("DoughBay automation [PAUSED]: token=hunter2 Alice 1.2.3.4\n[CHAT] private\nSQLiteException: /home/private\nDoughBay bid desk: COLLECT did not complete: ABORTED: private");
        assertTrue(summary.contains("Last recorded state: PAUSED"));
        assertTrue(summary.contains("SQLite errors: 1"));
        assertTrue(summary.contains("Uncompleted desk actions: 1"));
        for (String secret : new String[]{"hunter2", "Alice", "1.2.3.4", "private", "[CHAT]"}) assertFalse(summary.contains(secret));
    }
    @Test void boundedReadAndEmptyInputs() throws Exception {
        var path = java.nio.file.Files.createTempFile("bug-report", ".log");
        try {
            java.nio.file.Files.writeString(path, "SQLiteException\n" + "x".repeat(300000) + "\nDoughBay automation [SCANNING]: SECRET");
            String summary = BugReportDiagnostics.read(path);
            assertTrue(summary.contains("SQLite errors: 0"));
            assertTrue(summary.contains("SCANNING")); assertFalse(summary.contains("SECRET"));
        } finally { java.nio.file.Files.delete(path); }
        assertEquals("", BugReportPrivacy.clean(null));
        assertTrue(BugReportPrivacy.clean("a ".repeat(2000)).length() <= 1500);
    }
}
