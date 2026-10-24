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
    @Test void duplicateRowFallbackIsBoundedAndCannotRunAfterAnyClickOrInventoryChange() {
        assertEquals(1,AutomatedExecutionDriver.nextCollectOrdinal(0,3,Set.of(0),0,0));
        assertEquals(2,AutomatedExecutionDriver.nextCollectOrdinal(1,3,Set.of(0,1),0,0));
        assertEquals(-1,AutomatedExecutionDriver.nextCollectOrdinal(2,3,Set.of(0,1,2),0,0));
        // Credits suggest row2; if server ordering changed, a native empty page may safely try row0.
        assertEquals(0,AutomatedExecutionDriver.nextCollectOrdinal(2,3,Set.of(2),0,0));
        assertEquals(-1,AutomatedExecutionDriver.nextCollectOrdinal(0,3,Set.of(0),1,0));
        assertEquals(-1,AutomatedExecutionDriver.nextCollectOrdinal(0,3,Set.of(0),0,1));
        assertEquals(-1,AutomatedExecutionDriver.nextCollectOrdinal(0,3,Set.of(0),0,-1));
        assertEquals(-1,AutomatedExecutionDriver.nextCollectOrdinal(0,1,Set.of(0),0,0));
        assertEquals(-1,AutomatedExecutionDriver.nextCollectOrdinal(0,46,Set.of(0),0,0));
    }
}
