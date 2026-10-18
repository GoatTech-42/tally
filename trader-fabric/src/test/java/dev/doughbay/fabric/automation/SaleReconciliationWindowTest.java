package dev.doughbay.fabric.automation;

import dev.doughbay.core.automation.ContinuousAutomationPolicy;
import dev.doughbay.core.model.*;
import dev.doughbay.core.text.DonutSaleMessageParser.SaleNotice;
import dev.doughbay.fabric.AutomatedExecutionDriver;
import dev.doughbay.fabric.MarketWatcher;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.OptionalInt;
import static org.junit.jupiter.api.Assertions.*;

class SaleReconciliationWindowTest {
    @Test void twoSlowFreshScansDoNotExpireAtTheSingleSnapshotDeadline() throws Exception {
        var c = controller();
        assertFalse(expired(c, 1000, 81000));
        assertFalse(expired(c, 1000, 161000));
        assertFalse(expired(c, 1000, 241000), "allow one queued read followed by two fresh scans");
        assertTrue(expired(c, 1000, 271001), "a stalled feed still has a finite deadline");
        assertTrue(expired(c, 0, 1000));
        assertTrue(expired(c, 2000, 1000));
    }

    @Test void waitingAfterNinetySecondsRetainsTheListedPositionWithoutAssumingASale() throws Exception {
        var c = controller();
        var held = field(c, "trackedPosition");
        reconcile(c, 161000);
        assertEquals(AutomationSessionController.State.MONITORING, field(c, "state"));
        assertSame(held, field(c, "trackedPosition"));
        assertEquals(6400L, field(c, "committedSpend"));
        reconcile(c, 271001);
        assertEquals(AutomationSessionController.State.PAUSED, field(c, "state"));
        assertSame(held, field(c, "trackedPosition"));
        assertEquals(6400L, field(c, "committedSpend"));
    }

    @Test void longerWaitDoesNotMakeAnOldExactScanFresh() throws Exception {
        var c = controller();
        var coverage = new MarketWatcher.ExactListingCoverage("minecraft:stone", List.of(),
                2000, 3000, true, 0, 0, 1, "complete");
        var method = AutomationSessionController.class.getDeclaredMethod("exactCoverageBlocker",
                MarketWatcher.ExactListingCoverage.class, AutomationSessionController.CoverageRequest.class, long.class);
        method.setAccessible(true);
        assertEquals("coverage is stale", method.invoke(c, coverage,
                new AutomationSessionController.CoverageRequest("minecraft:stone", 1000), 161000L));
    }

    private static AutomationSessionController controller() throws Exception {
        var c = new AutomationSessionController(new AutomatedExecutionDriver());
        set(c, "policy", ContinuousAutomationPolicy.fromConfigValues(20,100000,50000,100,12,80,10,8,120,1));
        set(c, "state", AutomationSessionController.State.MONITORING);
        set(c, "trackedPosition", new Position(42,"REAL","minecraft:stone",StackBucket.of(64),64,
                6400,8000,100,500,0,0,Double.NaN,PositionStatus.LISTED));
        set(c, "committedSpend", 6400L);
        set(c, "bufferedSaleNotice", new SaleNotice("buyer", "Stone", OptionalInt.of(64),8000,8000));
        set(c, "saleNoticeObservedAtMillis", 1000L);
        return c;
    }
    private static boolean expired(AutomationSessionController c,long start,long now) throws Exception {
        var m=AutomationSessionController.class.getDeclaredMethod("reconciliationTimedOut",long.class,long.class);
        m.setAccessible(true);return (boolean)m.invoke(c,start,now);
    }
    private static void reconcile(AutomationSessionController c,long now) throws Exception {
        var m=AutomationSessionController.class.getDeclaredMethod("reconcileSaleDisappearance",
                net.minecraft.client.Minecraft.class,MarketWatcher.Snapshot.class,long.class);
        m.setAccessible(true);m.invoke(c,null,null,now);
    }
    private static Object field(Object c,String name) throws Exception {
        var f=c.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(c);
    }
    private static void set(Object c,String name,Object value) throws Exception {
        var f=c.getClass().getDeclaredField(name);f.setAccessible(true);f.set(c,value);
    }
}
