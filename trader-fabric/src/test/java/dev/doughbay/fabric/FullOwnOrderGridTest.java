package dev.doughbay.fabric;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FullOwnOrderGridTest {
    @Test void aFullPageOfRepeatedItemLabelsIsStillFull() {
        assertTrue(AutomatedExecutionDriver.fullOwnOrderGrid(Collections.nCopies(45,"Diamond Block")));
        var mixed = new ArrayList<>(Collections.nCopies(30,"Diamond Block"));
        mixed.addAll(Collections.nCopies(15,"Experience Bottle"));
        assertTrue(AutomatedExecutionDriver.fullOwnOrderGrid(mixed));
    }

    @Test void emptyHoverNamesStillRepresentOccupiedCellsButMissingCellsAreNotInvented() {
        assertTrue(AutomatedExecutionDriver.fullOwnOrderGrid(Collections.nCopies(45,"")));
        assertFalse(AutomatedExecutionDriver.fullOwnOrderGrid(Collections.nCopies(44,"Diamond Block")));
        assertFalse(AutomatedExecutionDriver.fullOwnOrderGrid(List.of()));
    }
}
