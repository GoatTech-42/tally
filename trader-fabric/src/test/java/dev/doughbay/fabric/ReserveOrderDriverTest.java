package dev.doughbay.fabric;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ReserveOrderDriverTest {
    @Test void shelfCollectionStopsAtRequestedWholeStackCountAndLegacyCollectionStaysUnbounded() {
        assertTrue(AutomatedExecutionDriver.collectionStackFits(0,64,64,128));
        assertTrue(AutomatedExecutionDriver.collectionStackFits(64,64,64,128));
        assertFalse(AutomatedExecutionDriver.collectionStackFits(128,64,64,128));
        assertFalse(AutomatedExecutionDriver.collectionStackFits(0,32,64,128));
        assertFalse(AutomatedExecutionDriver.collectionStackFits(0,64,64,32));
        assertTrue(AutomatedExecutionDriver.collectionStackFits(0,16,16,16));
        assertTrue(AutomatedExecutionDriver.collectionStackFits(1000,3,64,0));
        assertFalse(AutomatedExecutionDriver.collectionStackFits(-1,64,64,64));
    }
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
