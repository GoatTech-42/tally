package dev.doughbay.fabric;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static dev.doughbay.fabric.AutomatedExecutionDriver.PendingDispatch.*;
import static org.junit.jupiter.api.Assertions.*;

class AutomatedExecutionDriverCommandTest {
    private final AutomatedExecutionDriver driver = new AutomatedExecutionDriver();
    private final FakeTransport transport = new FakeTransport();

    @Test void rejectedBackgroundCommandCannotEscapeIntoMinecraftTick() {
        for (String command : List.of("follow Example_Player", "follow .ExamplePlayer", "pay ExamplePlayer 1.5")) {
            assertDoesNotThrow(() -> driver.sendWhenClear((net.minecraft.client.Minecraft)null, command));
            assertFalse(driver.externalCommandQueued());
        }
    }

    @Test void decoratedItemNameNeverConstructsOrDispatchesASearchCommand() {
        transport.menu = true;
        for (String label : List.of("Cooked Chicken \ud83c\udf57", "Steak \ue001", "\u00a7aChicken", "Chicken/pay")) {
            assertThrows(IllegalArgumentException.class, () -> driver.sendCommand(transport,
                    "ah search " + ItemSearchText.forItem("minecraft:cooked_chicken", label), 100_000, 600));
            assertTrue(transport.sent.isEmpty());
            assertEquals(0, transport.closes);
            assertFalse(driver.commandPending());
        }
    }

    @Test void sharedSenderRefusesEverySpecialCharacterBeforeMenuCloseQueueOrDispatch() {
        for (String command : List.of("ah search Chicken \ud83c\udf57", "pay Example_Player 123", "pay ExamplePlayer 1.5", "minecraft:msg ExamplePlayer hi", "msg ExamplePlayer caf\u00e9", "ah search Chicken;help", "msg ExamplePlayer hi!")) {
            transport.menu = true;
            assertThrows(IllegalArgumentException.class, () -> driver.sendCommand(transport, command, 100_000, 600));
            assertTrue(transport.sent.isEmpty());
            assertEquals(0, transport.closes);
            assertFalse(driver.commandPending());
        }
    }

    @Test void illegalSearchTextIsRefusedBeforeSendingOrDeferring() {
        for (String command : List.of("ah search \u00a7aGold", "ah search gold\nhelp", "ah search gold\u007f")) {
            transport.menu = true;
            assertThrows(IllegalArgumentException.class, () -> driver.sendCommand(transport, command, 100_000, 600));
            assertTrue(transport.sent.isEmpty());
            assertEquals(0, transport.closes);
        }
    }

    @Test void illegalPanelTextIsRefusedWithoutAnUncertainHandoff() {
        Object connection = new Object(); transport.connection = connection; transport.panelAllowed = true;
        driver.queuePanelCommand("invalid-message", "msg ExamplePlayer \u00a7hello", 200_000, connection);
        driver.drainExternalCommands(transport, 100_000);
        assertTrue(transport.sent.isEmpty());
        assertEquals("REFUSED", driver.panelCommandStatus("invalid-message"));
    }

    @Test void backgroundCommandsCannotSendOrQueueIllegalText() {
        for (boolean menu : new boolean[]{false, true}) {
            transport.menu = menu;
            assertThrows(IllegalArgumentException.class, () -> driver.sendWhenClear(
                    transport, "pay ExamplePlayer 200m\u00a7", 100_000, true));
            assertFalse(driver.externalCommandQueued());
            assertTrue(transport.sent.isEmpty());
        }
    }

