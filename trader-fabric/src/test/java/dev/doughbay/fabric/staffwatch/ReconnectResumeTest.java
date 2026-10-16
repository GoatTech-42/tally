package dev.doughbay.fabric.staffwatch;

import dev.doughbay.core.execution.ExecutionResult;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class ReconnectResumeTest {
    @Test void noRunningSessionMeansNothingIsAutomaticallyResumed() {
        var recovery = new ReconnectResume();
        recovery.arm(false, 1000);
        assertNull(recovery.tick(100_000, true, true, () -> fail("No ownership")));
        assertFalse(recovery.pending());
    }

    @Test void connectionCooldownAndWorldSettlingMustFinishBeforeResume() {
        var recovery = new ReconnectResume();
        recovery.arm(true, 1000);
        var calls = new AtomicInteger();
        java.util.function.Supplier<ExecutionResult> resume = () -> {
            calls.incrementAndGet(); return ExecutionResult.awaitingPlayer("Resumed");
        };
        assertNull(recovery.tick(300_000, false, true, resume));
        assertNull(recovery.tick(310_000, true, true, resume));
        assertNull(recovery.tick(324_999, true, true, resume));
        assertEquals(0, calls.get());
        assertTrue(recovery.tick(325_000, true, true, resume).ok());
        assertFalse(recovery.pending());
        assertNull(recovery.tick(400_000, true, true, resume));
        assertEquals(1, calls.get());
    }

    @Test void moreThanFourTemporaryRefusalsKeepOwnershipAndRespectRetrySpacing() {
        var recovery = new ReconnectResume();
        recovery.arm(true, 1000);
        recovery.tick(1000, true, true, () -> fail("Settling"));
        var calls = new AtomicInteger();
        for (int attempt = 0; attempt < 8; attempt++) {
            long now = 16_000 + attempt * 30_000L;
            assertFalse(recovery.tick(now, true, true, () -> {
                calls.incrementAndGet(); return ExecutionResult.refused("Persistence pending");
            }).ok());
            assertTrue(recovery.pending());
            assertNull(recovery.tick(now + 1, true, true, () -> fail("Too soon")));
        }
        assertEquals(8, calls.get());
    }

    @Test void manualStopAndExplicitCancellationRemoveOwnership() {
        var recovery = new ReconnectResume();
        recovery.arm(true, 1000);
        recovery.tick(1000, true, true, () -> fail("Settling"));
        assertNull(recovery.tick(100_000, true, false, () -> fail("Operator stopped")));
        assertFalse(recovery.pending());
        recovery.arm(true, 100_000);
        recovery.cancel();
        assertNull(recovery.tick(200_000, true, true, () -> fail("Cancelled")));
    }

    @Test void anotherDisconnectResetsWorldSettlingAndDuplicateAlertsDoNotResetOwnership() {
        var recovery = new ReconnectResume();
        recovery.arm(true, 1000);
        recovery.tick(1000, true, true, () -> fail("Settling"));
        recovery.arm(true, 2000);
        assertEquals(1000, recovery.startedAt());
        recovery.tick(15_000, false, true, () -> fail("Disconnected"));
        recovery.tick(16_000, true, true, () -> fail("New world"));
        assertNull(recovery.tick(30_000, true, true, () -> fail("Not settled")));
        assertTrue(recovery.tick(31_000, true, true, () -> ExecutionResult.awaitingPlayer("Resumed")).ok());
    }
}
