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

/** Actual authorized recovery controller with synthetic native container; no GUI or network. */
class RecoveryAuction392Test {
    @org.junit.jupiter.api.BeforeAll static void bind(){SharedConstants.tryDetectVersion();Bootstrap.bootStrap();}

    @Test void confirmedRecoveryCanLeaveKnownAuctionWithEmptyCursor()throws Exception{exercise("Auction House",false,true);exercise("Auction -> Your Items",false,true);}
    @Test void cursorAndUnrelatedContainersRemainUntouched()throws Exception{exercise("Auction House",true,false);exercise("Personal storage",false,false);}
    @Test void inventoryCursorAuthorizationAndFirstConfirmationRemainRequired()throws Exception{
        exercise("Auction House",false,false,true,true,true);
        exercise("Auction House",false,false,false,false,true);
        exercise("Auction House",false,false,false,true,false);
    }
    private void exercise(String title,boolean cursor,boolean expectClose)throws Exception{exercise(title,cursor,expectClose,false,true,true);}
    private void exercise(String title,boolean cursor,boolean expectClose,boolean inventoryCursor,boolean authorized,boolean confirmed)throws Exception{
        var f=new Fixture();set(f.c,"uncertainExposure",AutomationUncertainExposure.none());Object prior=field(Minecraft.class,"instance").get(null);
        try{
            field(Minecraft.class,"instance").set(null,f.mc);field(Minecraft.class,"isLocalServer").setBoolean(f.mc,true);
            field(LocalPlayer.class,"connection").set(f.mc.player,allocate(net.minecraft.client.multiplayer.ClientPacketListener.class));
            field(Minecraft.class,"gui").set(f.mc,allocate(net.minecraft.client.gui.Gui.class));f.mc.gameMode=allocate(net.minecraft.client.multiplayer.MultiPlayerGameMode.class);
            f.d.setAuthorizedExecutionEnabled(true);f.d.setAuthorizedServers(List.of("singleplayer"));
            set(f.c,"continuousAuthorizationEnabled",true);set(f.c,"continuousAuthorizedServers",Set.of("singleplayer"));set(f.c,"saleNotificationTrustedServers",Set.of("singleplayer"));set(f.c,"auctionFeesConfirmed",true);
            var contents=new net.minecraft.world.SimpleContainer(54);var menu=new net.minecraft.world.inventory.ChestMenu(net.minecraft.world.inventory.MenuType.GENERIC_9x6,5,f.mc.player.getInventory(),contents,6);f.mc.player.containerMenu=menu;
            var screen=allocate(net.minecraft.client.gui.screens.inventory.ContainerScreen.class);
            field(net.minecraft.client.gui.screens.inventory.AbstractContainerScreen.class,"menu").set(screen,menu);
            field(net.minecraft.client.gui.screens.Screen.class,"title").set(screen,net.minecraft.network.chat.Component.literal(title));field(net.minecraft.client.Minecraft.class,"screen").set(f.mc, screen);
            if(cursor)menu.setCarried(new ItemStack(Items.DIAMOND));
            if(inventoryCursor)f.mc.player.inventoryMenu.setCarried(new ItemStack(Items.DIAMOND));
            if(!authorized)set(f.c,"staffTransferHold",true);
            if(!confirmed)set(f.c,"manualResolutionFirstConfirmedAt",0L);
            var auth=f.c.getClass().getDeclaredMethod("authorizationBlocker",Minecraft.class);auth.setAccessible(true);if(authorized)assertNull(auth.invoke(f.c,f.mc),"Synthetic execution environment must satisfy ordinary authorization");else assertNotNull(auth.invoke(f.c,f.mc));
            if(confirmed){var m=f.c.getClass().getDeclaredMethod("updateNativeManualResolutionEvidence",Minecraft.class,long.class);m.setAccessible(true);m.invoke(f.c,f.mc,f.now);}
            else {var m=f.c.getClass().getDeclaredMethod("tickInternal",Minecraft.class,MarketWatcher.Snapshot.class);m.setAccessible(true);m.invoke(f.c,f.mc,null);}
            assertEquals(expectClose?1:0,((AlivePlayer)f.mc.player).closed);
            assertEquals(12345L,get(f.c,"committedSpend"));assertEquals(7,get(f.c,"tradesStarted"));assertEquals(77,((Position)get(f.c,"trackedPosition")).positionId());
            assertEquals(AutomationSessionController.State.PAUSED,f.c.snapshot().state());assertFalse((boolean)get(f.c,"manualResolutionEvidenceReady"));assertEquals(0,f.p.resolutions);
        }finally{field(Minecraft.class,"instance").set(null,prior);}
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
        private AlivePlayer(){super(null,null,null,null,null,null,false);}
        int closed;
        @Override public void closeContainer(){closed++;containerMenu=inventoryMenu;try{field(net.minecraft.client.Minecraft.class,"screen").set(Minecraft.getInstance(), null);}catch(Exception e){throw new RuntimeException(e);}}
        @Override public boolean isAlive(){return true;}
        @Override public void displayClientMessage(net.minecraft.network.chat.Component c, boolean actionBar){}
    }
    private static Field field(Class<?> c,String n)throws Exception{var f=c.getDeclaredField(n);f.setAccessible(true);return f;}
    private static Object get(Object o,String n)throws Exception{return field(o.getClass(),n).get(o);}
    private static void set(Object o,String n,Object v)throws Exception{field(o.getClass(),n).set(o,v);}
    private static void invoke(Object o,String n)throws Exception{var m=o.getClass().getDeclaredMethod(n);m.setAccessible(true);m.invoke(o);}
    private static <T>T allocate(Class<T> c)throws Exception{return c.cast(((Unsafe)field(Unsafe.class,"theUnsafe").get(null)).allocateInstance(c));}
}
