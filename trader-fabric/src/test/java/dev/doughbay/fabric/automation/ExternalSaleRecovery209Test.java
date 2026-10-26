package dev.doughbay.fabric.automation;

import dev.doughbay.core.automation.ContinuousAutomationPolicy;
import dev.doughbay.core.model.*;
import dev.doughbay.fabric.AutomatedExecutionDriver;
import dev.doughbay.storage.*;
import java.lang.reflect.*;
import java.util.*;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.*;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;
import static org.junit.jupiter.api.Assertions.*;

/** Offline investigation of report209's interrupted listing and externally sold item.
 * Starts after the explicit first confirmation; native audit receipts are test inputs.
 * No live connection, command, trade, ledger or automatic recovery override is used. */
class ExternalSaleRecovery209Test {
    @org.junit.jupiter.api.BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion();Bootstrap.bootStrap();
        net.minecraft.core.registries.BuiltInRegistries.DATA_COMPONENT_INITIALIZERS
            .build(net.minecraft.data.registries.VanillaRegistries.createLookup())
            .forEach(net.minecraft.core.component.DataComponentInitializers.PendingComponents::apply);
    }
    @Test void absentItemNeedsThreeFreshAuditsThenDurableConfirmationAndStaysStopped() throws Exception {
        var f=new Fixture();
        f.audit(1,true,0);
        assertFalse((boolean)get(f.c,"manualResolutionEvidenceReady"));
        f.audit(2,true,0);
        assertFalse((boolean)get(f.c,"manualResolutionEvidenceReady"),"Two checks still need the later recheck");
        f.audit(3,true,0);
        assertTrue((boolean)get(f.c,"manualResolutionEvidenceReady"));
        assertTrue(f.c.confirmManualResolution(f.mc,null).ok());
        assertTrue(f.c.snapshot().unresolvedExposure(),"A queued audit is not durable evidence");
        assertEquals(1,f.p.resolutions);
        assertEquals(PositionStatus.CANCELLED,f.p.last.position().status());
        f.p.acknowledge(); invoke(f.c,"processOutOfBandPersistence");
        assertFalse(f.c.snapshot().unresolvedExposure());
        assertFalse(f.c.armed());
        assertEquals(AutomationSessionController.State.PAUSED,f.c.snapshot().state());
        assertEquals(12345L,get(f.c,"committedSpend"));
        assertEquals(7,get(f.c,"tradesStarted"));
    }

    @Test void anIncompleteAuditCannotBecomeAbsenceEvidence() throws Exception {
        var f=new Fixture();f.audit(1,true,0);f.audit(2,false,0);f.audit(3,true,1);
        assertFalse(f.c.confirmManualResolution(f.mc,null).ok());
        assertEquals(0,f.p.resolutions);
        assertTrue(f.c.snapshot().unresolvedExposure());
    }

    @Test void itemReturningAfterCleanAuditsRefusesFinalConfirmation() throws Exception {
        var f=new Fixture();f.audit(1,true,0);f.audit(2,true,0);f.audit(3,true,0);
        f.mc.player.getInventory().setItem(0,new ItemStack(Items.GOLDEN_APPLE));
        assertFalse(f.c.confirmManualResolution(f.mc,null).ok());
        assertEquals(0,f.p.resolutions);
        assertTrue(f.c.snapshot().unresolvedExposure());
    }

    private static final class Fixture {
        final AutomatedExecutionDriver d=new AutomatedExecutionDriver();
        final AutomationSessionController c=new AutomationSessionController(d);
        final Persistence p=new Persistence();
        final Minecraft mc;final long now=System.currentTimeMillis();
        Fixture() throws Exception {
            SharedConstants.tryDetectVersion();Bootstrap.bootStrap();
            mc=allocate(Minecraft.class);mc.player=allocate(LocalPlayer.class);mc.level=allocate(ClientLevel.class);
            var inventory=new Inventory(mc.player,new EntityEquipment());
            field(Player.class,"inventory").set(mc.player,inventory);
            var menu=new InventoryMenu(inventory,true,mc.player);
            field(Player.class,"inventoryMenu").set(mc.player,menu);mc.player.containerMenu=menu;
            c.setPersistence(p);
            set(c,"recoveryApplied",true);set(c,"sessionOpen",true);set(c,"recoveredSession",true);
            set(c,"pausedByPlayer",true);set(c,"armed",false);set(c,"state",AutomationSessionController.State.PAUSED);
            set(c,"trackedPosition",new Position(77,"REAL","minecraft:golden_apple",StackBucket.of(1),1,1000,1500,100,0,0,0,Double.NaN,PositionStatus.PURCHASED));
            set(c,"uncertainExposure",new AutomationUncertainExposure(true,"LISTING","position:77","minecraft:golden_apple","minecraft:golden_apple",1,1000,1500,"",100));
            set(c,"committedSpend",12345L);set(c,"tradesStarted",7);
            set(c,"policy",ContinuousAutomationPolicy.fromConfigValues(20,100000,50000,5000,12,80,10,8,120,1));
            set(c,"manualResolutionFirstConfirmedAt",now-10000);set(c,"manualResolutionCoverageBoundary",now-10000);
            set(c,"manualNativeRecovery",true);
            ((RecoveryListingEvidence)get(c,"manualNativeEvidence")).begin(now-10000);
        }
        void audit(int sequence,boolean complete,int unreadable)throws Exception {
            long at=now-8000+sequence*1000;
            set(c,"manualNativeAuditPending",true);set(c,"manualNativeAuditStartedAt",at-100);
            set(d,"lastOwnListings",List.of());set(d,"lastOwnListingsUnreadable",unreadable);
            set(d,"lastTerminalEvent",new AutomatedExecutionDriver.TerminalEvent(sequence,AutomatedExecutionDriver.OperationIntent.AUDIT,
                complete?AutomatedExecutionDriver.TerminalOutcome.SUCCEEDED:AutomatedExecutionDriver.TerminalOutcome.ABORTED,
                "","","",0,0,"",false,at,"offline native audit fixture"));
            var m=c.getClass().getDeclaredMethod("updateNativeManualResolutionEvidence",Minecraft.class,long.class);
            m.setAccessible(true);m.invoke(c,mc,now);
        }
    }
    private static final class Persistence implements AutomationPersistencePort {
        int resolutions;AutomationSessionCheckpoint last;
        Status status=new Status(LoadState.READY,AutomationSessionRecovery.empty(),1,1,"offline fixture");
        public void start(){}public void close(){}public Status status(){return status;}
        public long submit(AutomationSessionCheckpoint c){throw new AssertionError("Unexpected ordinary write");}
        public long submitManualResolution(AutomationManualResolution r,AutomationSessionCheckpoint c){
            resolutions++;last=c;status=new Status(LoadState.READY,status.recovery(),2,1,"queued");return 2;
        }
        void acknowledge(){status=new Status(LoadState.READY,new AutomationSessionRecovery(true,"CONTINUOUS","PAUSED",7,12345,100,200,"offline durable receipt","",AutomationUncertainExposure.none(),List.of()),2,2,"durable");}
    }
    private static Field field(Class<?> c,String n)throws Exception{var f=c.getDeclaredField(n);f.setAccessible(true);return f;}
    private static Object get(Object o,String n)throws Exception{return field(o.getClass(),n).get(o);}
    private static void set(Object o,String n,Object v)throws Exception{field(o.getClass(),n).set(o,v);}
    private static void invoke(Object o,String n)throws Exception{var m=o.getClass().getDeclaredMethod(n);m.setAccessible(true);m.invoke(o);}
    private static <T>T allocate(Class<T> c)throws Exception{return c.cast(((Unsafe)field(Unsafe.class,"theUnsafe").get(null)).allocateInstance(c));}
}
