package dev.doughbay.fabric.automation;

import dev.doughbay.core.automation.ContinuousAutomationPolicy;
import dev.doughbay.core.model.*;
import dev.doughbay.fabric.AutomatedExecutionDriver;
import dev.doughbay.fabric.MarketWatcher;
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

    @Test void queuedPurchaseAgeAndEmptyInventoryCannotCancelOwnershipOrReleaseSpend() throws Exception {
        var f=new Fixture();Object previous=field(Minecraft.class,"instance").get(null);
        try {
            field(Minecraft.class,"instance").set(null,f.mc);
            field(Minecraft.class,"isLocalServer").setBoolean(f.mc,true);
            field(LocalPlayer.class,"connection").set(f.mc.player,allocate(net.minecraft.client.multiplayer.ClientPacketListener.class));
            field(Minecraft.class,"gui").set(f.mc,allocate(net.minecraft.client.gui.Gui.class));
            f.mc.gameMode=allocate(net.minecraft.client.multiplayer.MultiPlayerGameMode.class);
            f.d.setAuthorizedExecutionEnabled(true);f.d.setAuthorizedServers(List.of("singleplayer"));
            set(f.c,"continuousAuthorizationEnabled",true);set(f.c,"continuousAuthorizedServers",Set.of("singleplayer"));
            set(f.c,"saleNotificationTrustedServers",Set.of("singleplayer"));set(f.c,"auctionFeesConfirmed",true);
            set(f.c,"pausedByPlayer",false);set(f.c,"manualResolutionFirstConfirmedAt",0L);
            set(f.c,"trackedPosition",null);set(f.c,"recoveredSession",false);
            set(f.c,"uncertainExposure",AutomationUncertainExposure.none());set(f.c,"state",AutomationSessionController.State.SCANNING);
            set(f.c,"serverListedSlots",0);set(f.c,"serverListedAt",f.now);
            var stock=new Position(78,"REAL","minecraft:golden_apple",StackBucket.of(1),1,1000,1500,f.now-31*60_000,0,0,0,Double.NaN,PositionStatus.PURCHASED);
            @SuppressWarnings("unchecked") var queue=(List<Position>)get(f.c,"recoveredQueue");queue.add(stock);
            var snapshot=new MarketWatcher.Snapshot(List.of(),List.of(),List.of(),List.of(),Map.of(),Map.of(),null,"",-1,"OK",null,f.now-100,f.now,false);
            var scan=f.c.getClass().getDeclaredMethod("scanAndBuy",Minecraft.class,MarketWatcher.Snapshot.class,long.class);
            scan.setAccessible(true);scan.invoke(f.c,f.mc,snapshot,f.now);
            assertTrue(f.p.writes.stream().noneMatch(w->w.position()!=null&&w.position().status().isClosed()),"Inventory absence alone cannot cancel a queued purchase");
            assertEquals(12345L,get(f.c,"committedSpend"),"An unresolved purchase cannot release the durable spending cap");
            assertEquals(stock,get(f.c,"trackedPosition"),f.c.snapshot().detail());
            assertTrue(f.c.snapshot().unresolvedExposure());
            assertEquals(AutomationSessionController.State.PAUSED,f.c.snapshot().state());
        } finally {field(Minecraft.class,"instance").set(null,previous);}
    }

    @Test void excludedOwnedPurchaseKeepsRecordAndSpendingCommitment() throws Exception {
        restrictedOwnedPurchase("", "=golden_apple");
    }
    @Test void ownedPurchaseRemovedFromIncludedMarketsKeepsRecordAndSpendingCommitment() throws Exception {
        restrictedOwnedPurchase("=diamond", "");
    }
    private void restrictedOwnedPurchase(String include,String exclude) throws Exception {
        var f=new Fixture();Object previous=field(Minecraft.class,"instance").get(null);
        String allow=dev.doughbay.fabric.Tuning.text("items.allow"), deny=dev.doughbay.fabric.Tuning.text("items.deny"), liquidate=dev.doughbay.fabric.Tuning.text("items.liquidate");
        try {
            field(Minecraft.class,"instance").set(null,f.mc);
            dev.doughbay.fabric.Tuning.setText("items.allow",include);
            dev.doughbay.fabric.Tuning.setText("items.deny",exclude);
            dev.doughbay.fabric.Tuning.setText("items.liquidate","");
            f.mc.player.getInventory().setItem(0,new ItemStack(Items.GOLDEN_APPLE));
            Position owned=(Position)get(f.c,"trackedPosition");
            var list=f.c.getClass().getDeclaredMethod("listPurchasedStackInHand",Minecraft.class,long.class,String.class);
            list.setAccessible(true);list.invoke(f.c,f.mc,f.now,"offline owned stock fixture");
            assertTrue(f.p.writes.stream().noneMatch(w->w.position()!=null&&w.position().status().isClosed()),"An item restriction cannot erase an owned purchase");
            assertEquals(12345L,get(f.c,"committedSpend"),"Unsold owned goods cannot release the spending commitment");
            assertEquals(owned,get(f.c,"trackedPosition"));
            assertEquals(1,f.mc.player.getInventory().getItem(0).getCount());
            assertEquals(AutomatedExecutionDriver.OperationIntent.NONE,f.d.operationIntent(),"Do not list excluded goods");
            assertEquals(AutomationSessionController.State.PAUSED,f.c.snapshot().state());
            list.invoke(f.c,f.mc,f.now+1000,"offline repeated recovery");
            assertEquals(owned,get(f.c,"trackedPosition"));
            assertEquals(12345L,get(f.c,"committedSpend"));
            assertTrue(f.p.writes.stream().noneMatch(w->w.position()!=null&&w.position().status().isClosed()));
        } finally {
            dev.doughbay.fabric.Tuning.setText("items.allow",allow);dev.doughbay.fabric.Tuning.setText("items.deny",deny);dev.doughbay.fabric.Tuning.setText("items.liquidate",liquidate);
            field(Minecraft.class,"instance").set(null,previous);
        }
    }

    @Test void preparationRetryExhaustionCannotCancelStockStillInInventory() throws Exception {
        preparationRetryRetainsOwnedPurchase(true);
    }
    @Test void preparationRetryExhaustionCannotTreatMissingStockAsAnOutcome() throws Exception {
        preparationRetryRetainsOwnedPurchase(false);
    }
    private void preparationRetryRetainsOwnedPurchase(boolean present) throws Exception {
        var f=new Fixture();Object previous=field(Minecraft.class,"instance").get(null);
        try {
            field(Minecraft.class,"instance").set(null,f.mc);
            field(Minecraft.class,"gui").set(f.mc,allocate(net.minecraft.client.gui.Gui.class));
            f.mc.gameMode=allocate(net.minecraft.client.multiplayer.MultiPlayerGameMode.class);
            Position owned=(Position)get(f.c,"trackedPosition");
            if(present)f.mc.player.getInventory().setItem(20,new ItemStack(Items.GOLDEN_APPLE));
            set(f.c,"state",AutomationSessionController.State.PREPARING_LIST);
            set(f.c,"preparationStartedAtMillis",f.now);set(f.c,"preListMarketVerified",true);
            set(f.c,"preListVerifiedScanStartedAt",f.now);set(f.c,"swapIssuedAtTick",0L);set(f.c,"controllerTick",20L);
            set(f.c,"prepareRetryPositionId",owned.positionId());
            @SuppressWarnings("unchecked") var failures=(Map<String,Integer>)get(f.c,"unpreparableItems");failures.put(owned.itemKey(),6);
            var prepare=f.c.getClass().getDeclaredMethod("prepareAndList",Minecraft.class,MarketWatcher.Snapshot.class,long.class);
            prepare.setAccessible(true);prepare.invoke(f.c,f.mc,null,f.now);
            assertTrue(f.p.writes.stream().noneMatch(w->w.position()!=null&&w.position().status().isClosed()),"Failed preparation is not proof of sale, loss or cancelled ownership");
            assertEquals(12345L,get(f.c,"committedSpend"));assertEquals(owned,get(f.c,"trackedPosition"));
            assertEquals(AutomatedExecutionDriver.OperationIntent.NONE,f.d.operationIntent());
            assertEquals(AutomationSessionController.State.PAUSED,f.c.snapshot().state());
        } finally {field(Minecraft.class,"instance").set(null,previous);}
    }

    @Test void boundedRetailShortfallRetainsMissingCostWhilePresentRemainderCanContinue() throws Exception {
        var f=new Fixture();Object previous=field(Minecraft.class,"instance").get(null);
        try {
            field(Minecraft.class,"instance").set(null,f.mc);
            var parent=new Position(77,"REAL","minecraft:golden_apple",StackBucket.of(127),127,127000,254000,100,0,0,0,Double.NaN,PositionStatus.PURCHASED);
            set(f.c,"trackedPosition",parent);set(f.c,"committedSpend",200000L);
            f.mc.player.getInventory().setItem(0,new ItemStack(Items.GOLDEN_APPLE,63));
            f.mc.player.getInventory().setItem(1,new ItemStack(Items.GOLDEN_APPLE,63));
            @SuppressWarnings("unchecked") var lots=(Map<Long,RetailLot>)get(f.c,"retailLots");lots.put(parent.positionId(),new RetailLot(parent.positionId(),parent.positionId(),2000));
            var shortfall=f.c.getClass().getDeclaredMethod("writeOffMissingRetail",long.class,int.class);shortfall.setAccessible(true);
            for(int i=0;i<5;i++)assertFalse((boolean)shortfall.invoke(f.c,f.now+i*40000,126));
            assertEquals(0,f.p.retailSplits,"Shortfall still requires bounded repeated native inventory evidence");
            assertTrue((boolean)shortfall.invoke(f.c,f.now+200000,126));
            assertEquals(200000L,get(f.c,"committedSpend"),"Missing stock is not returned money and cannot reopen spending headroom");
            assertEquals(1,f.p.retailSplits);assertEquals(126,f.p.retailRemainder.quantity());
            assertEquals(parent.purchasePrice(),f.p.retailRemainder.purchasePrice()+f.p.retailMissing.purchasePrice());
            @SuppressWarnings("unchecked") var queue=(List<Position>)get(f.c,"recoveredQueue");
            assertTrue(queue.stream().anyMatch(q->q.positionId()==f.p.retailRemainder.positionId()),"Verified present remainder still continues under existing retail flow");
        } finally {field(Minecraft.class,"instance").set(null,previous);}
    }

    private static final class Fixture {
        final AutomatedExecutionDriver d=new AutomatedExecutionDriver();
        final AutomationSessionController c=new AutomationSessionController(d);
        final Persistence p=new Persistence();
        final Minecraft mc;final long now=System.currentTimeMillis();
        Fixture() throws Exception {
            SharedConstants.tryDetectVersion();Bootstrap.bootStrap();
            mc=allocate(Minecraft.class);mc.player=allocate(AlivePlayer.class);mc.level=allocate(ClientLevel.class);
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
        final List<AutomationSessionCheckpoint> writes=new ArrayList<>();
        int resolutions;AutomationSessionCheckpoint last;
        Status status=new Status(LoadState.READY,AutomationSessionRecovery.empty(),1,1,"offline fixture");
        public void start(){}public void close(){}public Status status(){return status;}
        public long submit(AutomationSessionCheckpoint c){writes.add(c);return writes.size()+2;}
        int retailSplits;Position retailRemainder,retailMissing;
        public long submitRetail(AutomationSessionCheckpoint c,RetailLot lot,Position parent,Position remainder){
            retailSplits++;retailMissing=c.position();retailRemainder=remainder;writes.add(c);return writes.size()+2;
        }
        public long submitManualResolution(AutomationManualResolution r,AutomationSessionCheckpoint c){
            resolutions++;last=c;status=new Status(LoadState.READY,status.recovery(),2,1,"queued");return 2;
        }
        void acknowledge(){status=new Status(LoadState.READY,new AutomationSessionRecovery(true,"CONTINUOUS","PAUSED",7,12345,100,200,"offline durable receipt","",AutomationUncertainExposure.none(),List.of()),2,2,"durable");}
    }
    private static class AlivePlayer extends LocalPlayer {
        private AlivePlayer(){super(null,null,null,null,null,null,false,null);}
        @Override public boolean isAlive(){return true;}
        @Override public void sendSystemMessage(net.minecraft.network.chat.Component c){}
    }
    private static Field field(Class<?> c,String n)throws Exception{var f=c.getDeclaredField(n);f.setAccessible(true);return f;}
    private static Object get(Object o,String n)throws Exception{return field(o.getClass(),n).get(o);}
    private static void set(Object o,String n,Object v)throws Exception{field(o.getClass(),n).set(o,v);}
    private static void invoke(Object o,String n)throws Exception{var m=o.getClass().getDeclaredMethod(n);m.setAccessible(true);m.invoke(o);}
    private static <T>T allocate(Class<T> c)throws Exception{return c.cast(((Unsafe)field(Unsafe.class,"theUnsafe").get(null)).allocateInstance(c));}
}
