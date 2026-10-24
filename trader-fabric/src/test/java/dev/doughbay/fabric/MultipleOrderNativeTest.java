package dev.doughbay.fabric;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MultipleOrderNativeTest {
    @Test void oneOfTwoIdenticalOrdersCanBeCancelledWithoutWaitingForBothToDisappear() {
        assertTrue(AutomatedExecutionDriver.cancellationObserved(2,1,false));
        assertTrue(AutomatedExecutionDriver.cancellationObserved(2,2,true));
        assertFalse(AutomatedExecutionDriver.cancellationObserved(2,2,false));
        assertFalse(AutomatedExecutionDriver.cancellationObserved(0,0,true));
        assertFalse(AutomatedExecutionDriver.cancellationObserved(2,0,false));
    }

    @Test void moreOrdersRequireACompleteBookRatherThanAssumingFortyFiveRowsMeansNinetySlotsRead() {
        assertTrue(AutomatedExecutionDriver.completeOwnOrderPage(2,false,false,"Your Orders"));
        assertFalse(AutomatedExecutionDriver.completeOwnOrderPage(45,false,false,"Your Orders"));
        assertFalse(AutomatedExecutionDriver.completeOwnOrderPage(2,true,false,"Your Orders"));
        assertFalse(AutomatedExecutionDriver.completeOwnOrderPage(2,false,true,"Your Orders"));
        assertFalse(AutomatedExecutionDriver.completeOwnOrderPage(2,false,false,"Your Orders Page 2"));
    }
}
