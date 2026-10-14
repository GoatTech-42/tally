package dev.doughbay.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class ConnectionAlertsTest {
    @TempDir Path directory;

    @Test void upgradesV17AndKeepsStaffAndDisconnectQueuesSeparateAcrossReopens() throws Exception {
        Path file = directory.resolve("existing.db");
        try (var db = new Database(file); var s = db.connection().createStatement()) {
            StaffWatchAlerts.record(db.connection(), 1, "First", "StaffA", "staff escape");
            s.execute("DROP TABLE connection_alerts");
            s.execute("DELETE FROM schema_version WHERE version > 17");
        }
        try (var db = new Database(file)) {
            assertEquals(1, StaffWatchAlerts.pending(db.connection()).size());
            ConnectionAlerts.record(db.connection(), 2, "Second", "Click-Spam", "Slower pacing applied");
        }
        try (var db = new Database(file); var s = db.connection().createStatement()) {
            var pending = ConnectionAlerts.pending(db.connection());
            assertEquals(1, pending.size());
            assertEquals("Second", pending.getFirst().client());
            assertEquals("Click-Spam", pending.getFirst().who());
            ConnectionAlerts.delivered(db.connection(), pending.getFirst().id(), 3);
            try (var rows = s.executeQuery("SELECT COUNT(*) FROM evasion_events")) {
                assertTrue(rows.next());
                assertEquals(2, rows.getInt(1));
            }
        }
        try (var db = new Database(file)) {
            assertTrue(ConnectionAlerts.pending(db.connection()).isEmpty());
            assertEquals(1, StaffWatchAlerts.pending(db.connection()).size());
        }
    }

    @Test void failedQueueWriteDoesNotLeavePartialHistory() throws Exception {
        try (var db = Database.inMemory(); var s = db.connection().createStatement()) {
            s.execute("DROP TABLE connection_alerts");
            assertThrows(java.sql.SQLException.class, () -> ConnectionAlerts.record(db.connection(), 1, "A", "Timed out", "retry"));
            try (var rows = s.executeQuery("SELECT COUNT(*) FROM evasion_events")) {
                assertTrue(rows.next());
                assertEquals(0, rows.getInt(1));
            }
        }
    }
}
