package dev.doughbay.fabric;

import dev.doughbay.storage.Database;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class KeylessFeedConcurrencyTest {
    @TempDir Path directory;

    private void seed(Connection c) throws SQLException {
        try (var st = c.createStatement()) {
            st.execute("CREATE TABLE write_probe(value INTEGER)");
            st.execute("INSERT INTO orders(observed_at,item_id,item_key,descriptor_json,parts,unit_price,delivered,total,page) "
                    + "VALUES(1000,'minecraft:diamond','minecraft:diamond','{}','',80,0,128,1),"
                    + "(2000,'minecraft:diamond','minecraft:diamond','{}','',80,64,128,1)");
        }
    }

    private void write(Connection c, int value) throws SQLException {
        try (var st = c.createStatement()) { st.execute("INSERT INTO write_probe VALUES(" + value + ")"); }
    }

    @Test void theOldOpenReaderCannotBePromotedAfterASiblingCommits() throws Exception {
        Path file = directory.resolve("old-pattern.db");
        try (Database reader = new Database(file); Database sibling = new Database(file)) {
            seed(reader.connection());
            try (var st = reader.connection().createStatement();
                 var rs = st.executeQuery("SELECT observed_at FROM orders ORDER BY observed_at")) {
                assertTrue(rs.next());
                write(sibling.connection(), 1);
                SQLException error = assertThrows(SQLException.class, () -> write(reader.connection(), 2));
                assertTrue(error.getMessage().contains("SQLITE_BUSY_SNAPSHOT"), error.toString());
            }
        }
    }

    @Test void theFeedClosesItsSnapshotBeforeWritingWithoutLosingFillDeltas() throws Exception {
        Path file = directory.resolve("new-pattern.db");
        try (Database reader = new Database(file); Database sibling = new Database(file)) {
            seed(reader.connection());
            AtomicBoolean committed = new AtomicBoolean();
            Connection intercepted = (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                    new Class<?>[]{Connection.class}, (proxy, method, args) -> {
                        Object result = call(method, reader.connection(), args);
                        if (!method.getName().equals("prepareStatement")) return result;
                        PreparedStatement statement = (PreparedStatement) result;
                        return Proxy.newProxyInstance(PreparedStatement.class.getClassLoader(),
                                new Class<?>[]{PreparedStatement.class}, (p, m, a) -> {
                                    Object value = call(m, statement, a);
                                    if (!m.getName().equals("executeQuery")) return value;
                                    ResultSet rows = (ResultSet) value;
                                    return Proxy.newProxyInstance(ResultSet.class.getClassLoader(),
                                            new Class<?>[]{ResultSet.class}, (r, rm, ra) -> {
                                                Object row = call(rm, rows, ra);
                                                if (rm.getName().equals("next") && Boolean.TRUE.equals(row)
                                                        && committed.compareAndSet(false, true)) {
                                                    write(sibling.connection(), 1);
                                                }
                                                return row;
                                            });
                                });
                    });
            KeylessFeed.OrderReadBatch batch = KeylessFeed.readOrderFills(intercepted, 0, 3000, true,
                    new OrderFillTracker());
            assertTrue(committed.get());
            assertEquals(2000, batch.newest());
            assertEquals(java.util.List.of(new OrderFillTracker.Fill("minecraft:diamond", 80, 64, 2000)), batch.fills());
            assertDoesNotThrow(() -> write(reader.connection(), 2));
            try (var st = sibling.connection().createStatement(); var rs = st.executeQuery("SELECT COUNT(*) FROM write_probe")) {
                assertTrue(rs.next());
                assertEquals(2, rs.getInt(1));
            }
        }
    }

    @Test void historicalWarmupEmitsNoSalesAndKeepsTheNextLiveDelta() throws Exception {
        try (Database db = new Database(directory.resolve("warmup.db"))) {
            seed(db.connection());
            OrderFillTracker tracker = new OrderFillTracker();
            var warm = KeylessFeed.readOrderFills(db.connection(), 0, 2000, false, tracker);
            assertEquals(2000, warm.newest());
            assertTrue(warm.fills().isEmpty());
            try (var st = db.connection().createStatement()) {
                st.execute("INSERT INTO orders(observed_at,item_id,item_key,descriptor_json,parts,unit_price,delivered,total,page) "
                        + "VALUES(3000,'minecraft:diamond','minecraft:diamond','{}','',80,96,128,1)");
            }
            var live = KeylessFeed.readOrderFills(db.connection(), warm.newest(), 3000, true, tracker);
            assertEquals(32, live.fills().getFirst().units());
        }
    }

    private static Object call(Method method, Object target, Object[] args) throws Throwable {
        try { return method.invoke(target, args); }
        catch (InvocationTargetException e) { throw e.getCause(); }
    }
}
