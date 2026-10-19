package dev.doughbay.fabric.automation;

import dev.doughbay.fabric.AutomatedExecutionDriver;
import dev.doughbay.storage.*;
import java.lang.reflect.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class InterruptedWatchRecoveryTest {
    @Test void protectivePauseReleasesOnlyTheWatchThatNeverClickedAndWaitsForDurability() throws Exception {
        var p = new Persistence();
        var c = watching(p, false);
        c.pause("Evasion: test protective hold");
        assertFalse(((AutomationUncertainExposure)get(c,"uncertainExposure")).active(),
                "read-only watch proof must be captured before emergencyStop destroys it");
        assertFalse((boolean)get(c,"recoveredSession"));
        assertTrue(c.armed());
        assertFalse((boolean)get(c,"pausedByPlayer"));
        assertNotNull(p.last);
        assertFalse(p.last.uncertainExposure().active());
        assertTrue((long)get(c,"buyIntentPersistenceGeneration") > 0,
                "the release receipt must survive until durable evidence is applied");
        assertEquals(12345L,get(c,"committedSpend"));
        assertEquals(7,get(c,"tradesStarted"));
        assertEquals(AutomatedExecutionDriver.OperationIntent.NONE,
                ((AutomatedExecutionDriver)get(c,"driver")).operationIntent());
        internalResume(c);
        assertTrue(c.bugReportContext(System.currentTimeMillis()).contains("last_resume_gate=PERSISTENCE_WAIT"));
        p.acknowledge();
        invoke(c,"processOutOfBandPersistence");
        assertEquals(0L,get(c,"buyIntentPersistenceGeneration"));
        assertEquals(AutomationSessionController.State.PAUSED,get(c,"state"),"a receipt is not permission to trade");
    }

    @Test void selectedOrPossiblyPurchasedRowStillRequiresRecovery() throws Exception {
        var c = watching(new Persistence(), true);
        var exposure=get(c,"uncertainExposure");
        c.pause("Evasion: test protective hold");
        assertSame(exposure,get(c,"uncertainExposure"));
        assertTrue((boolean)get(c,"recoveredSession"));
        assertEquals(12345L,get(c,"committedSpend"));
    }

    @Test void anExplicitStopStillDisarmsAndCannotBeResumedByTheTimer() throws Exception {
        var c=watching(new Persistence(),false);
        c.emergencyStop();
        internalResume(c);
        assertFalse(c.armed());assertTrue((boolean)get(c,"pausedByPlayer"));
        assertTrue(c.bugReportContext(System.currentTimeMillis()).contains("last_resume_gate=NOT_ARMED"));
        assertEquals(12345L,get(c,"committedSpend"));
        assertEquals(7,get(c,"tradesStarted"));
    }

    @Test void rejectedReleaseKeepsOriginalExposureAndReceipt() throws Exception {
        var p=new Persistence();p.reject=true;
        var c=watching(p,false);
        var exposure=get(c,"uncertainExposure");
        c.pause("Evasion: test protective hold");
        assertSame(exposure,get(c,"uncertainExposure"));
        assertEquals(1L,get(c,"buyIntentPersistenceGeneration"));
        assertTrue((boolean)get(c,"recoveredSession"));
    }

    @Test void automaticResumeMustWaitForAControllerReceiptEvenWhenWriterIsCaughtUp() throws Exception {
        var c=watching(new Persistence(),false);
        set(c,"state",AutomationSessionController.State.PAUSED);
        set(c,"uncertainExposure",AutomationUncertainExposure.none());
        internalResume(c);
        assertTrue(c.bugReportContext(System.currentTimeMillis()).contains("last_resume_gate=PERSISTENCE_WAIT"));
        assertTrue(c.armed());assertEquals(12345L,get(c,"committedSpend"));
    }

    @SuppressWarnings({"rawtypes","unchecked"})
    private static AutomationSessionController watching(Persistence p, boolean selected) throws Exception {
        var d=new AutomatedExecutionDriver();
        Field op=field(d,"operation");op.set(d,Enum.valueOf((Class)op.getType(),"BUY"));
        set(d,"watching",true);set(d,"targetListingKey","watch:all");
        set(d,"targetItemId",selected?"minecraft:stone":"");
        var c=new AutomationSessionController(d);c.setPersistence(p);
        set(c,"recoveryApplied",true);set(c,"sessionOpen",true);set(c,"armed",true);
        set(c,"state",AutomationSessionController.State.BUYING);
        set(c,"watchBuy",true);set(c,"buyCommandStarted",true);
        set(c,"buyIntentPersistenceGeneration",1L);
        set(c,"uncertainExposure",new AutomationUncertainExposure(true,"BUYING","ceiling:test",
                "minecraft:stone","minecraft:stone",64,1000,1500,"",1000));
        set(c,"committedSpend",12345L);set(c,"tradesStarted",7);
        set(c,"policy",dev.doughbay.core.automation.ContinuousAutomationPolicy.fromConfigValues(
                20,100000,50000,5000,12,80,10,8,120,1));
        return c;
    }
    private static void internalResume(AutomationSessionController c) throws Exception {
        Method m=c.getClass().getDeclaredMethod("autoResumeInternalPause",net.minecraft.client.Minecraft.class,long.class);
        m.setAccessible(true);m.invoke(c,null,System.currentTimeMillis()+600000);
    }
    private static void invoke(Object c,String name)throws Exception {Method m=c.getClass().getDeclaredMethod(name);m.setAccessible(true);m.invoke(c);}
    private static Field field(Object o,String n)throws Exception {Field f=o.getClass().getDeclaredField(n);f.setAccessible(true);return f;}
    private static void set(Object o,String n,Object v)throws Exception {field(o,n).set(o,v);}
    private static Object get(Object o,String n)throws Exception {return field(o,n).get(o);}
    private static class Persistence implements AutomationPersistencePort {
        boolean reject; AutomationSessionCheckpoint last;
        Status status=new Status(LoadState.READY,AutomationSessionRecovery.empty(),1,1,"ready");
        public void start(){} public void close(){} public Status status(){return status;}
        public long submit(AutomationSessionCheckpoint c){
            if(reject)return 0;last=c;long g=status.lastAcceptedGeneration()+1;
            status=new Status(LoadState.READY,status.recovery(),g,status.lastDurableGeneration(),"queued");return g;
        }
        public long submitManualResolution(AutomationManualResolution r,AutomationSessionCheckpoint c){throw new AssertionError();}
        void acknowledge(){
            var r=new AutomationSessionRecovery(true,"CONTINUOUS","PAUSED",7,12345,1000,2000,
                    "release durable","",AutomationUncertainExposure.none(),List.of());
            status=new Status(LoadState.READY,r,status.lastAcceptedGeneration(),status.lastAcceptedGeneration(),"durable");
        }
    }
}