    @Test void panelPaymentUsesNativePacingGatesAndSendsAtMostOnce() throws Exception {
        Object connection=new Object();transport.connection=connection;transport.panelAllowed=false;
        driver.queuePanelCommand("request-one","pay ExamplePlayer 123",200_000,connection);
        driver.queuePanelCommand("request-one","pay ExamplePlayer 123",200_000,connection);
        driver.drainExternalCommands(transport,100_000);assertTrue(transport.sent.isEmpty());
        transport.panelAllowed=true;transport.holdUntil=110_000;driver.drainExternalCommands(transport,109_999);assertTrue(transport.sent.isEmpty());
        driver.setExternalAllowed(false);driver.drainExternalCommands(transport,110_000);assertTrue(transport.sent.isEmpty());
        driver.setExternalAllowed(true);driver.drainExternalCommands(transport,110_001);driver.drainExternalCommands(transport,120_000);
        assertEquals(List.of("pay ExamplePlayer 123"),transport.sent);assertEquals("SENT",driver.panelCommandStatus("request-one"));
    }
    @Test void paymentCannotBeSentOnAReplacementConnectionOrCancelledAsPayroll() {
        Object connection=new Object();transport.connection=new Object();transport.panelAllowed=true;
        driver.queuePanelCommand("request-two","pay ExamplePlayer 123",200_000,connection);
        assertFalse(driver.withdrawExternal("pay ExamplePlayer 123"));
        driver.drainExternalCommands(transport,100_000);assertTrue(transport.sent.isEmpty());assertEquals("CANCELLED",driver.panelCommandStatus("request-two"));
    }

    @Test
    void genericQueueReproducesUnsentEscapeBehindIdleOrders() {
        transport.menu = true;
        transport.escapeClose = true;
        driver.sendWhenClear(transport, "rtp west", 100_000, true);
        driver.drainExternalCommands(transport, 120_001);
        assertEquals(0, transport.closes);
        assertTrue(driver.externalQueued("rtp west"));
        assertTrue(transport.sent.isEmpty());
        assertEquals(0, driver.lastEscapeSentAt());
    }

    @Test
    void escapeClosesIdleOrdersThenSendsOnceWithActualDispatchTime() {
        transport.menu = true;
        transport.escapeClose = true;
        driver.sendEscapeWhenClear(transport, "rtp west", 100_000, true);
        assertEquals(1, transport.closes);
        assertTrue(transport.sent.isEmpty());
        assertEquals(0, driver.lastEscapeSentAt(), "Closing the page is not a teleport send");
        driver.drainExternalCommands(transport, 101_000);
        driver.drainExternalCommands(transport, 102_000);
        assertEquals(List.of("rtp west"), transport.sent);
        assertEquals(101_000, driver.lastEscapeSentAt());
        assertEquals("rtp west", driver.lastEscapeCommand());
    }

    @Test
    void escapeDoesNotCloseMenusDuringAnOperationOrUnsafeInventory() throws Exception {
        activateListing();
        transport.menu = true;
        transport.escapeClose = true;
        driver.sendEscapeWhenClear(transport, "rtp", 100_000, true);
        driver.drainExternalCommands(transport, 101_000);
        assertEquals(0, transport.closes);
        clearOperation();
        transport.escapeClose = false;
        driver.drainExternalCommands(transport, 102_000);
        assertEquals(0, transport.closes);
        assertTrue(transport.sent.isEmpty());
    }

    @Test
    void cancelledEscapeCannotTeleportLaterAfterMenuCloses() {
        transport.menu = true;
        driver.sendEscapeWhenClear(transport, "rtp", 100_000, true);
        assertTrue(driver.withdrawExternal("rtp"));
        transport.menu = false;
        driver.drainExternalCommands(transport, 200_000);
        assertTrue(transport.sent.isEmpty());
        assertEquals(0, driver.lastEscapeSentAt());
    }

    @Test
    void escapeStillHonorsServerHoldAndExternalSafetyGate() {
        transport.menu = true;
        transport.escapeClose = true;
        transport.holdUntil = 130_000;
        driver.sendEscapeWhenClear(transport, "rtp", 100_000, true);
        driver.drainExternalCommands(transport, 129_999);
        assertEquals(0, transport.closes);
        driver.setExternalAllowed(false);
        driver.drainExternalCommands(transport, 130_000);
        assertEquals(0, transport.closes);
        driver.setExternalAllowed(true);
        driver.drainExternalCommands(transport, 131_000);
        assertEquals(1, transport.closes);
        driver.drainExternalCommands(transport, 132_000);
        assertEquals(List.of("rtp"), transport.sent);
    }

