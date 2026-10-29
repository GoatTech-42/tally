package dev.doughbay.fabric.staffwatch.gui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HudRendererStackLifecycleTest {
    // render-owned staleness lifecycle: reset on hidden, measured only on actual draw
    @Test void stalenessFlagFollowsRenderLifecycle() {
        var r = new HudRenderer();
        assertTrue(r.stackGeometryStaleForTest(true), "unmeasured while showing is stale");
        r.markStackMeasuredForTest();
        assertFalse(r.stackGeometryStaleForTest(true), "measured while showing is not stale");
        r.noteStackHidden();
        assertTrue(r.stackGeometryStaleForTest(true), "re-shown after hidden is stale again");
        assertFalse(r.stackGeometryStaleForTest(false), "hidden is never stale");
    }
}
