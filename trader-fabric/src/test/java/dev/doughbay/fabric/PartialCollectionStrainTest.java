package dev.doughbay.fabric;

import java.lang.reflect.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PartialCollectionStrainTest {
    @AfterEach void reset() { ServerStrain.reset(); }

    @Test void repeatedPhysicalPartialStockWaitsNeverSlowUnrelatedTrading() throws Exception {
        ServerStrain.reset();
        for (int i=0;i<4;i++) {
            var driver=collecting();
            invoke(driver,"finishPartStackWait",null);
            assertEquals(AutomatedExecutionDriver.TerminalOutcome.ABORTED,driver.lastTerminalEvent().outcome());
            assertEquals(AutomatedExecutionDriver.OperationIntent.COLLECT_ORDER,driver.lastTerminalEvent().intent());
            assertFalse(driver.lastTerminalEvent().confirmationSubmitted());
            assertEquals(0,driver.lastTerminalEvent().itemCount());
            assertTrue(driver.lastTerminalEvent().detail().contains(AutomatedExecutionDriver.PART_STACK_WAITING));
        }
        assertFalse(ServerStrain.holding());
        assertEquals(1.0,ServerStrain.pacingMultiplier());
        assertTrue(ActionTrace.INSTANCE.report(System.currentTimeMillis()).contains("event=ABORTED code=COLLECT_ORDER reason=STACK_BATCH"));
    }

    @Test void guardedWaitDoesNotClearGenuineTimeoutHistoryOrAnExistingHold() throws Exception {
        ServerStrain.reset();
        ServerStrain.noteFailure("Native menu timed out");
        ServerStrain.noteFailure("Native menu timed out");
        invoke(collecting(),"finishPartStackWait",null);
        assertEquals(2.0,ServerStrain.pacingMultiplier());
        ServerStrain.noteFailure("Native menu timed out");
        assertTrue(ServerStrain.holding());
        long held=ServerStrain.commandNotBeforeMillis();
        invoke(collecting(),"finishPartStackWait",null);
        assertTrue(ServerStrain.holding());
        assertEquals(held,ServerStrain.commandNotBeforeMillis());
    }

    @Test void realCollectionFaultsAndAlreadyClickedActionsStillCountAsFailures() throws Exception {
        ServerStrain.reset();
        for(int i=0;i<3;i++) invoke(collecting(),"abort","Edit Order shows no collect control");
        assertTrue(ServerStrain.holding());
        ServerStrain.reset();
        for(int i=0;i<3;i++) {
            var driver=collecting();field("collectClicks").setInt(driver,1);
            invoke(driver,"finishPartStackWait",null);
        }
        assertTrue(ServerStrain.holding());
    }

    @SuppressWarnings({"rawtypes","unchecked"})
    private static AutomatedExecutionDriver collecting() throws Exception {
        var driver=new AutomatedExecutionDriver();Field operation=field("operation");
        operation.set(driver,Enum.valueOf((Class)operation.getType(),"COLLECT_ORDER"));
        field("targetItemId").set(driver,"minecraft:obsidian");
        return driver;
    }
    private static Field field(String name) throws Exception {
        Field f=AutomatedExecutionDriver.class.getDeclaredField(name);f.setAccessible(true);return f;
    }
    private static void invoke(AutomatedExecutionDriver driver,String method,String reason) throws Exception {
        Method m=reason==null?AutomatedExecutionDriver.class.getDeclaredMethod(method,net.minecraft.client.Minecraft.class)
                :AutomatedExecutionDriver.class.getDeclaredMethod(method,net.minecraft.client.Minecraft.class,String.class);
        m.setAccessible(true);
        if(reason==null)m.invoke(driver,new Object[]{null});else m.invoke(driver,null,reason);
    }
}
