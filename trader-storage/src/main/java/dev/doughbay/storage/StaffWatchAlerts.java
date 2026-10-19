package dev.doughbay.storage;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** Staff escape history and a durable notification queue shared by all clients. */
public final class StaffWatchAlerts {
    public record Alert(long id, long at, String client, String who, String action) { }

    private StaffWatchAlerts() { }

    public static final String REGION_ESCAPE_ACTION = "Staff Watch: watcher detected; preparing a safe region change";
    public static String title(String action) {
        return REGION_ESCAPE_ACTION.equals(action) ? "STAFF WATCH — REGION ESCAPE" : "STAFF WATCH — DISCONNECTED";
    }
    public static String historyLabel(String action) {
        return REGION_ESCAPE_ACTION.equals(action) ? "STAFF — REGION ESCAPE REQUESTED" : "STAFF — DISCONNECTED";
    }

    public static void record(Connection connection, long at, String client, String who, String action) throws SQLException {
        connection.setAutoCommit(false);
        try {
            for (String table : List.of("evasion_events", "staff_watch_alerts")) {
                try (var statement = connection.prepareStatement(
                        "INSERT INTO " + table + " (at, client, who, action) VALUES (?, ?, ?, ?)")) {
                    statement.setLong(1, at);
                    statement.setString(2, client);
                    statement.setString(3, who);
                    statement.setString(4, action);
                    statement.executeUpdate();
                }
            }
            connection.commit();
        } catch (SQLException failure) {
            connection.rollback();
            throw failure;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    public static List<Alert> pending(Connection connection) throws SQLException {
        List<Alert> alerts = new ArrayList<>();
        try (var statement = connection.prepareStatement(
                "SELECT id, at, client, who, action FROM staff_watch_alerts WHERE delivered_at = 0 ORDER BY id LIMIT 10");
             var result = statement.executeQuery()) {
            while (result.next()) alerts.add(new Alert(result.getLong(1), result.getLong(2),
                    result.getString(3), result.getString(4), result.getString(5)));
        }
        return alerts;
    }

    public static void delivered(Connection connection, long id, long now) throws SQLException {
        try (var statement = connection.prepareStatement("UPDATE staff_watch_alerts SET delivered_at = ? WHERE id = ?")) {
            statement.setLong(1, now);
            statement.setLong(2, id);
            statement.executeUpdate();
        }
    }
}
