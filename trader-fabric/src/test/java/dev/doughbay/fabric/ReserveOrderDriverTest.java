package dev.doughbay.fabric;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ReserveOrderDriverTest {
    @Test void oldIdenticalOrderCannotConfirmASecondPlacement() {
        assertFalse(AutomatedExecutionDriver.newPlacementObserved(1,1));
        assertFalse(AutomatedExecutionDriver.newPlacementObserved(-1,1));
        assertFalse(AutomatedExecutionDriver.newPlacementObserved(2,1));
        assertTrue(AutomatedExecutionDriver.newPlacementObserved(1,2));
        assertTrue(AutomatedExecutionDriver.newPlacementObserved(0,1));
    }
    @Test void reserveSelectsFinishedRowsWithoutChangingOrdinaryPartialCollection() {
        var partial=new AutomatedExecutionDriver.OwnOrderRow("minecraft:stone","minecraft:stone",768,100,64,1000);
        var full=new AutomatedExecutionDriver.OwnOrderRow("minecraft:stone","minecraft:stone",768,100,768,1000);
        var cancelled=new AutomatedExecutionDriver.OwnOrderRow("minecraft:stone","minecraft:stone",768,100,64,0,true);
        assertTrue(AutomatedExecutionDriver.collectionReady(partial,false));
        assertFalse(AutomatedExecutionDriver.collectionReady(partial,true));
        assertTrue(AutomatedExecutionDriver.collectionReady(full,true));
        assertTrue(AutomatedExecutionDriver.collectionReady(cancelled,true));
    }
}
