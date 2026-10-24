package dev.doughbay.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseMigrationAtomicityTest {
    @TempDir Path directory;

    @Test void clientsOpeningTheSameLegacyLedgerSerializeTheUpgrade() throws Exception {
        Path file = directory.resolve("shared-upgrade.db");
        legacyCheckpoint(file);
        var start = new CountDownLatch(1);
        try (var clients = Executors.newFixedThreadPool(4)) {
            var results = new ArrayList<Future<Long>>();
            for (int client = 0; client < 4; client++) {
                results.add(clients.submit(() -> {
                    assertTrue(start.await(10, TimeUnit.SECONDS));
                    try (var database = new Database(file); var statement = database.connection().createStatement()) {
                        try (var rows = statement.executeQuery("SELECT MAX(version) FROM schema_version")) {
                            assertTrue(rows.next());
                            assertEquals(19, rows.getInt(1));
                        }
                        try (var rows = statement.executeQuery("SELECT lifetime_active_millis FROM automation_session_state WHERE client='' AND uncertain_active=1")) {
                            assertTrue(rows.next());
                            long retained = rows.getLong(1);
                            assertFalse(rows.next());
                            return retained;
                        }
                    }
                }));
            }
            start.countDown();
            for (var result : results) assertEquals(9876L, result.get(45, TimeUnit.SECONDS));
        }
    }

    @Test void failedVersionWriteDoesNotLeaveTheCheckpointRebuiltWithoutItsVersion() throws Exception {
        Path file = directory.resolve("interrupted-upgrade.db");
        legacyCheckpoint(file);
        try (var connection = raw(file); var statement = connection.createStatement()) {
            statement.execute("CREATE TRIGGER reject_v14 BEFORE INSERT ON schema_version "
                    + "WHEN NEW.version=14 BEGIN SELECT RAISE(ABORT,'synthetic version write failure'); END");
        }

        assertThrows(SQLException.class, () -> new Database(file));

        try (var connection = raw(file); var statement = connection.createStatement()) {
            try (var columns = statement.executeQuery("SELECT name FROM pragma_table_info('automation_session_state') WHERE name='singleton_id'")) {
                assertTrue(columns.next(), "failed migration must leave the original checkpoint schema intact");
            }
            try (var rows = statement.executeQuery("SELECT committed_spend,uncertain_active,lifetime_active_millis FROM automation_session_state WHERE singleton_id=1")) {
                assertTrue(rows.next());
                assertEquals(1234, rows.getLong(1));
                assertEquals(1, rows.getInt(2));
                assertEquals(9876, rows.getLong(3));
            }
            try (var rows = statement.executeQuery("SELECT MAX(version) FROM schema_version")) {
                assertTrue(rows.next());
                assertEquals(13, rows.getInt(1));
            }
            try (var rows = statement.executeQuery("SELECT COUNT(*) FROM sqlite_master WHERE name='automation_session_state_v13'")) {
                assertTrue(rows.next());
                assertEquals(0, rows.getInt(1));
            }
            statement.execute("DROP TRIGGER reject_v14");
        }
        // A normal retry must work without manually repairing a table or advancing a version.
        try (var database = new Database(file); var statement = database.connection().createStatement();
             var rows = statement.executeQuery("SELECT uncertain_active,uncertain_item_count,lifetime_active_millis FROM automation_session_state WHERE client=''")) {
            assertTrue(rows.next());
            assertEquals(1, rows.getInt(1));
            assertEquals(64, rows.getInt(2));
            assertEquals(9876, rows.getLong(3));
        }
    }

    private static void legacyCheckpoint(Path file) throws Exception {
        try (var database = new Database(file); var statement = database.connection().createStatement()) {
            // V13 and V14 have the same column order except for the owner key.
            // Retain later unrelated tables to exercise the real upgrade path twice.
            statement.execute("ALTER TABLE automation_session_state RENAME COLUMN client TO singleton_id");
            statement.execute("INSERT INTO automation_session_state(singleton_id,session_open,run_mode,controller_state,"
                    + "trades_started,committed_spend,session_started_at,updated_at,uncertain_active,uncertain_item_count,lifetime_active_millis) "
                    + "VALUES(1,1,'AUTO','BUYING',2,1234,10,20,1,64,9876)");
            statement.execute("DELETE FROM schema_version WHERE version>13");
        }
    }

    private static Connection raw(Path file) throws SQLException {
        return new org.sqlite.JDBC().connect("jdbc:sqlite:" + file, new java.util.Properties());
    }
}