    @Test
    void onlyKnownTradingPagesWithEmptyCursorCanCloseForEscape() {
        assertTrue(AutomatedExecutionDriver.escapeMenuMayClose("Your Orders", true));
        assertTrue(AutomatedExecutionDriver.escapeMenuMayClose("Auction House", true));
        assertFalse(AutomatedExecutionDriver.escapeMenuMayClose("Your Orders", false));
        assertFalse(AutomatedExecutionDriver.escapeMenuMayClose("Confirm Auction Purchase", true));
        assertFalse(AutomatedExecutionDriver.escapeMenuMayClose("Large Chest", true));
        assertFalse(AutomatedExecutionDriver.escapeMenuMayClose("RTP", true));
    }

    @Test
    void queuedSaleCannotUseAnEmptyOrChangedHandAfterTheDelay() throws Exception {
        activateListing();
        set("lastCommandSentAt",100_000L);
        driver.sendCommand(transport,"ah sell 20000",100_100,600);
        transport.listingBlocker="Listing stack changed before dispatch; no sell command sent";
        assertThrows(IllegalStateException.class,()->driver.dispatchPendingCommand(transport,100_600));
        assertTrue(transport.sent.isEmpty());
        assertEquals(100_000L,driver.lastCommandSentAt());
        clearOperation();
        assertEquals(NONE,driver.dispatchPendingCommand(transport,120_000));
    }

    @Test
    void listingHandRequiresSameSlotStackComponentsAndUnchangedInventory() {
        assertTrue(AutomatedExecutionDriver.listingHandUnchanged(8,8,64,64,true,false,false,128,128));
        assertFalse(AutomatedExecutionDriver.listingHandUnchanged(8,8,64,0,true,false,false,128,64));
        assertFalse(AutomatedExecutionDriver.listingHandUnchanged(8,7,64,64,true,false,false,128,128));
        assertFalse(AutomatedExecutionDriver.listingHandUnchanged(8,8,64,64,false,false,false,128,128));
        assertFalse(AutomatedExecutionDriver.listingHandUnchanged(8,8,64,64,true,true,false,128,128));
        assertFalse(AutomatedExecutionDriver.listingHandUnchanged(8,8,64,64,true,false,true,128,128));
        assertFalse(AutomatedExecutionDriver.listingHandUnchanged(8,8,64,64,true,false,false,128,64),
                "a late receipt cannot make an already removed stack safe to list again");
    }

    @Test
    void changedHandAfterFirstSellWithdrawsRetryAndAllowsLateReceipt() throws Exception {
        activateListing();
        driver.sendCommand(transport, "ah sell 20000", 100_000, 600);
        driver.sendCommand(transport, "ah sell 20000", 100_100, 600);
        transport.listingBlocker = "Listing stack changed before dispatch; no sell command sent";
        long terminal = driver.lastTerminalEvent().sequence();

        assertEquals(NONE, driver.dispatchPendingCommand(transport, 100_600));
        assertFalse(driver.commandPending());
        assertEquals(List.of("ah sell 20000"), transport.sent);
        assertEquals(terminal, driver.lastTerminalEvent().sequence(),
                "inventory movement alone must neither finish nor abort the first send");
        driver.observeGameMessage("You listed 64 Redstone for $ 20K");
        assertEquals("You listed 64 Redstone for $ 20K", get("listingReceipt"));
        assertEquals("WAITING_FOR_CONFIRMATION", get("phase").toString(),
                "the normal receipt and inventory verifier still owns completion");
        assertEquals(NONE, driver.dispatchPendingCommand(transport, 120_000));
        assertEquals(1, transport.sent.size());
    }

