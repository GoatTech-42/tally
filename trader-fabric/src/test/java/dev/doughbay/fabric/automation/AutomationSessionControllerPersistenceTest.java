package dev.doughbay.fabric.automation;

import dev.doughbay.core.automation.ContinuousAutomationPolicy;
import dev.doughbay.core.execution.ExecutionResult;
import dev.doughbay.fabric.AutomatedExecutionDriver;
import dev.doughbay.storage.AutomationManualResolution;
import dev.doughbay.storage.AutomationPersistencePort;
import dev.doughbay.storage.AutomationSessionCheckpoint;
import dev.doughbay.storage.AutomationSessionRecovery;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class AutomationSessionControllerPersistenceTest {
    @Test void parkedSaleDuringSessionCloseCannotReopenItsDurableSession() throws Exception {
        var p=new FakePersistence();
        var c=new AutomationSessionController(new AutomatedExecutionDriver());
        c.setPersistence(p);c.tick(null,null);
        setField(c,"policy",policy());setField(c,"state",AutomationSessionController.State.MONITORING);
        setField(c,"committedSpend",10000L);
        var listed=new dev.doughbay.core.model.Position(42,"REAL","minecraft:diamond",
                dev.doughbay.core.model.StackBucket.of(64),64,10000,12000,1000,0,0,0,Double.NaN,
                dev.doughbay.core.model.PositionStatus.LISTED);
        ((java.util.List<dev.doughbay.core.model.Position>)field(c,"openListings")).add(listed);
        var end=AutomationSessionController.class.getDeclaredMethod("requestSessionEnd",String.class);
        end.setAccessible(true);assertTrue((boolean)end.invoke(c,"Test supported session close"));
        assertEquals(AutomationSessionController.State.PAUSED,field(c,"state"));
        var sale=AutomationSessionController.class.getDeclaredMethod("closeOpenListingSoldOffline",dev.doughbay.core.model.Position.class,long.class);
        sale.setAccessible(true);sale.invoke(c,listed,2000L);
        var last=p.checkpoints.getLast();
        assertFalse(last.sessionOpen(),"A sale queued after the close checkpoint must not reopen the session");
        assertEquals("STOPPED",last.controllerState());
        assertEquals(42,last.position().positionId());
        assertEquals(dev.doughbay.core.model.PositionStatus.SOLD,last.position().status());
        assertEquals(0,last.committedSpend(),"Preserve the legitimate release of this sold position's cost");
        p.acknowledgeAll();
        var process=AutomationSessionController.class.getDeclaredMethod("processOutOfBandPersistence");
        process.setAccessible(true);process.invoke(c);
        assertEquals(AutomationSessionController.State.STOPPED,field(c,"state"));
        assertFalse((boolean)field(c,"sessionOpen"));
    }

    @Test void parkedSaleWithoutCloseIntentKeepsItsActiveSessionAndUnrelatedCaps() throws Exception {
        var p=new FakePersistence();var c=new AutomationSessionController(new AutomatedExecutionDriver());
        c.setPersistence(p);c.tick(null,null);setField(c,"state",AutomationSessionController.State.MONITORING);
        setField(c,"committedSpend",15000L);
        var listed=new dev.doughbay.core.model.Position(43,"REAL","minecraft:diamond",
                dev.doughbay.core.model.StackBucket.of(64),64,10000,12000,1000,0,0,0,Double.NaN,
                dev.doughbay.core.model.PositionStatus.LISTED);
        ((java.util.List<dev.doughbay.core.model.Position>)field(c,"openListings")).add(listed);
        var sale=AutomationSessionController.class.getDeclaredMethod("closeOpenListingSoldOffline",dev.doughbay.core.model.Position.class,long.class);
        sale.setAccessible(true);sale.invoke(c,listed,2000L);var last=p.checkpoints.getLast();
        assertTrue(last.sessionOpen());assertEquals("MONITORING",last.controllerState());
        assertEquals(5000,last.committedSpend());assertEquals(1,last.tradesStarted());
        assertEquals(43,last.position().positionId());
    }

    @Test void freshStoppedAccountNeedsNoInventedCheckpointToLeaveSafely() throws Exception {
        var p=new FakePersistence();
        p.status=new AutomationPersistencePort.Status(AutomationPersistencePort.LoadState.READY,
                AutomationSessionRecovery.empty(),0,0,"Empty recovery loaded");
        var c=new AutomationSessionController(new AutomatedExecutionDriver());c.setPersistence(p);
        assertFalse(c.safeToLeave(),"Loading/applying recovery must finish first");
        c.tick(null,null);
        assertTrue(c.safeToLeave(),"A never-started empty account has no write to acknowledge");
        assertEquals(0,p.submissions.get(),"Do not fabricate a checkpoint or start trading");
    }

    @Test void zeroGenerationCannotHideAnOpenSessionOrRefusedWrite() throws Exception {
        var p=new FakePersistence();
        p.status=new AutomationPersistencePort.Status(AutomationPersistencePort.LoadState.READY,
                AutomationSessionRecovery.empty(),0,0,"Empty recovery loaded");
        var c=new AutomationSessionController(new AutomatedExecutionDriver());c.setPersistence(p);c.tick(null,null);
        setField(c,"sessionOpen",true);assertFalse(c.safeToLeave());
        setField(c,"sessionOpen",false);
        p.status=new AutomationPersistencePort.Status(AutomationPersistencePort.LoadState.READY,
                AutomationSessionRecovery.empty(),0,0,"Refused write",1);
        assertFalse(c.safeToLeave());
    }

    @Test void acceptedCheckpointStillNeedsItsActualDurableAcknowledgement() throws Exception {
        var p=new FakePersistence();
        p.status=new AutomationPersistencePort.Status(AutomationPersistencePort.LoadState.READY,
                AutomationSessionRecovery.empty(),1,0,"Write pending");
        var c=new AutomationSessionController(new AutomatedExecutionDriver());c.setPersistence(p);c.tick(null,null);
        assertFalse(c.safeToLeave());p.acknowledgeAll();assertTrue(c.safeToLeave());
    }

    @Test void flipsOnlyModeReleasesAnUndispatchedWatchThroughADurableCheckpoint() throws Exception {
        double previous = dev.doughbay.fabric.Tuning.get("prices.only");
        try {
            dev.doughbay.fabric.Tuning.set("prices.only", 1);
            var persistence = new FakePersistence();
            var c = new AutomationSessionController(new AutomatedExecutionDriver());
            c.setPersistence(persistence);
            setField(c, "state", AutomationSessionController.State.BUYING);
            setField(c, "sessionOpen", true);
            setField(c, "watchBuy", true);
            setField(c, "buyCommandStarted", false);
            setField(c, "buyIntentPersistenceGeneration", 1L);
            setField(c, "committedSpend", 12345L);
            var method = AutomationSessionController.class.getDeclaredMethod("observeBuyTerminal",
                    net.minecraft.client.Minecraft.class, dev.doughbay.fabric.MarketWatcher.Snapshot.class, long.class);
            method.setAccessible(true);
            method.invoke(c, null, null, 1000L);
            assertEquals(AutomationSessionController.State.SCANNING, field(c, "state"));
            assertEquals(false, field(c, "watchBuy"));
            assertEquals(0L, field(c, "buyIntentPersistenceGeneration"));
            assertEquals(12345L, field(c, "committedSpend"));
            assertEquals(1, persistence.submissions.get(), "release must be queued durably, never silently cleared");
        } finally { dev.doughbay.fabric.Tuning.set("prices.only", previous); }
    }


    @Test void collectedBatchRetainsBothStacksAndAllCostBeforeListing() throws Exception {
        checkCollectedBatch(false);
    }

    @Test void rejectedBatchWriteStillRetainsEveryDeliveredStackWithoutDispatch() throws Exception {
        checkCollectedBatch(true);
    }

    private void checkCollectedBatch(boolean rejected) throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        double singles = dev.doughbay.fabric.Tuning.get("list.singles");
        try {
        dev.doughbay.fabric.Tuning.set("list.singles", 0);
        var persistence = new FakePersistence();
        persistence.rejectSubmissions = rejected;
        var c = new AutomationSessionController(new AutomatedExecutionDriver());
        c.setPersistence(persistence);
        setField(c, "policy", policy());
        setField(c, "state", AutomationSessionController.State.BID_DESK);
        var method = AutomationSessionController.class.getDeclaredMethod("bookCollectedBatch",
                net.minecraft.client.Minecraft.class, long.class, String.class, long.class, java.util.List.class);
        method.setAccessible(true);
        assertEquals(!rejected, method.invoke(c, null, 1000L, "minecraft:stone", 100L,
                CollectedStacks.split(128, 64, 12800, 100)));
        var queued = (java.util.List<dev.doughbay.core.model.Position>) field(c, "recoveredQueue");
        assertEquals(2, queued.size());
        assertEquals(128, queued.stream().mapToInt(dev.doughbay.core.model.Position::quantity).sum());
        assertEquals(12800, queued.stream().mapToLong(dev.doughbay.core.model.Position::purchasePrice).sum());
        assertEquals(12800L, field(c, "committedSpend"));
        assertEquals(2, queued.stream().map(dev.doughbay.core.model.Position::positionId).distinct().count());
        assertEquals(null, field(c, "trackedPosition"), "booking must not dispatch a listing");
        assertEquals(rejected ? AutomationSessionController.State.PAUSED : AutomationSessionController.State.SCANNING,
                field(c, "state"));
        } finally {
            dev.doughbay.fabric.Tuning.set("list.singles", singles);
        }
    }

    @Test
    void normalResumeNeverForceResetsARecoveredPurchaseMissingInHandEvidence() throws Exception {
        var persistence=new FakePersistence();
        var controller=new AutomationSessionController(new AutomatedExecutionDriver());controller.setPersistence(persistence);
        var purchase=new dev.doughbay.core.model.Position(62,"REAL","minecraft:deepslate_diamond_ore",
                dev.doughbay.core.model.StackBucket.of(62),62,589000,1750074,1000,0,0,0,Double.NaN,
                dev.doughbay.core.model.PositionStatus.PURCHASED);
        setField(controller,"state",AutomationSessionController.State.PAUSED);
        setField(controller,"recoveryApplied",true);setField(controller,"recoveredSession",true);
        setField(controller,"sessionOpen",true);setField(controller,"pauseResumable",true);
        setField(controller,"trackedPosition",purchase);setField(controller,"committedSpend",320830476L);
        assertFalse(controller.resumeRecoveredSession(policy()).ok());
        assertFalse(controller.resume().ok());
        assertEquals(purchase,field(controller,"trackedPosition"));
        assertEquals(320830476L,field(controller,"committedSpend"));
        assertEquals(AutomationSessionController.State.PAUSED,field(controller,"state"));
        assertEquals(false,field(controller,"armed"));assertEquals(true,field(controller,"sessionOpen"));
        assertEquals(0,persistence.submissions.get(),"ordinary resume must not write off the purchase");
    }

    @Test
    void unchangedStackQuantityKeepsPositionAndRetryIdentityWithoutLedgerChurn() throws Exception {
        var persistence = new FakePersistence();
        var controller = new AutomationSessionController(new AutomatedExecutionDriver());
        controller.setPersistence(persistence);
        var position = new dev.doughbay.core.model.Position(42, "REAL", "minecraft:diamond_leggings",
                dev.doughbay.core.model.StackBucket.of(1), 1, 10_000, 12_000,
                1_000, 0, 0, 0, Double.NaN, dev.doughbay.core.model.PositionStatus.PURCHASED);
        setField(controller, "prepareRetryPositionId", 42L);
        setField(controller, "prepareRetries", 2);
        var method = AutomationSessionController.class.getDeclaredMethod("supersedeQuantity",
                dev.doughbay.core.model.Position.class, int.class, long.class);
        method.setAccessible(true);
        for (int attempt = 0; attempt < 5; attempt++) {
            org.junit.jupiter.api.Assertions.assertSame(position, method.invoke(controller, position, 1, 2_000L));
        }
        assertEquals(0, persistence.submissions.get());
        assertEquals(42L, field(controller, "prepareRetryPositionId"));
        assertEquals(2, field(controller, "prepareRetries"));

        var resized = (dev.doughbay.core.model.Position) method.invoke(controller, position, 2, 2_000L);
        assertEquals(1, persistence.submissions.get());
        assertEquals(2, resized.quantity());
        assertEquals(20_000, resized.purchasePrice());
        assertEquals(24_000, resized.targetPrice());
    }

    @Test
    void durableListIntentSurvivesLongConfirmationRetries() throws Exception {
        FakePersistence persistence = new FakePersistence();
        AutomationSessionController controller = new AutomationSessionController(new AutomatedExecutionDriver());
        controller.setPersistence(persistence);
        persistence.status = new AutomationPersistencePort.Status(AutomationPersistencePort.LoadState.READY,
                persistence.status.recovery(), 7, 7, "Durable");
        setField(controller, "listIntentPersistenceGeneration", 7L);
        checkWatchdog(controller, 1_000);
        checkWatchdog(controller, 121_001);
        assertEquals(7L, field(controller, "listIntentPersistenceGeneration"));
        assertTrue(persistence.status().durable((long) field(controller, "listIntentPersistenceGeneration")),
                "a retry must retain its acknowledged LIST intent after the two-minute watchdog");
        assertEquals(0L, field(controller, "pendingPersistenceSince"));
    }

    @Test
    void stalledWriteRetainsReceiptsForLateAcknowledgement() throws Exception {
        FakePersistence persistence = new FakePersistence();
        AutomationSessionController controller = new AutomationSessionController(new AutomatedExecutionDriver());
        controller.setPersistence(persistence);
        persistence.status = new AutomationPersistencePort.Status(AutomationPersistencePort.LoadState.READY,
                persistence.status.recovery(), 8, 7, "Queued");
        setField(controller, "listIntentPersistenceGeneration", 8L);
        checkWatchdog(controller, 1_000);
        checkWatchdog(controller, 121_001);
        assertEquals(8L, field(controller, "listIntentPersistenceGeneration"));
        persistence.acknowledgeAll();
        checkWatchdog(controller, 122_000);
        assertTrue(persistence.status().durable((long) field(controller, "listIntentPersistenceGeneration")));
        assertEquals(0L, field(controller, "pendingPersistenceSince"));
    }

    @Test
    void persistenceProgressRestartsStallBudget() throws Exception {
        FakePersistence persistence = new FakePersistence();
        AutomationSessionController controller = new AutomationSessionController(new AutomatedExecutionDriver());
        controller.setPersistence(persistence);
        persistence.status = new AutomationPersistencePort.Status(AutomationPersistencePort.LoadState.READY,
                persistence.status.recovery(), 9, 7, "Queued");
        checkWatchdog(controller, 1_000);
        persistence.status = new AutomationPersistencePort.Status(AutomationPersistencePort.LoadState.READY,
                persistence.status.recovery(), 9, 8, "Progress");
        checkWatchdog(controller, 120_000);
        checkWatchdog(controller, 121_001);
        assertEquals(120_000L, field(controller, "pendingPersistenceSince"));
    }

    private static Object field(Object target, String name) throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void checkWatchdog(AutomationSessionController controller, long now)
            throws ReflectiveOperationException {
        var method = AutomationSessionController.class.getDeclaredMethod("checkStuckPersistence", long.class);
        method.setAccessible(true);
        method.invoke(controller, now);
    }

    @Test
    void pendingRecoveredSessionCloseCannotBeFollowedByResumeOpen() {
        FakePersistence persistence = new FakePersistence();
        AutomationSessionController controller =
                new AutomationSessionController(new AutomatedExecutionDriver());
        controller.setPersistence(persistence);
        controller.tick(null, null);

        assertTrue(controller.snapshot().recoveredSession());
        assertFalse(controller.snapshot().unresolvedExposure());
        assertTrue(controller.endRecoveredSession().ok());
        assertEquals("SESSION_CLOSE_PENDING",
                controller.snapshot().manualResolutionStage());
        assertEquals(1, persistence.submissions.get());

        ExecutionResult beforeDurable = controller.resumeRecoveredSession(policy());
        assertEquals(ExecutionResult.Outcome.REFUSED, beforeDurable.outcome());
        assertEquals(1, persistence.submissions.get(),
                "resume must not enqueue OPEN behind an undurable CLOSE");

        persistence.acknowledgeAll();
        ExecutionResult durableButUnconsumed =
                controller.resumeRecoveredSession(policy());
        assertEquals(ExecutionResult.Outcome.REFUSED,
                durableButUnconsumed.outcome());
        assertEquals(1, persistence.submissions.get(),
                "resume must not enqueue OPEN behind a durable but unconsumed CLOSE");
    }

    @Test
    void rejectedCloseStillStopsRunningControllerFailClosed() throws Exception {
        FakePersistence persistence = new FakePersistence();
        AutomationSessionController controller =
                new AutomationSessionController(new AutomatedExecutionDriver());
        controller.setPersistence(persistence);
        controller.tick(null, null);

        setField(controller, "state", AutomationSessionController.State.SCANNING);
        setField(controller, "recoveredSession", false);
        persistence.rejectSubmissions = true;

        controller.stop();
        AutomationSessionController.SessionSnapshot stopped = controller.snapshot();
        assertEquals(AutomationSessionController.State.PAUSED, stopped.state());
        assertFalse(stopped.active());
        assertFalse(stopped.resumable());
        assertTrue(stopped.recoveredSession());
        assertTrue(stopped.detail().contains("could not close durably"));
        assertEquals(1, persistence.submissions.get());

        controller.tick(null, null);
        assertEquals(AutomationSessionController.State.PAUSED,
                controller.snapshot().state(),
                "a rejected CLOSE must never leave scanning able to advance next tick");
    }

    @Test
    void activeRuntimeUsesMonotonicElapsedTimeAndFreezesWhilePaused() throws Exception {
        FakePersistence persistence = new FakePersistence();
        AutomationSessionController controller =
                new AutomationSessionController(new AutomatedExecutionDriver());
        controller.setPersistence(persistence);
        controller.tick(null, null);

        assertEquals(250, controller.snapshot().currentSessionActiveMillis());
        assertEquals(750, controller.snapshot().lifetimeActiveMillis());

        setField(controller, "state", AutomationSessionController.State.SCANNING);
        setField(controller, "sessionOpen", true);
        setField(controller, "activeStateStartedNanos",
                System.nanoTime() - 50_000_000L);
        long whileActive = controller.snapshot().currentSessionActiveMillis();
        assertTrue(whileActive >= 295 && whileActive < 1_000,
                "active runtime should include the monotonic in-flight interval");

        controller.pause("runtime test pause");
        long paused = controller.snapshot().currentSessionActiveMillis();
        Thread.sleep(20);
        assertEquals(paused, controller.snapshot().currentSessionActiveMillis(),
                "PAUSED wall time must never become automation runtime");
        assertEquals(500, controller.snapshot().lifetimeActiveMillis()
                        - controller.snapshot().currentSessionActiveMillis(),
                "session and lifetime counters must advance by the same delta");
    }

    @Test void forceResetRestoresValidatedPolicyBeforeHeartbeat() throws Exception {
        var persistence = new FakePersistence();
        var controller = new AutomationSessionController(new AutomatedExecutionDriver());
        controller.setPersistence(persistence);
        controller.tick(null, null);
        var configured = policy();
        Field global = dev.doughbay.fabric.DoughBayClient.class.getDeclaredField("continuousPolicy");
        global.setAccessible(true);
        Object previous = global.get(null);
        try {
            global.set(null, configured);
            assertTrue(controller.forceReset(null).ok());
            org.junit.jupiter.api.Assertions.assertSame(configured, field(controller, "policy"));
            var heartbeat = AutomationSessionController.class.getDeclaredMethod("queueRuntimeHeartbeat");
            heartbeat.setAccessible(true);
            org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> heartbeat.invoke(controller));
            assertEquals(1, field(controller, "tradesStarted"));
            assertEquals(4_100L, field(controller, "committedSpend"));
        } finally { global.set(null, previous); }
    }

    @Test void forceResetWithoutValidatedPolicyRetainsRecoveryAndExposure() throws Exception {
        var persistence = new FakePersistence();
        var controller = new AutomationSessionController(new AutomatedExecutionDriver());
        controller.setPersistence(persistence);
        controller.tick(null, null);
        var exposure = new dev.doughbay.storage.AutomationUncertainExposure(true, "BUYING", "listing",
                "minecraft:diamond", "minecraft:diamond", 1, 4100, 6000, "seller", 1000);
        setField(controller, "uncertainExposure", exposure);
        Field global = dev.doughbay.fabric.DoughBayClient.class.getDeclaredField("continuousPolicy");
        global.setAccessible(true);
        Object previous = global.get(null);
        try {
            global.set(null, null);
            assertFalse(controller.forceReset(null).ok());
            assertEquals(AutomationSessionController.State.PAUSED, controller.snapshot().state());
            assertTrue(controller.snapshot().recoveredSession());
            assertFalse((boolean) field(controller, "armed"));
            assertEquals(exposure, field(controller, "uncertainExposure"));
            assertEquals(4_100L, field(controller, "committedSpend"));
            assertEquals(0, persistence.submissions.get());
        } finally { global.set(null, previous); }
    }

    @Test void activeTickWithoutPolicyPausesBeforeSpendingReconciliation() throws Exception {
        var controller = new AutomationSessionController(new AutomatedExecutionDriver());
        controller.setPersistence(new FakePersistence());
        controller.tick(null, null);
        setField(controller, "state", AutomationSessionController.State.SCANNING);
        setField(controller, "recoveredSession", false);
        var tick = AutomationSessionController.class.getDeclaredMethod("tickInternal",
                net.minecraft.client.Minecraft.class, dev.doughbay.fabric.MarketWatcher.Snapshot.class);
        tick.setAccessible(true);
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> tick.invoke(controller, null, null));
        assertEquals(AutomationSessionController.State.PAUSED, controller.snapshot().state());
        assertTrue(controller.snapshot().detail().contains("policy"));
        assertEquals(4_100L, field(controller, "committedSpend"));
    }

    @Test void refusedManualResumePreservesExistingAutomaticRecoveryOwnership() throws Exception {
        for (boolean resumable : new boolean[]{false, true}) {
            var c = internallyPausedAfterLeave();
            setField(c, "pauseResumable", resumable);
            setField(c, "continuousAuthorizationEnabled", true);
            setField(c, "auctionFeesConfirmed", true);
            ((AutomatedExecutionDriver) field(c, "driver")).setAuthorizedExecutionEnabled(true);
            assertTrue(c.restartRecoveryMayResume());
            for (int attempt = 0; attempt < 3; attempt++) {
                var result = c.resume();
                assertFalse(result.ok(), "no world is authorized, even for a resumable pause");
                if (resumable) assertTrue(result.detail().contains("Join a server"), result.detail());
                assertTrue(c.armed(), "a refused request must not cancel the running session's authorization");
                assertTrue(c.restartRecoveryMayResume(), "evasion/restart must retain their existing recovery ownership");
            }
            assertEquals(AutomationSessionController.State.PAUSED, c.snapshot().state());
            assertEquals(4100L, field(c, "committedSpend"));
            assertEquals(1, field(c, "tradesStarted"));
            assertEquals(0, ((FakePersistence) field(c, "persistence")).submissions.get());
        }
    }

    @Test void refusedManualResumeNeverArmsAnOperatorStop() throws Exception {
        var c = internallyPausedAfterLeave();
        setField(c, "armed", false);
        setField(c, "pausedByPlayer", true);
        assertFalse(c.resume().ok());
        assertFalse(c.armed());
        assertFalse(c.restartRecoveryMayResume());
        assertTrue((boolean) field(c, "pausedByPlayer"));
    }

    @Test void ordinaryResumeWaitsForPendingControllerAcknowledgementWithoutDroppingOwnership() throws Exception {
        var c = internallyPausedAfterLeave();
        setField(c, "pauseResumable", true);
        setField(c, "runtimePersistenceGeneration", 7L);
        var p = (FakePersistence) field(c, "persistence");
        p.status = new AutomationPersistencePort.Status(AutomationPersistencePort.LoadState.READY,
                p.status.recovery(), 7, 7, "Durable, not consumed");
        var result = c.resume();
        assertFalse(result.ok());
        assertTrue(result.detail().contains("checkpoint"), result.detail());
        assertTrue(c.armed());
        assertEquals(7L, field(c, "runtimePersistenceGeneration"));
        assertEquals(0, p.submissions.get());
    }

    @Test void evasionResumeBypassesOnlyTheManualLeaveGate() throws Exception {
        double previous = dev.doughbay.fabric.Tuning.get("session.resume_after_leave");
        try {
            dev.doughbay.fabric.Tuning.set("session.resume_after_leave", 0);
            var controller = internallyPausedAfterLeave();
            assertFalse(controller.resumeRecoveredSession(policy()).ok(),
                    "this live cooldown pause is not a recovered session");
            assertFalse(controller.resume().ok(),
                    "ordinary Resume refuses the interrupted cooldown just as on the alt");
            assertFalse(controller.resumeAfterEvasion().ok(), "no test world is authorized");
            assertFalse((boolean) field(controller, "pauseResumable"),
                    "guard recovery must not enter the manual-leave gate that strands cooldown pauses");
            assertEquals(4_100L, field(controller, "committedSpend"));
            assertEquals(1, field(controller, "tradesStarted"));

            var manual = internallyPausedAfterLeave();
            var timer = AutomationSessionController.class.getDeclaredMethod("autoResumeInternalPause",
                    net.minecraft.client.Minecraft.class, long.class);
            timer.setAccessible(true);
            timer.invoke(manual, null, System.currentTimeMillis());
            assertTrue((boolean) field(manual, "pauseResumable"),
                    "ordinary timer still leaves a manual disconnect waiting for Resume");
            assertEquals(AutomationSessionController.State.PAUSED, manual.snapshot().state());
        } finally { dev.doughbay.fabric.Tuning.set("session.resume_after_leave", previous); }
    }

    @Test void protectiveResumeOfDurablyListedLivePauseReachesExistingAuthorizationGate() throws Exception {
        var c=knownListedInternalPause();
        var result=c.resumeAfterEvasion();
        assertFalse(result.ok());
        assertEquals("Continuous automation is disabled in config",result.detail(),
                "A verified live LISTED pause should reach normal Resume authorization, not deadlock as unknown exposure");
        assertTrue((boolean)field(c,"recoveredSession"),"A refused Resume must restore the recovery marker");
        assertEquals(dev.doughbay.core.model.PositionStatus.LISTED,
                ((dev.doughbay.core.model.Position)field(c,"trackedPosition")).status());
        assertEquals(4100L,field(c,"committedSpend"));
        assertEquals(0,((FakePersistence)field(c,"persistence")).submissions.get());
    }

    @Test void protectiveListedResumeRetainsUncertaintyPendingReceiptsAndManualStops() throws Exception {
        var c=knownListedInternalPause();assertTrue(c.durableListedPauseMayResume());
        setField(c,"listedPersistenceGeneration",1L);assertFalse(c.durableListedPauseMayResume());
        setField(c,"listedPersistenceGeneration",0L);setField(c,"pausedByPlayer",true);assertFalse(c.durableListedPauseMayResume());
        setField(c,"pausedByPlayer",false);setField(c,"pauseResumable",false);assertFalse(c.durableListedPauseMayResume());
        setField(c,"pauseResumable",true);setField(c,"staffTransferHold",true);assertFalse(c.durableListedPauseMayResume());
        setField(c,"staffTransferHold",false);setField(c,"uncertainExposure",new dev.doughbay.storage.AutomationUncertainExposure(true,"LISTING","position:44",
                "minecraft:ender_chest","minecraft:ender_chest",64,4100,6000,"",1000));
        assertFalse(c.durableListedPauseMayResume());assertFalse(c.resumeAfterEvasion().ok());
        assertTrue(((dev.doughbay.storage.AutomationUncertainExposure)field(c,"uncertainExposure")).active());
        assertEquals(4100L,field(c,"committedSpend"));
    }

    private static AutomationSessionController knownListedInternalPause() throws Exception {
        var c=internallyPausedAfterLeave();
        setField(c,"recoveredSession",true);setField(c,"pauseResumable",true);
        setField(c,"pausedFromState",AutomationSessionController.State.MONITORING);
        setField(c,"boundListingKey","test-exact-listing");
        var p=(FakePersistence)field(c,"persistence");
        p.status=new AutomationPersistencePort.Status(AutomationPersistencePort.LoadState.READY,p.status.recovery(),1,1,"Exact LISTED receipt durable");
        setField(c,"trackedPosition",new dev.doughbay.core.model.Position(44,"REAL","minecraft:ender_chest",
                dev.doughbay.core.model.StackBucket.of(64),64,4100,6000,1000,System.currentTimeMillis(),0,0,Double.NaN,
                dev.doughbay.core.model.PositionStatus.LISTED));
        return c;
    }

    @Test void evasionResumeCannotRearmManualStopOrDiscardUncertainExposure() throws Exception {
        var controller = internallyPausedAfterLeave();
        setField(controller, "armed", false);
        assertFalse(controller.resumeAfterEvasion().ok());
        assertFalse((boolean) field(controller, "armed"));
        setField(controller, "armed", true);
        setField(controller, "pausedByPlayer", true);
        assertFalse(controller.resumeAfterEvasion().ok());
        setField(controller, "pausedByPlayer", false);
        var exposure = new dev.doughbay.storage.AutomationUncertainExposure(true, "BUYING", "listing",
                "minecraft:diamond", "minecraft:diamond", 1, 4100, 6000, "seller", 1000);
        setField(controller, "uncertainExposure", exposure);
        assertFalse(controller.resumeAfterEvasion().ok());
        assertEquals(exposure, field(controller, "uncertainExposure"));
        assertEquals(4_100L, field(controller, "committedSpend"));
        assertEquals(1, field(controller, "tradesStarted"));
        assertEquals(0, ((FakePersistence) field(controller, "persistence")).submissions.get());
    }

    private static AutomationSessionController internallyPausedAfterLeave() throws Exception {
        var controller = new AutomationSessionController(new AutomatedExecutionDriver());
        controller.setPersistence(new FakePersistence());
        controller.tick(null, null);
        setField(controller, "recoveredSession", false);
        setField(controller, "policy", policy());
        setField(controller, "armed", true);
        setField(controller, "pauseResumable", false);
        setField(controller, "pausedFromState", AutomationSessionController.State.COOLDOWN);
        setField(controller, "stateChangedAtMillis", System.currentTimeMillis() - 300_000L);
        setField(controller, "detail", "PAUSED: Join a server before starting live execution");
        return controller;
    }

    private static ContinuousAutomationPolicy policy() {
        return ContinuousAutomationPolicy.fromConfigValues(
                2, 100_000, 50_000, 5_000,
                12, 80, 10, 8, 120, 1);
    }

    @Test void restartCannotRearmAStoppedOrOperatorPausedSession() throws Exception {
        var controller=new AutomationSessionController(new AutomatedExecutionDriver());
        setField(controller,"state",AutomationSessionController.State.PAUSED);
        setField(controller,"sessionOpen",true);
        setField(controller,"policy",policy());
        setField(controller,"armed",false);
        assertFalse(controller.resumeAfterRestart().ok());
        assertFalse((boolean)field(controller,"armed"));
        setField(controller,"armed",true);
        setField(controller,"pausedByPlayer",true);
        assertFalse(controller.restartRecoveryMayResume());
        assertFalse(controller.resumeAfterRestart().ok());
        assertTrue((boolean)field(controller,"pausedByPlayer"));
    }

    @Test void restartResumeDoesNotForceResetUncertainExposureOrSpendCaps() throws Exception {
        var controller=new AutomationSessionController(new AutomatedExecutionDriver());
        var persistence=new FakePersistence();controller.setPersistence(persistence);
        setField(controller,"recoveryApplied",true);
        setField(controller,"state",AutomationSessionController.State.PAUSED);
        setField(controller,"sessionOpen",true);
        setField(controller,"policy",policy());
        setField(controller,"armed",true);
        setField(controller,"committedSpend",4100L);
        setField(controller,"tradesStarted",1);
        var exposure=new dev.doughbay.storage.AutomationUncertainExposure(true,"BUYING","listing",
                "minecraft:diamond","minecraft:diamond",1,4100,6000,"seller",1000);
        setField(controller,"uncertainExposure",exposure);
        assertFalse(controller.resumeAfterRestart().ok());
        assertEquals(exposure,field(controller,"uncertainExposure"));
        assertEquals(4100L,field(controller,"committedSpend"));
        assertEquals(1,field(controller,"tradesStarted"));
        assertEquals(0,persistence.submissions.get());
    }

    @Test void restartResumeWaitsForVerifiedWorld() throws Exception {
        var controller=new AutomationSessionController(new AutomatedExecutionDriver());
        setField(controller,"state",AutomationSessionController.State.PAUSED);
        setField(controller,"sessionOpen",true);
        setField(controller,"policy",policy());
        setField(controller,"armed",true);
        setField(controller,"limboUntil",System.currentTimeMillis()+60_000L);
        assertTrue(controller.restartRecoveryMayResume());
        var result=controller.resumeAfterRestart();
        assertFalse(result.ok());
        assertTrue(result.detail().contains("not verified"));
    }

    private static void setField(Object target, String name, Object value)
            throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    @Test void pausedResolvedBuyReceiptIsConsumedOnlyAfterTheReleaseCheckpointIsDurable() throws Exception {
        var p=new FakePersistence();var c=new AutomationSessionController(new AutomatedExecutionDriver());
        c.setPersistence(p);c.tick(null,null);
        setField(c,"buyIntentPersistenceGeneration",7L);
        p.status=new AutomationPersistencePort.Status(AutomationPersistencePort.LoadState.READY,p.status.recovery(),8,7,"Release queued");
        consume(c);assertEquals(7L,field(c,"buyIntentPersistenceGeneration"));
        p.acknowledgeAll();consume(c);
        assertEquals(0L,field(c,"buyIntentPersistenceGeneration"));
        assertEquals(null,startBlocker(c));
        assertEquals(AutomationSessionController.State.PAUSED,c.snapshot().state());
        assertEquals(4100L,field(c,"committedSpend"));assertEquals(1,field(c,"tradesStarted"));
        assertEquals(0,p.submissions.get(),"Acknowledgement consumes evidence; it never starts a trade");
    }

    @Test void pausedResolvedListReceiptIsConsumedWithoutRearmingAnOperatorPause() throws Exception {
        var p=new FakePersistence();var c=new AutomationSessionController(new AutomatedExecutionDriver());
        c.setPersistence(p);c.tick(null,null);setField(c,"pausedByPlayer",true);
        setField(c,"listIntentPersistenceGeneration",7L);
        p.status=new AutomationPersistencePort.Status(AutomationPersistencePort.LoadState.READY,p.status.recovery(),8,8,"Release durable");
        consume(c);assertEquals(0L,field(c,"listIntentPersistenceGeneration"));
        assertTrue((boolean)field(c,"pausedByPlayer"));assertFalse(c.restartRecoveryMayResume());
        assertEquals(AutomationSessionController.State.PAUSED,c.snapshot().state());
    }

    @Test void unresolvedIntentInMemoryOrInTheDurableLedgerKeepsItsReceipt() throws Exception {
        var p=new FakePersistence();var c=new AutomationSessionController(new AutomatedExecutionDriver());
        c.setPersistence(p);c.tick(null,null);setField(c,"buyIntentPersistenceGeneration",7L);
        var exposure=new dev.doughbay.storage.AutomationUncertainExposure(true,"BUYING","listing",
                "minecraft:diamond","minecraft:diamond",1,4100,6000,"seller",1000);
        p.status=new AutomationPersistencePort.Status(AutomationPersistencePort.LoadState.READY,p.status.recovery(),8,8,"Durable");
        setField(c,"uncertainExposure",exposure);consume(c);assertEquals(7L,field(c,"buyIntentPersistenceGeneration"));
        setField(c,"uncertainExposure",dev.doughbay.storage.AutomationUncertainExposure.none());
        var recovery=new AutomationSessionRecovery(true,"CONTINUOUS","PAUSED",1,4100,1000,250,750,2000,"","",exposure,java.util.List.of());
        p.status=new AutomationPersistencePort.Status(AutomationPersistencePort.LoadState.READY,recovery,8,8,"Prior intent durable");
        consume(c);assertEquals(7L,field(c,"buyIntentPersistenceGeneration"));
    }

    @Test void aRefusedReleaseCannotBeMaskedByAnUnrelatedLaterDurableWrite() throws Exception {
        var p=new FakePersistence();var c=new AutomationSessionController(new AutomatedExecutionDriver());
        c.setPersistence(p);c.tick(null,null);setField(c,"buyIntentPersistenceGeneration",7L);
        for(long refused:new long[]{7,8}) {
            p.status=new AutomationPersistencePort.Status(AutomationPersistencePort.LoadState.READY,p.status.recovery(),9,9,"Other write durable",refused,java.util.Set.of(refused));
            consume(c);assertEquals(7L,field(c,"buyIntentPersistenceGeneration"));assertTrue(startBlocker(c)!=null);
        }
    }

    @Test void anActiveListingRetryKeepsItsDurableIntentReceipt() throws Exception {
        var p=new FakePersistence();var c=new AutomationSessionController(new AutomatedExecutionDriver());
        c.setPersistence(p);c.tick(null,null);setField(c,"state",AutomationSessionController.State.PREPARING_LIST);
        setField(c,"listIntentPersistenceGeneration",7L);
        p.status=new AutomationPersistencePort.Status(AutomationPersistencePort.LoadState.READY,p.status.recovery(),8,8,"Durable");
        consume(c);assertEquals(7L,field(c,"listIntentPersistenceGeneration"));
    }

    @Test void consumingAResolvedIntentDoesNotDiscardPurchasedStock() throws Exception {
        var p=new FakePersistence();var c=new AutomationSessionController(new AutomatedExecutionDriver());
        c.setPersistence(p);c.tick(null,null);
        var position=new dev.doughbay.core.model.Position(42,"REAL","minecraft:diamond",dev.doughbay.core.model.StackBucket.of(64),64,10000,12000,1000,0,0,0,Double.NaN,dev.doughbay.core.model.PositionStatus.PURCHASED);
        setField(c,"trackedPosition",position);setField(c,"recoveredOpenPositions",java.util.List.of(position));
        setField(c,"buyIntentPersistenceGeneration",7L);
        p.status=new AutomationPersistencePort.Status(AutomationPersistencePort.LoadState.READY,p.status.recovery(),8,8,"Release durable");
        consume(c);assertEquals(0L,field(c,"buyIntentPersistenceGeneration"));
        assertEquals(position,field(c,"trackedPosition"));assertTrue(c.snapshot().unresolvedExposure());
        assertEquals(4100L,field(c,"committedSpend"));assertEquals(1,field(c,"tradesStarted"));
    }

    private static void consume(AutomationSessionController c) throws Exception {
        var method=AutomationSessionController.class.getDeclaredMethod("processOutOfBandPersistence");method.setAccessible(true);method.invoke(c);
    }

    @Test void recoveredListingRetainsResumeOwnershipWhenTheNextServerRestartPausesIt() throws Exception {
        var c = recoveredWithPolicy();
        assertFalse(c.armed(), "Disk recovery itself must not arm a paused session");
        transition(c, AutomationSessionController.State.PREPARING_LIST);
        transition(c, AutomationSessionController.State.SCANNING);
        c.observeServerRestart("The server is restarting");
        assertTrue(c.armed(), "A recovered session that actually traded must retain its resume ownership");
        assertTrue(c.restartRecoveryMayResume());
        assertEquals(4100L, field(c, "committedSpend"));
        assertEquals(1, field(c, "tradesStarted"));
    }

    @Test void recoveredListingAdoptionRetainsResumeOwnershipAcrossAStaffPause() throws Exception {
        var c = recoveredWithPolicy();
        transition(c, AutomationSessionController.State.SCANNING);
        c.pause("Staff Watch alert: test");
        assertTrue(c.armed());
        assertTrue(c.restartRecoveryMayResume());
        assertFalse((boolean) field(c, "pausedByPlayer"));
    }

    @Test void pausedRecoveryAndExplicitStopNeverAcquireAutomaticResumeOwnership() throws Exception {
        var c = recoveredWithPolicy();
        transition(c, AutomationSessionController.State.PAUSED);
        assertFalse(c.armed());
        assertFalse(c.restartRecoveryMayResume());
        transition(c, AutomationSessionController.State.SCANNING);
        c.emergencyStop();
        assertFalse(c.armed());
        assertFalse(c.restartRecoveryMayResume());
        // Even an accidental later active-state transition cannot override the player's stop.
        transition(c, AutomationSessionController.State.PREPARING_LIST);
        assertFalse(c.armed());
        assertTrue((boolean) field(c, "pausedByPlayer"));
    }

    @Test void activeStateWithoutOpenSessionOrPolicyDoesNotCreateResumeAuthorization() throws Exception {
        var c = recoveredWithPolicy();
        setField(c, "policy", null);
        transition(c, AutomationSessionController.State.PREPARING_LIST);
        assertFalse(c.armed());
        setField(c, "policy", policy());
        setField(c, "sessionOpen", false);
        transition(c, AutomationSessionController.State.SCANNING);
        assertFalse(c.armed());
    }

    @Test void restoringRunningOwnershipDoesNotResolveUncertainStockOrResetCaps() throws Exception {
        var c = recoveredWithPolicy();
        var exposure = new dev.doughbay.storage.AutomationUncertainExposure(true, "LISTING", "position:42",
                "minecraft:diamond", "minecraft:diamond", 64, 4100, 6000, "", 1000);
        setField(c, "uncertainExposure", exposure);
        transition(c, AutomationSessionController.State.LISTING);
        assertTrue(c.armed());
        c.pause("Staff Watch alert: test");
        assertFalse(c.resumeAfterEvasion().ok());
        assertEquals(exposure, field(c, "uncertainExposure"));
        assertEquals(4100L, field(c, "committedSpend"));
        assertEquals(1, field(c, "tradesStarted"));
    }

    private static AutomationSessionController recoveredWithPolicy() throws Exception {
        var c = new AutomationSessionController(new AutomatedExecutionDriver());
        c.setPersistence(new FakePersistence());
        c.tick(null, null);
        setField(c, "policy", policy());
        return c;
    }

    private static void transition(AutomationSessionController c, AutomationSessionController.State state) throws Exception {
        var method = AutomationSessionController.class.getDeclaredMethod("transition", AutomationSessionController.State.class, String.class);
        method.setAccessible(true);
        method.invoke(c, state, "Test completed recovery transition");
    }
    private static String startBlocker(AutomationSessionController c) throws Exception {
        var method=AutomationSessionController.class.getDeclaredMethod("persistenceStartBlocker");method.setAccessible(true);return (String)method.invoke(c);
    }

    private static final class FakePersistence implements AutomationPersistencePort {
        private final AtomicInteger submissions = new AtomicInteger();
        private final java.util.List<AutomationSessionCheckpoint> checkpoints=new java.util.ArrayList<>();
        private volatile boolean rejectSubmissions;
        private volatile Status status = new Status(
                LoadState.READY,
                new AutomationSessionRecovery(true, "CONTINUOUS", "PAUSED",
                        1, 4_100, 1_000, 250, 750, 2_000,
                        "Recovered open session", "",
                        null, java.util.List.of()),
                0, 0, "Recovery ready");

        @Override
        public void start() {
        }

        @Override
        public long submit(AutomationSessionCheckpoint checkpoint) {
            long generation = submissions.incrementAndGet();
            if (rejectSubmissions) return 0;
            checkpoints.add(checkpoint);
            status = new Status(LoadState.READY, status.recovery(),
                    generation, status.lastDurableGeneration(),
                    "Checkpoint queued");
            return generation;
        }

        @Override
        public long submitManualResolution(
                AutomationManualResolution resolution,
                AutomationSessionCheckpoint checkpoint) {
            throw new AssertionError("manual resolution was not expected");
        }

        @Override
        public Status status() {
            return status;
        }

        void acknowledgeAll() {
            status = new Status(LoadState.READY, status.recovery(),
                    status.lastAcceptedGeneration(), status.lastAcceptedGeneration(),
                    "Checkpoint durable but not consumed by controller");
        }

        @Override
        public void close() {
        }
    }
}
