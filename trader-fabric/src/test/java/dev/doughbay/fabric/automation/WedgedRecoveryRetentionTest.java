package dev.doughbay.fabric.automation;

import dev.doughbay.core.automation.ContinuousAutomationPolicy;
import dev.doughbay.core.model.*;
import dev.doughbay.fabric.AutomatedExecutionDriver;
import dev.doughbay.fabric.Tuning;
import org.junit.jupiter.api.Test;
import java.lang.reflect.*;
import static org.junit.jupiter.api.Assertions.*;

class WedgedRecoveryRetentionTest {
    private static void set(Object target, String name, Object value) throws Exception {
        Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);f.set(target,value);
    }
    private static Object get(Object target,String name) throws Exception {
        Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);
    }
    @Test void aRecoveryDeadlineCannotAbandonStockOrArmTrading() throws Exception {
        double previous=Tuning.get("recovery.auto_reset_min");
        try {
            Tuning.set("recovery.auto_reset_min",1);
            var c=new AutomationSessionController(new AutomatedExecutionDriver());
            var writes=new java.util.concurrent.atomic.AtomicInteger();
            c.setPersistence(new dev.doughbay.storage.AutomationPersistencePort() {
                public void start() { }
                public long submit(dev.doughbay.storage.AutomationSessionCheckpoint checkpoint) { return writes.incrementAndGet(); }
                public long submitManualResolution(dev.doughbay.storage.AutomationManualResolution resolution,dev.doughbay.storage.AutomationSessionCheckpoint checkpoint) { return writes.incrementAndGet(); }
                public Status status() { return new Status(LoadState.READY,dev.doughbay.storage.AutomationSessionRecovery.empty(),0,0,"ready"); }
                public void close() { }
            });
            var stock=new Position(62,"REAL","minecraft:stone",StackBucket.of(64),64,6400,10000,
                    1000,0,0,0,Double.NaN,PositionStatus.PURCHASED);
            set(c,"policy",ContinuousAutomationPolicy.fromConfigValues(2,100000,50000,5000,12,80,10,8,120,1));
            set(c,"trackedPosition",stock);set(c,"recoveredSession",true);
            set(c,"state",AutomationSessionController.State.PAUSED);set(c,"armed",false);
            set(c,"committedSpend",6400L);set(c,"buyIntentPersistenceGeneration",7L);
            Method timer;
            try {timer=c.getClass().getDeclaredMethod("maybeReportWedgedRecovery",net.minecraft.client.Minecraft.class,long.class);}
            catch(NoSuchMethodException e){timer=c.getClass().getDeclaredMethod("maybeForceResetWedged",net.minecraft.client.Minecraft.class,long.class);}
            timer.setAccessible(true);
            timer.invoke(c,null,1000L);timer.invoke(c,null,62000L);timer.invoke(c,null,122000L);
            assertSame(stock,get(c,"trackedPosition"),"a timer is not evidence that held stock was resolved");
            assertEquals(true,get(c,"recoveredSession"));assertEquals(false,get(c,"armed"));
            assertEquals(AutomationSessionController.State.PAUSED,get(c,"state"));
            assertEquals(6400L,get(c,"committedSpend"));assertEquals(7L,get(c,"buyIntentPersistenceGeneration"));
            assertEquals(0,writes.get(),"recovery timer must not write cancellations");
        } finally {Tuning.set("recovery.auto_reset_min",previous);}
    }
}
