package dev.doughbay.fabric;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WatchGhostPageRetryTest {
    @Test void reopeningAfterSoldRowStartsAtPageOneWithoutInheritingOldNavigation() throws Exception {
        for (int oldPage : new int[]{2, 4, 1}) {
            var driver = selectedRow(oldPage);
            var commands = new AtomicInteger();
            driver.retryAfterGhost(null, commands::incrementAndGet);
            assertEquals(1, commands.get());
            // tickWatchPage treats page <= lastPage as a response that has not arrived.
            // The new /ah page is page 1, not an awaited next page from the old search.
            assertEquals(0, field("watchLastPage").getInt(driver), "new page 1 must be readable immediately");
            assertEquals(0, field("watchPagesRead").getInt(driver), "the reopened look keeps its full page budget");
            assertEquals(0L, field("watchOpenedAtMillis").getLong(driver));
            assertEquals(100_000L, field("watchStartedAtMillis").getLong(driver), "retry cannot extend session deadline");
            assertEquals(2, field("huntLooksRemaining").getInt(driver));
            assertEquals(0, driver.lastTerminalEvent().sequence(), "a retry is not a purchase or terminal result");
            assertEquals("", field("targetItemId").get(driver));
            var ghosts = (Map<?, ?>) field("watchGhosts").get(driver);
            assertTrue(ghosts.containsKey("minecraft:diamond|64|1000"), "sold row remains excluded");
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static AutomatedExecutionDriver selectedRow(int page) throws Exception {
        var d = new AutomatedExecutionDriver();
        Field op = field("operation");
        op.set(d, Enum.valueOf((Class)op.getType(), "BUY"));
        field("watching").setBoolean(d, true);
        field("watchPagesRead").setInt(d, page - 1);
        field("watchLastPage").setInt(d, Math.max(0, page - 1));
        field("watchOpenedAtMillis").setLong(d, 120_000);
        field("watchStartedAtMillis").setLong(d, 100_000);
        field("huntLooksRemaining").setInt(d, 2);
        field("targetItemId").set(d, "minecraft:diamond");
        field("targetCount").setInt(d, 64);
        field("targetPrice").setLong(d, 1000);
        return d;
    }

    private static Field field(String name) throws Exception {
        var f = AutomatedExecutionDriver.class.getDeclaredField(name);
        f.setAccessible(true);
        return f;
    }
}
