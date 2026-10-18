package dev.doughbay.fabric.automation;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RecoveryAuditRetryTest {
    @Test void failedReadRetriesWithoutSpammingAndNewStockIsNotBlocked() {
        var retry = new RecoveryAuditRetry();
        assertTrue(retry.ready(1, 1000));
        retry.attempt(1, 1000);
        assertFalse(retry.ready(1, 15_999));
        assertTrue(retry.ready(1, 16_000));
        assertTrue(retry.ready(2, 1001));
        retry.attempt(2, 1001);
        assertFalse(retry.ready(2, 1002));
        retry.reset();
        assertTrue(retry.ready(2, 1002));
    }
}