    @Test
    void changedHandWithoutReceiptCannotInventListingSuccess() throws Exception {
        activateListing();
        driver.sendCommand(transport, "ah sell 20000", 100_000, 600);
        driver.sendCommand(transport, "ah sell 20000", 100_100, 600);
        transport.listingBlocker = "Listing stack changed before dispatch; no sell command sent";
        long terminal = driver.lastTerminalEvent().sequence();

        assertEquals(NONE, driver.dispatchPendingCommand(transport, 100_600));
        assertEquals("", get("listingReceipt"));
        assertEquals(terminal, driver.lastTerminalEvent().sequence());
        assertEquals("WAITING_FOR_CONFIRMATION", get("phase").toString());
        assertEquals(List.of("ah sell 20000"), transport.sent);
    }

    @Test
    void lateSellConfirmationIsNotClosedByQueuedRetry() throws Exception {
        activateListing();
        long now = 100_000;
        driver.sendCommand(transport, "ah sell 20000", now, 600);
        driver.sendCommand(transport, "ah sell 20000", now + 100, 600);
        transport.menu = true; // The first command's delayed response arrived during the retry gap.
        assertEquals(NONE, driver.dispatchPendingCommand(transport, now + 600));
        assertEquals(0, transport.closes, "let the existing confirmation verifier inspect the late menu");
        assertFalse(driver.commandPending());
        assertEquals(List.of("ah sell 20000"), transport.sent);
        assertEquals("WAITING_FOR_CONFIRMATION", get("phase").toString());
    }

    @Test
    void lateListingReceiptWithdrawsRetryWithoutBypassingInventoryVerification() throws Exception {
        activateListing();
        long now = System.currentTimeMillis();
        driver.sendCommand(transport, "ah sell 20000", now, 600);
        driver.sendCommand(transport, "ah sell 20000", now + 100, 600);
        driver.observeGameMessage("You listed 64 Redstone for $ 20K");
        transport.listingBlocker = "Listing stack changed before dispatch; no sell command sent";
        transport.holdUntil = now + 100_000;
        assertEquals(NONE, driver.dispatchPendingCommand(transport, now + 200));
        assertFalse(driver.commandPending());
        assertEquals(List.of("ah sell 20000"), transport.sent);
        assertEquals("WAITING_FOR_CONFIRMATION", get("phase").toString(),
                "receipt alone must not finish the operation or invent a listed position");
        assertEquals("You listed 64 Redstone for $ 20K", get("listingReceipt"));
    }

    @Test
    void serverRefusalWithdrawsQueuedSellInsteadOfRepeatingIt() throws Exception {
        activateListing();
        driver.sendCommand(transport, "ah sell 20000", 100_000, 600);
        driver.sendCommand(transport, "ah sell 20000", 100_100, 600);
        driver.observeGameMessage("You have too many listed items");
        assertEquals(NONE, driver.dispatchPendingCommand(transport, 100_600));
        assertFalse(driver.commandPending());
        assertEquals(List.of("ah sell 20000"), transport.sent);
    }

    @Test
    void initialQueuedSaleStillClosesOldMenuAndDoesNotTreatItAsConfirmation() throws Exception {
        activateListing();
        transport.menu = true;
        transport.stickyMenu = true;
        driver.sendCommand(transport, "ah sell 20000", 100_000, 600);
        assertEquals(WAITING, driver.dispatchPendingCommand(transport, 100_600));
        assertTrue(driver.commandPending());
        assertTrue(transport.sent.isEmpty());
        assertEquals(2, transport.closes);
    }

    @Test
    void sellRetriesHonorLongServerBackoffWithoutTimingOutInTheQueue() throws Exception {
        activateListing();
        transport.holdUntil = 220_000;
        driver.sendCommand(transport, "ah sell 15500", 100_000, 600);
        assertTrue(driver.commandPending());
        assertTrue(transport.sent.isEmpty());
        assertEquals(WAITING, driver.dispatchPendingCommand(transport, 160_000));
        assertEquals(WAITING, driver.dispatchPendingCommand(transport, 219_999));
        assertEquals(SENT, driver.dispatchPendingCommand(transport, 220_000));
        assertFalse(driver.commandPending());
        assertEquals(220_000, driver.lastCommandSentAt());
        assertEquals(List.of("ah sell 15500"), transport.sent);
    }

