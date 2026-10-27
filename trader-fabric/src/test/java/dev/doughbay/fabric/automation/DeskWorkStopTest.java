package dev.doughbay.fabric.automation;

import dev.doughbay.fabric.AutomatedExecutionDriver;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayDeque;

import static org.junit.jupiter.api.Assertions.*;

class DeskWorkStopTest {
    // A bid-desk run is planned into deskQueue/deskStep and executed later from
    // SCANNING. Nothing cleared that plan on Stop, so bids and collects queued
    // under the old session could go out after it ended, inside the next one.
    @Test void explicitStopDropsPlannedBidDeskWork() throws Exception {
        var c = deskLoadedController();
        c.stop();
        assertDeskDropped(c);
        assertTrue(c.bugReportContext(System.currentTimeMillis()).contains("queued_action=NONE"),
                "the report must not keep advertising dropped work as queued");
    }

    @Test void emergencyStopDropsPlannedBidDeskWork() throws Exception {
        var c = deskLoadedController();
        c.emergencyStop();
        assertDeskDropped(c);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static AutomationSessionController deskLoadedController() throws Exception {
        var c = new AutomationSessionController(new AutomatedExecutionDriver());
        Class actionClass = Class.forName("dev.doughbay.fabric.automation.AutomationSessionController$DeskAction");
        Class stepClass = Class.forName("dev.doughbay.fabric.automation.AutomationSessionController$DeskStep");
        var ctor = stepClass.getDeclaredConstructor(actionClass, String.class, int.class, long.class, String.class);
        ctor.setAccessible(true);
        Object place = ctor.newInstance(Enum.valueOf(actionClass, "PLACE"), "minecraft:stone", 16, 500L, "planned bid");
        Object read = ctor.newInstance(Enum.valueOf(actionClass, "READ"), "", 0, 0L, "reading your orders");
        set(c, "state", AutomationSessionController.State.SCANNING);
        var queue = (ArrayDeque) get(c, "deskQueue");
        queue.add(read);
        queue.add(place);
        set(c, "deskStep", read);
        set(c, "deskReadDue", true);
        return c;
    }

    private static void assertDeskDropped(AutomationSessionController c) throws Exception {
        assertTrue(((ArrayDeque<?>) get(c, "deskQueue")).isEmpty(),
                "a stopped session's planned order-house steps must not outlive it");
        assertNull(get(c, "deskStep"), "a stopped session has no desk step in flight");
        assertFalse((boolean) get(c, "deskReadDue"), "a stopped session owes no order read");
    }

    private static Field field(Object o, String n) throws Exception {
        Field f = o.getClass().getDeclaredField(n);
        f.setAccessible(true);
        return f;
    }
    private static void set(Object o, String n, Object v) throws Exception { field(o, n).set(o, v); }
    private static Object get(Object o, String n) throws Exception { return field(o, n).get(o); }
}
