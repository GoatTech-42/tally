package dev.doughbay.fabric.hosted;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class IsolatedOrderRetryTest {
    @Test void reconnectAndQuietPeriodAreRequired() {
        var r=new IsolatedOrderRetry();r.begin(1000);assertTrue(r.interrupted(2000));
        assertFalse(r.ready(20000,false,true,true,true));
        assertFalse(r.ready(21000,true,true,false,true));
        assertFalse(r.ready(22000,true,true,true,true));
        assertFalse(r.ready(36999,true,true,true,true));assertTrue(r.ready(37000,true,true,true,true));
        assertFalse(r.pending());
    }
    @Test void newThreatResetsQuietPeriodAndUnsafeInventoryDoesNotResume() {
        var r=new IsolatedOrderRetry();r.begin(1000);r.interrupted(2000);
        assertFalse(r.ready(20000,true,true,true,true));
        assertFalse(r.ready(30000,true,false,true,true));
        assertFalse(r.ready(31000,true,true,true,true));assertFalse(r.ready(45000,true,true,true,true));
        assertTrue(r.ready(46000,true,true,true,true));
    }
    @Test void manualStopOrTradingResumeCancelsTheTest() {
        var r=new IsolatedOrderRetry();r.begin(1000);r.interrupted(2000);
        assertFalse(r.ready(20000,true,true,true,false));assertFalse(r.pending());
        r.begin(30000);r.interrupted(31000);r.cancel();assertFalse(r.ready(60000,true,true,true,true));
    }
    @Test void repeatedInterruptionsAndElapsedTimeAreBounded() {
        var r=new IsolatedOrderRetry();r.begin(1000);
        assertTrue(r.interrupted(2000));assertTrue(r.interrupted(3000));assertTrue(r.interrupted(4000));assertFalse(r.interrupted(5000));
        r.begin(1000);assertFalse(r.interrupted(1801001));
    }
    @Test void initialOfflineQueueDoesNotConsumeAnInterruptionAttempt() {
        var r=new IsolatedOrderRetry();r.begin(1000);r.waitForGuard(2000);
        assertEquals(0,r.interruptions());assertTrue(r.pending());
        assertFalse(r.ready(3000,false,true,true,true));
        assertFalse(r.ready(20000,true,true,true,true));assertTrue(r.ready(35000,true,true,true,true));
    }
}