    @Test
    void newlyStartedHoldAlsoProtectsAnAlreadyQueuedCommand() throws Exception {
        activateListing();
        set("lastCommandSentAt", 100_000L);
        driver.sendCommand(transport, "ah sell 15500", 100_100, 600);
        transport.holdUntil = 130_000;
        assertEquals(WAITING, driver.dispatchPendingCommand(transport, 106_000));
        assertEquals(SENT, driver.dispatchPendingCommand(transport, 130_000));
        assertEquals(List.of("ah sell 15500"), transport.sent);
    }

    @Test
    void backgroundCommandsCannotBreakTheServerHold() throws Exception {
        transport.holdUntil = 130_000;
        driver.sendWhenClear(transport, "pay Alex 100", 100_000, true);
        driver.drainExternalCommands(transport, 110_000);
        assertTrue(transport.sent.isEmpty());
        driver.drainExternalCommands(transport, 130_000);
        assertEquals(List.of("pay Alex 100"), transport.sent);
    }

    @Test
    void backgroundPaymentCannotDismissSellConfirmationAndSendsAfterOperationEnds() throws Exception {
        activateListing();
        transport.menu = true;
        driver.sendWhenClear(transport, "pay Alex 100", 100_000, true);
        driver.drainExternalCommands(transport, 200_000);
        assertTrue(transport.menu);
        assertEquals(0, transport.closes);
        assertTrue(transport.sent.isEmpty());

        clearOperation();
        driver.drainExternalCommands(transport, 300_000);
        assertEquals(0, transport.closes, "Even a late dialog must not be dismissed by payroll");
        transport.menu = false;
        driver.drainExternalCommands(transport, 400_000);
        assertEquals(List.of("pay Alex 100"), transport.sent);
    }

    @Test
    void backgroundCommandAlsoWaitsWhileConfirmationHasNotArrived() throws Exception {
        activateListing();
        driver.sendWhenClear(transport, "pay Alex 100", 100_000, true);
        driver.drainExternalCommands(transport, 200_000);
        assertTrue(transport.sent.isEmpty());
        clearOperation();
        driver.drainExternalCommands(transport, 300_000);
        assertEquals(List.of("pay Alex 100"), transport.sent);
    }

    @Test
    void deferredSellGetsItsFullResponseBudgetOnlyAfterDispatch() throws Exception {
        activateListing();
        set("lastCommandSentAt", 100_000L);
        set("ticksInPhase", 139);
        driver.sendCommand(transport, "ah sell 20000", 100_100, 10_000);
        assertEquals(WAITING, driver.dispatchPendingCommand(transport, 108_000));
        assertTrue(transport.sent.isEmpty());
        assertEquals(139, get("ticksInPhase"));
        assertEquals(SENT, driver.dispatchPendingCommand(transport, 110_000));
        assertEquals(0, get("ticksInPhase"));
        assertEquals(110_000L, driver.lastCommandSentAt());
        assertEquals(List.of("ah sell 20000"), transport.sent);
        assertEquals(NONE, driver.dispatchPendingCommand(transport, 120_000));
        assertEquals(1, transport.sent.size(), "A dispatched sale must not be replayed");
    }

    @Test
    void menuClosingDoesNotPretendCommandWasSent() throws Exception {
        activateListing();
        transport.menu = true;
        transport.stickyMenu = true;
        driver.sendCommand(transport, "ah sell 20000", 100_000, 600);
        assertEquals(WAITING, driver.dispatchPendingCommand(transport, 100_600));
        assertEquals(0, driver.lastCommandSentAt());
        assertTrue(transport.sent.isEmpty());
        assertEquals(EXPIRED, driver.dispatchPendingCommand(transport, 145_000));
        assertTrue(transport.sent.isEmpty());
    }

