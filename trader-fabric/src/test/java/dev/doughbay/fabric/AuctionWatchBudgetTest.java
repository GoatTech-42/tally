package dev.doughbay.fabric;

import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AuctionWatchBudgetTest {
    @Test void driverChecksTheModeBeforeReadingOrClickingTheNextAuctionPage() throws Exception {
        double previous = Tuning.get("prices.only");
        try {
            Tuning.set("prices.only", 1);
            var driver = watching(100_000);
            var tick = AutomatedExecutionDriver.class.getDeclaredMethod("tickWaitingForListing", net.minecraft.client.Minecraft.class);
            tick.setAccessible(true);
            tick.invoke(driver, new Object[]{null});
            assertEquals(AutomatedExecutionDriver.TerminalOutcome.ABORTED, driver.lastTerminalEvent().outcome());
            assertFalse(driver.lastTerminalEvent().confirmationSubmitted());
            assertFalse(driver.watchHasNotClicked());
        } finally { Tuning.set("prices.only", previous); }
    }

    @Test void modeChangeEndsOnlyAReadingWatchWithoutServerFailureOrCharge() throws Exception {
        ServerStrain.reset();
        try {
            var reading = watching(100_000);
            assertTrue(reading.cancelUnclickedWatch(null, "Flips only enabled"));
            assertFalse(reading.lastTerminalEvent().confirmationSubmitted());
            assertEquals(AutomatedExecutionDriver.TerminalOutcome.ABORTED, reading.lastTerminalEvent().outcome());
            assertFalse(reading.watchHasNotClicked());
            assertFalse(ServerStrain.holding());
            var clicked = watching(100_000);
            field("targetItemId").set(clicked, "minecraft:emerald_block");
            assertFalse(clicked.cancelUnclickedWatch(null, "Flips only enabled"));
            assertEquals(0, clicked.lastTerminalEvent().sequence());
            assertEquals("minecraft:emerald_block", clicked.watchClickedItemId());
        } finally { ServerStrain.reset(); }
    }

    @Test void entireWatchYieldsAtDeadlineAcrossRepeatedLooks() throws Exception {
        double prior = Tuning.get("watch.session_max_sec");
        try {
            Tuning.set("watch.session_max_sec", 45);
            var driver = watching(100_000);
            field("watchOpenedAtMillis").setLong(driver, 140_000); // latest look, not session start
            assertFalse(driver.yieldUnclickedWatchIfDue(null, 144_999));
            assertTrue(driver.yieldUnclickedWatchIfDue(null, 145_000));
            var event = driver.lastTerminalEvent();
            assertEquals(AutomatedExecutionDriver.OperationIntent.BUY, event.intent());
            assertEquals(AutomatedExecutionDriver.TerminalOutcome.ABORTED, event.outcome());
            assertFalse(event.confirmationSubmitted());
            assertTrue(event.detail().contains("nothing was charged"));
            assertEquals(0, field("watchStartedAtMillis").getLong(driver));
            assertFalse(driver.yieldUnclickedWatchIfDue(null, 200_000));
        } finally { Tuning.set("watch.session_max_sec", prior); }
    }

    @Test void selectedPurchaseIsNeverInterruptedEvenBeyondBudget() throws Exception {
        var driver = watching(100_000);
        field("targetItemId").set(driver, "minecraft:emerald_block");
        assertFalse(driver.yieldUnclickedWatchIfDue(null, 900_000));
        assertEquals(0, driver.lastTerminalEvent().sequence());
    }

    @Test void boxSweepGetsItsLongerBudget() throws Exception {
        double prior = Tuning.get("watch.session_max_sec");
        try {
            Tuning.set("watch.session_max_sec", 45);
            var driver = watching(100_000);
            field("watchPagesOverride").setInt(driver, 20);
            assertFalse(driver.yieldUnclickedWatchIfDue(null, 249_999));
            assertTrue(driver.yieldUnclickedWatchIfDue(null, 250_000));
        } finally { Tuning.set("watch.session_max_sec", prior); }
    }

    @Test void repeatedEmptyWatchesDoNotPretendServerIsFailing() throws Exception {
        double prior = Tuning.get("watch.session_max_sec");
        ServerStrain.reset();
        try {
            Tuning.set("watch.session_max_sec", 45);
            for (int i = 0; i < 4; i++) assertTrue(watching(100_000).yieldUnclickedWatchIfDue(null, 145_000));
            assertFalse(ServerStrain.holding());
            assertEquals(1.0, ServerStrain.pacingMultiplier());
        } finally { Tuning.set("watch.session_max_sec", prior); ServerStrain.reset(); }
    }

    @Test void disabledBudgetAndNonWatchActionsAreUnaffected() throws Exception {
        double prior = Tuning.get("watch.session_max_sec");
        try {
            Tuning.set("watch.session_max_sec", 0);
            assertFalse(watching(100_000).yieldUnclickedWatchIfDue(null, 900_000));
            Tuning.set("watch.session_max_sec", 45);
            var driver = watching(100_000);
            field("watching").setBoolean(driver, false);
            assertFalse(driver.yieldUnclickedWatchIfDue(null, 900_000));
        } finally { Tuning.set("watch.session_max_sec", prior); }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static AutomatedExecutionDriver watching(long started) throws Exception {
        var driver = new AutomatedExecutionDriver();
        Field operation = field("operation");
        operation.set(driver, Enum.valueOf((Class) operation.getType(), "BUY"));
        field("watching").setBoolean(driver, true);
        field("watchStartedAtMillis").setLong(driver, started);
        field("targetListingKey").set(driver, "watch:all");
        return driver;
    }

    private static Field field(String name) throws Exception {
        Field field = AutomatedExecutionDriver.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
}
