package dev.doughbay.fabric.hosted;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class IsolatedOrderTestGateTest {
    @Test void idleStoppedAccountCanReadWithoutTrading() {
        assertTrue(IsolatedOrderTestGate.canStart("STOPPED", "NONE", false, false, false, true));
    }
    @Test void neverEntersFromTradingOrRecovery() {
        for (String state : new String[]{"SCANNING","BUYING","RECOVERING","READING_ORDERS"})
            assertFalse(IsolatedOrderTestGate.canStart(state,"NONE",false,false,false,true));
    }
    @Test void refusesAnyInFlightOperation() {
        for (String intent : new String[]{"BUY","LIST","ORDERS","OWN_ORDERS"})
            assertFalse(IsolatedOrderTestGate.canStart("STOPPED",intent,false,false,false,true));
    }
    @Test void unsafeInventoryStaffAndDuplicateRequestsBlockAdmission() {
        assertFalse(IsolatedOrderTestGate.canStart("STOPPED","NONE",true,false,false,true));
        assertFalse(IsolatedOrderTestGate.canStart("STOPPED","NONE",false,true,false,true));
        assertFalse(IsolatedOrderTestGate.canStart("STOPPED","NONE",false,false,true,true));
    }
    @Test void manualPauseCanReadWithoutClearingRetainedPositions() {
        assertTrue(IsolatedOrderTestGate.canStart("PAUSED","NONE",false,false,false,true));
        assertFalse(IsolatedOrderTestGate.canStart("PAUSED","NONE",false,false,false,false));
        assertFalse(IsolatedOrderTestGate.canStart("STOPPED","NONE",false,false,false,false));
    }
}