    @Test
    void changedSendHistoryDoesNotGetBypassedWhenQueuedCommandBecomesDue() throws Exception {
        activateListing();
        set("lastCommandSentAt", 100_000L);
        driver.sendCommand(transport, "ah sell 20000", 100_100, 600);
        set("lastCommandSentAt", 100_500L);
        assertEquals(WAITING, driver.dispatchPendingCommand(transport, 100_600));
        assertEquals(SENT, driver.dispatchPendingCommand(transport, 101_100));
        assertEquals(List.of("ah sell 20000"), transport.sent);
    }

    @Test
    void terminalCleanupDiscardsQueuedSaleAndStaleCooldownRetry() throws Exception {
        activateListing();
        set("lastCommandSentAt", 100_000L);
        driver.sendCommand(transport, "ah sell 20000", 100_100, 600);
        clearOperation();
        activateListing();
        driver.observeGameMessage("You need to wait another 0.25 seconds to execute a command");
        assertEquals(NONE, driver.dispatchPendingCommand(transport, 110_000));
        assertTrue(transport.sent.isEmpty());
        assertEquals(100_000L, driver.lastCommandSentAt(), "Keep pacing history across operations");
    }

    @Test
    void cooldownForPreviousCommandCannotReplacePendingSale() throws Exception {
        activateListing();
        driver.sendCommand(transport, "ah", 100_000, 600);
        driver.sendCommand(transport, "ah sell 20000", 100_100, 600);
        driver.observeGameMessage("You need to wait another 0.25 seconds to execute a command");
        assertEquals(SENT, driver.dispatchPendingCommand(transport, 100_600));
        assertEquals(List.of("ah", "ah sell 20000"), transport.sent);
    }

    @Test
    void explicitServerCooldownRetriesDispatchedSaleButNotAfterConfirmation() throws Exception {
        activateListing();
        long now = System.currentTimeMillis();
        driver.sendCommand(transport, "ah sell 20000", now, 600);
        driver.observeGameMessage("You need to wait another 0.25 seconds to execute a command");
        assertEquals(SENT, driver.dispatchPendingCommand(transport, now + 20_000));
        enumValue("phase", "WAITING_FOR_COMPLETION");
        driver.observeGameMessage("You need to wait another 0.25 seconds to execute a command");
        assertEquals(NONE, driver.dispatchPendingCommand(transport, now + 30_000));
        assertEquals(List.of("ah sell 20000", "ah sell 20000"), transport.sent);
    }

    private void activateListing() throws Exception {
        enumValue("operation", "LIST");
        enumValue("phase", "WAITING_FOR_CONFIRMATION");
    }

    private void clearOperation() throws Exception {
        var method = AutomatedExecutionDriver.class.getDeclaredMethod("clearTarget");
        method.setAccessible(true);
        method.invoke(driver);
    }

    private void enumValue(String name, String value) throws Exception {
        Field field = field(name);
        for (Object candidate : field.getType().getEnumConstants()) {
            if (((Enum<?>) candidate).name().equals(value)) {
                field.set(driver, candidate);
                return;
            }
        }
        fail("Missing enum value " + value);
    }

    private void set(String name, Object value) throws Exception { field(name).set(driver, value); }
    private Object get(String name) throws Exception { return field(name).get(driver); }
    private Field field(String name) throws Exception {
        Field field = AutomatedExecutionDriver.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static final class FakeTransport implements AutomatedExecutionDriver.CommandTransport {
        Object connection;boolean panelAllowed;
        public Object connection(){return connection;}
        public boolean panelCommandAllowed(){return panelAllowed;}
        boolean menu;
        boolean stickyMenu;
        boolean escapeClose;
        int closes;
        long holdUntil;
        String listingBlocker;
        final List<String> sent = new ArrayList<>();
        public boolean menuOpen() { return menu; }
        public boolean escapeMenuMayClose() { return escapeClose; }
        public long notBeforeMillis() { return holdUntil; }
        public String listingBlocker() { return listingBlocker; }
        public void closeMenu() { closes++; if (!stickyMenu) menu = false; }
        public void send(String command) { sent.add(command); }
    }
}
