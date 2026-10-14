package dev.doughbay.fabric;

import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PacedPageTurnTest {
    @Test void deferredFiltersDoNotExhaustAllowedAttempts() throws Exception {
        var driver = new AutomatedExecutionDriver();
        for (boolean orders : new boolean[]{false, true}) {
            String counter = orders ? "ordersFilterClicks" : "filterClicks";
            for (int tick = 0; tick < 30; tick++) assertFalse(driver.tryFilterClick(orders, () -> false));
            assertEquals(0, field(counter).getInt(driver));
            assertTrue(driver.tryFilterClick(orders, () -> true));
            assertEquals(1, field(counter).getInt(driver));
            assertFalse(driver.tryFilterClick(orders, () -> false));
            assertEquals(1, field(counter).getInt(driver));
        }
    }
    @Test void deferredOrderTurnRemainsPendingWithoutStartingResponseTimeout() throws Exception {
        var driver = new AutomatedExecutionDriver();
        field("ordersTurnPending").set(driver, true);
        field("ordersPagesRead").set(driver, 1);
        field("ticksInPhase").set(driver, 15);
        for (int tick = 0; tick < 20; tick++) {
            assertFalse(driver.tryOrdersPageTurn(() -> false));
            assertTrue(field("ordersTurnPending").getBoolean(driver));
            assertEquals(1, driver.ordersPagesRead());
            assertEquals(15, field("ticksInPhase").getInt(driver));
        }
        assertTrue(driver.tryOrdersPageTurn(() -> true));
        assertFalse(field("ordersTurnPending").getBoolean(driver));
        assertEquals(0, field("ticksInPhase").getInt(driver));
        assertEquals(12, field("ordersSettleUntilTick").getInt(driver));
    }

    @Test void deferredAuctionTurnDoesNotConsumePagesOrPretendResponseIsPending() throws Exception {
        var driver = new AutomatedExecutionDriver();
        for (int tick = 0; tick < 20; tick++) {
            assertFalse(driver.tryWatchPageTurn(1, () -> false));
            assertEquals(0, field("watchPagesRead").getInt(driver));
            assertEquals(0, field("watchLastPage").getInt(driver));
        }
        assertTrue(driver.tryWatchPageTurn(1, () -> true));
        assertEquals(1, field("watchPagesRead").getInt(driver));
        assertEquals(1, field("watchLastPage").getInt(driver));
        assertFalse(driver.tryWatchPageTurn(2, () -> false));
        assertEquals(1, field("watchPagesRead").getInt(driver));
        assertTrue(driver.tryWatchPageTurn(2, () -> true));
        assertEquals(2, field("watchPagesRead").getInt(driver));
    }

    @Test void orderControlsStayStableWhileRowCountersChangeButMovedControlsMustResettle() {
        var driver = new AutomatedExecutionDriver();
        String before = AutomatedExecutionDriver.orderNavigationSignature(1, "Orders (Page 1)", "53|Next Page", "49|Recently Listed");
        assertFalse(driver.observeStableSignature(before));
        assertTrue(driver.observeStableSignature(before));
        assertFalse(driver.observeStableSignature(AutomatedExecutionDriver.orderNavigationSignature(1, "Orders (Page 2)", "53|Next Page", "49|Recently Listed")));
        assertFalse(driver.observeStableSignature(AutomatedExecutionDriver.orderNavigationSignature(1, "Orders (Page 2)", "52|Next Page", "49|Recently Listed")));
    }

    private static Field field(String name) throws Exception {
        Field field = AutomatedExecutionDriver.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
}
