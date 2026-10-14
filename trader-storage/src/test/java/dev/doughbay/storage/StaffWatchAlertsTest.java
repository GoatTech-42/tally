package dev.doughbay.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class StaffWatchAlertsTest {
    @TempDir Path directory;

    @Test void twoClientsReachSharedEvasionHistoryAndDurableDiscordQueue() throws Exception {
        Path file = directory.resolve("hive.db");
        try (var first = new Database(file)) {
            StaffWatchAlerts.record(first.connection(), 100, "FirstBot", "StaffA", "Staff Watch: disconnected");
        }
        try (var second = new Database(file)) {
            StaffWatchAlerts.record(second.connection(), 200, "SecondBot", "StaffB", "Staff Watch: disconnected");
            var alerts = StaffWatchAlerts.pending(second.connection());
            assertEquals(2, alerts.size());
            assertEquals("FirstBot", alerts.getFirst().client());
            try (var statement = second.connection().createStatement();
                 var rows = statement.executeQuery("SELECT COUNT(*) FROM evasion_events WHERE action LIKE 'Staff Watch:%'")) {
                assertTrue(rows.next());
                assertEquals(2, rows.getInt(1));
            }
            StaffWatchAlerts.delivered(second.connection(), alerts.getFirst().id(), 300);
        }
        // A failed/unsent message survives reopening; the confirmed message is not replayed.
        try (var host = new Database(file)) {
            var remaining = StaffWatchAlerts.pending(host.connection());
            assertEquals(1, remaining.size());
            assertEquals("StaffB", remaining.getFirst().who());
        }
    }

    @Test void historyAndNotificationInsertAreAtomic() throws Exception {
        try (var db = Database.inMemory()) {
            try (var statement = db.connection().createStatement()) {
                statement.execute("DROP TABLE staff_watch_alerts");
            }
            assertThrows(java.sql.SQLException.class, () ->
                    StaffWatchAlerts.record(db.connection(), 100, "Bot", "Staff", "disconnected"));
            try (var statement = db.connection().createStatement();
                 var rows = statement.executeQuery("SELECT COUNT(*) FROM evasion_events")) {
                assertTrue(rows.next());
                assertEquals(0, rows.getInt(1));
            }
            assertTrue(db.connection().getAutoCommit());
        }
    }
}
