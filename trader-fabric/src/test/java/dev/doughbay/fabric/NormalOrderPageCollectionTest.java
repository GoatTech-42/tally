package dev.doughbay.fabric;

import java.lang.reflect.*;
import java.util.*;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;
import static org.junit.jupiter.api.Assertions.*;

/** Runs the actual native own-order reader against an offline menu, using report305's order mode. */
class NormalOrderPageCollectionTest {
    @Test void expiredNativeCountRearmsBelowCapEvenWithoutMarketSnapshot() throws Exception {
        var f=fixture(false);setOperation(f.driver,"NONE");
        var menu=new net.minecraft.world.inventory.InventoryMenu(f.client.player.getInventory(),true,f.client.player);
        field(Player.class,"inventoryMenu").set(f.client.player,menu);f.client.player.containerMenu=menu;
        field(Gui.class,"screen").set(f.client.gui,null);
        var c=new dev.doughbay.fabric.automation.AutomationSessionController(f.driver);var type=c.getClass();
        long now=System.currentTimeMillis();field(type,"serverListedSlots").setInt(c,6);
        field(type,"serverListedAt").setLong(c,now-601_000);
        var scan=type.getDeclaredMethod("scanAndBuy",Minecraft.class,MarketWatcher.Snapshot.class,long.class);scan.setAccessible(true);
        scan.invoke(c,f.client,null,now);
        assertEquals(now,field(type,"lastMissingCountAuditAttemptAt").getLong(c),"Expired occupancy must reach the native audit path below capacity, even with no market snapshot");
        assertEquals(-1,c.serverListedSlots(),"Attempting a recount must never fabricate a fresh count");
        scan.invoke(c,f.client,null,now+59_000);
        assertEquals(now,field(type,"lastMissingCountAuditAttemptAt").getLong(c),"A refused native audit must not be retried every tick");
        scan.invoke(c,f.client,null,now+60_000);
        assertEquals(now+60_000,field(type,"lastMissingCountAuditAttemptAt").getLong(c),"An expired source may retry at the existing minute cadence");
    }

    @Test void missingCountMaintenanceRetainsInventoryAndOperationGates() throws Exception {
        var f=fixture(false);var c=new dev.doughbay.fabric.automation.AutomationSessionController(f.driver);var type=c.getClass();
        var scan=type.getDeclaredMethod("scanAndBuy",Minecraft.class,MarketWatcher.Snapshot.class,long.class);scan.setAccessible(true);
        long now=System.currentTimeMillis();scan.invoke(c,f.client,null,now);
        assertEquals(0,field(type,"lastMissingCountAuditAttemptAt").getLong(c),"An active own-order read cannot be interrupted by occupancy maintenance");
        setOperation(f.driver,"NONE");scan.invoke(c,f.client,null,now);
        assertEquals(0,field(type,"lastMissingCountAuditAttemptAt").getLong(c),"An open container is not a safe inventory boundary");
        var menu=new net.minecraft.world.inventory.InventoryMenu(f.client.player.getInventory(),true,f.client.player);
        field(Player.class,"inventoryMenu").set(f.client.player,menu);f.client.player.containerMenu=menu;field(Gui.class,"screen").set(f.client.gui,null);
        field(type,"serverListedSlots").setInt(c,6);field(type,"serverListedAt").setLong(c,now);
        scan.invoke(c,f.client,null,now);
        assertEquals(0,field(type,"lastMissingCountAuditAttemptAt").getLong(c),"Fresh native occupancy must not cause an extra audit");
        field(type,"serverListedAt").setLong(c,0);field(type,"staffTransferHold").setBoolean(c,true);scan.invoke(c,f.client,null,now);
        assertEquals(0,field(type,"lastMissingCountAuditAttemptAt").getLong(c),"A native transfer hold remains authoritative");
    }
    @org.junit.jupiter.api.BeforeAll static void bootstrap(){
        SharedConstants.tryDetectVersion();Bootstrap.bootStrap();
        net.minecraft.core.registries.BuiltInRegistries.DATA_COMPONENT_INITIALIZERS
                .build(net.minecraft.data.registries.VanillaRegistries.createLookup())
                .forEach(net.minecraft.core.component.DataComponentInitializers.PendingComponents::apply);
    }
    @Test void normalModeMustNotFinishAFullFirstPageThatHasANextPage() throws Exception {
        double mode=Tuning.get("orders.max_per_market"), trip=Tuning.get("orders.one_trip");
        try {
            Tuning.set("orders.max_per_market",0);Tuning.set("orders.one_trip",1);
            var f=fixture(true);read(f);
            assertEquals(45,f.driver.lastOwnOrders().size(),"All native fixture rows must parse");
            assertEquals(AutomatedExecutionDriver.TerminalOutcome.NONE,f.driver.lastTerminalEvent().outcome(),
                    "A full first page with Next is not a completed own-book read, even with one order per item");
            assertFalse(f.driver.lastOwnOrdersComplete());
        } finally {Tuning.set("orders.max_per_market",mode);Tuning.set("orders.one_trip",trip);ServerStrain.reset();}
    }

    @Test void settledFullFinalPageCanSupplyCompleteDeliveryEvidenceInNormalMode() throws Exception {
        double mode=Tuning.get("orders.max_per_market"), trip=Tuning.get("orders.one_trip");
        try {
            Tuning.set("orders.max_per_market",0);Tuning.set("orders.one_trip",1);
            var f=fixture(false);read(f);
            var pages=field(AutomatedExecutionDriver.class,"ownPages").get(f.driver);
            field(OwnOrderPages.class,"candidateAt").setLong(pages,System.currentTimeMillis()-1000);
            read(f);
            assertEquals(AutomatedExecutionDriver.TerminalOutcome.SUCCEEDED,f.driver.lastTerminalEvent().outcome());
            assertTrue(f.driver.lastOwnOrdersComplete(),"A settled native end page must allow delivery retry evidence to advance");
            assertEquals(45,f.driver.lastOwnOrders().size());
        } finally {Tuning.set("orders.max_per_market",mode);Tuning.set("orders.one_trip",trip);ServerStrain.reset();}
    }

    @Test void normalCollectionFindsAnOwnedOrderBeyondTheFullFirstPage() throws Exception {
        double mode=Tuning.get("orders.max_per_market");
        try {
            Tuning.set("orders.max_per_market",0);var f=fixture(true);
            setOperation(f.driver,"COLLECT_ORDER");
            field(AutomatedExecutionDriver.class,"targetItemId").set(f.driver,"minecraft:diamond");
            field(AutomatedExecutionDriver.class,"ownTargetUnitPrice").setLong(f.driver,200);
            field(AutomatedExecutionDriver.class,"ownMatchRequested").setInt(f.driver,192);
            read(f);settle(f);read(f);
            var clicks=(RecordingGameMode)f.client.gameMode;
            assertEquals(List.of(53),clicks.slots,"The actual driver must follow native Next before declaring the order missing");
            f=secondPage(f);read(f);settle(f);f.client.player.tickCount+=100;read(f);
            assertEquals(List.of(53,0),clicks.slots,"Open only the matching owned row on the next page");
            assertEquals(3,field(AutomatedExecutionDriver.class,"ownStage").getInt(f.driver));
            assertEquals(AutomatedExecutionDriver.TerminalOutcome.NONE,f.driver.lastTerminalEvent().outcome());
        } finally {Tuning.set("orders.max_per_market",mode);ServerStrain.reset();}
    }

    @Test void normalReadAccumulatesAllPagesAndDoesNotReuseTheLastPageForAnotherOrder() throws Exception {
        double mode=Tuning.get("orders.max_per_market"),trip=Tuning.get("orders.one_trip");
        try {
            Tuning.set("orders.max_per_market",0);Tuning.set("orders.one_trip",1);var f=fixture(true);
            read(f);settle(f);read(f);f=secondPage(f);read(f);settle(f);read(f);
            assertTrue(f.driver.lastOwnOrdersComplete());assertEquals(46,f.driver.lastOwnOrders().size());
            assertFalse(f.driver.holdsOrdersPage(f.client),"A final page must not become the starting page for a new placement");
        } finally {Tuning.set("orders.max_per_market",mode);Tuning.set("orders.one_trip",trip);ServerStrain.reset();}
    }

    @SuppressWarnings("unchecked")
    @Test void fullNormalBookLetsNewDeliveriesReopenAnExhaustedCollection() throws Exception {
        double mode=Tuning.get("orders.max_per_market"),bid=Tuning.get("orders.bid"),reserve=Tuning.get("orders.reserve_stock");
        try {
            Tuning.set("orders.max_per_market",0);Tuning.set("orders.bid",0);Tuning.set("orders.reserve_stock",0);
            var controller=new dev.doughbay.fabric.automation.AutomationSessionController(new AutomatedExecutionDriver());
            var type=controller.getClass();field(type,"policy").set(controller,dev.doughbay.core.automation.ContinuousAutomationPolicy.fromConfigValues(2,100_000,50_000,5_000,12,80,10,8,120,1));
            var f=fixture(false);read(f);settle(f);read(f);plan(controller,f.driver,1_000_000);
            String key="minecraft:stone|100|192";
            Map<String,Integer> attempts=(Map<String,Integer>)field(type,"collectAttempts").get(controller);attempts.put(key,4);
            var newer=fixture(false);
            for(int i=0;i<45;i++)newer.screen.getMenu().slots.get(i).getItem().set(DataComponents.LORE,new ItemLore(List.of(Component.literal("192 requested"),Component.literal("$"+(100+i)+" each"),Component.literal("128/192 delivered"))));
            read(newer);settle(newer);read(newer);plan(controller,newer.driver,1_100_000);
            assertNull(attempts.get(key),"Increased deliveries from a complete native full page must release the earlier retry budget");
            assertTrue(((Deque<?>)field(type,"deskQueue").get(controller)).stream().anyMatch(s->s.toString().contains("COLLECT")));
        } finally {Tuning.set("orders.max_per_market",mode);Tuning.set("orders.bid",bid);Tuning.set("orders.reserve_stock",reserve);ServerStrain.reset();}
    }
    @Test void completed192ItemOrderPhysicallyTransfersAllThreeStacks() throws Exception {
        physicalCollection(192,192,0,true,192);
    }

    @Test void explicitlyArmedCollectionInspectionCapturesNativeControlsWithoutClicking(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) throws Exception {
        var f=fixture(false);field(Minecraft.class,"gameDirectory").set(f.client,dir.toFile());
        f=collectionMenu(f,"Orders -> Edit Order",0);
        ItemStack button=new ItemStack(Items.CHEST);button.set(DataComponents.CUSTOM_NAME,Component.literal("Collect Items privatePlayer"));
        button.set(DataComponents.LORE,new ItemLore(List.of(Component.literal("Click to collect. secret123"))));
        f.screen.getMenu().slots.get(11).set(button);
        NativeOrderEvidence.request();
        try {
            NativeOrderEvidence.captureCollectionIfRequested(f.client,f.screen,"minecraft:stone",3,1,2,0,0,0);
            var target=dir.resolve("config/doughbay/native-collection-evidence.json");
            String text=java.nio.file.Files.readString(target);var root=dev.doughbay.fabric.hosted.DashboardProtocol.JSON.readTree(text);
            assertEquals("EDIT_ORDER",root.path("frames").get(0).path("page").asText());
            assertEquals(11,root.path("frames").get(0).path("slots").get(0).path("slot").asInt());
            assertFalse(text.contains("privatePlayer"));assertFalse(text.contains("secret123"));
            assertTrue(((RecordingGameMode)f.client.gameMode).slots.isEmpty());
            NativeOrderEvidence.captureCollectionIfRequested(f.client,f.screen,"minecraft:stone",3,1,2,0,0,0);
            assertEquals(text,java.nio.file.Files.readString(target),"Unchanged screens must not rewrite or fill the bounded capture");
            field(NativeOrderEvidence.class,"collectionUntil").setLong(null,0);
            NativeOrderEvidence.captureCollectionIfRequested(f.client,f.screen,"minecraft:stone",5,1,2,0,0,0);
            assertEquals(text,java.nio.file.Files.readString(target),"Expired inspection must have no filesystem effect");
        }finally{field(NativeOrderEvidence.class,"collectionUntil").setLong(null,0);}
    }
    @Test void boundedCleanupCanTakeAnUnfinishedPhysicalTailWhenWholeStacksAreNotRequired() throws Exception {
        double pace=Tuning.get("pace.human_steps");
        try {
            Tuning.set("pace.human_steps",0);var f=fixture(false);setOperation(f.driver,"COLLECT_ORDER");
            field(AutomatedExecutionDriver.class,"targetItemId").set(f.driver,"minecraft:stone");
            field(AutomatedExecutionDriver.class,"ownStage").setInt(f.driver,5);
            field(AutomatedExecutionDriver.class,"collectWholeStacksOnly").setBoolean(f.driver,false);
            field(AutomatedExecutionDriver.class,"collectMaximumUnits").setInt(f.driver,64);
            field(AutomatedExecutionDriver.class,"ownTargetFinished").setBoolean(f.driver,false);
            f=collectionMenu(f,"Orders -> Collect Items",5);((RecordingGameMode)f.client.gameMode).moveStacks=true;
            for(int i=0;i<5&&f.driver.lastTerminalEvent().outcome()==AutomatedExecutionDriver.TerminalOutcome.NONE;i++)advance(f);
            assertEquals(5,AutomatedExecutionDriver.inventoryCount(f.client,"minecraft:stone"),"Explicit cleanup must transfer the native tail within its unchanged64-unit capacity budget");
            assertEquals(AutomatedExecutionDriver.TerminalOutcome.SUCCEEDED,f.driver.lastTerminalEvent().outcome());
            assertEquals(5,f.driver.lastTerminalEvent().itemCount());
            for(boolean wholeOnly:new boolean[]{true,false}) {
                var blocked=fixture(false);setOperation(blocked.driver,"COLLECT_ORDER");
                field(AutomatedExecutionDriver.class,"targetItemId").set(blocked.driver,"minecraft:stone");
                field(AutomatedExecutionDriver.class,"ownStage").setInt(blocked.driver,5);
                field(AutomatedExecutionDriver.class,"collectWholeStacksOnly").setBoolean(blocked.driver,wholeOnly);
                field(AutomatedExecutionDriver.class,"collectMaximumUnits").setInt(blocked.driver,wholeOnly?64:4);
                blocked=collectionMenu(blocked,"Orders -> Collect Items",5);((RecordingGameMode)blocked.client.gameMode).moveStacks=true;
                for(int i=0;i<5&&blocked.driver.lastTerminalEvent().outcome()==AutomatedExecutionDriver.TerminalOutcome.NONE;i++)advance(blocked);
                assertEquals(0,AutomatedExecutionDriver.inventoryCount(blocked.client,"minecraft:stone"),"Whole-stack policy and insufficient capacity must still refuse the tail");
                assertEquals(5,blocked.screen.getMenu().slots.get(0).getItem().getCount());
                assertEquals(0,blocked.driver.lastTerminalEvent().itemCount());
            }
        }finally{Tuning.set("pace.human_steps",pace);ServerStrain.reset();}
    }

    @Test void reportedLockedUpgradePageCompletesAndRetainsEarlierOwnedRows() throws Exception {
        var f=fixture(true);fillLockedSlots(f,0);
        var pages=(OwnOrderPages)field(AutomatedExecutionDriver.class,"ownPages").get(f.driver);
        var earlier=java.util.Collections.nCopies(3,new AutomatedExecutionDriver.OwnOrderRow("minecraft:diamond","minecraft:diamond",192,200,64,1000));
        assertTrue(pages.append(earlier));read(f);
        assertEquals(AutomatedExecutionDriver.TerminalOutcome.SUCCEEDED,f.driver.lastTerminalEvent().outcome());
        assertTrue(f.driver.lastOwnOrdersComplete());assertEquals(earlier,f.driver.lastOwnOrders());
        assertEquals(0,f.driver.lastOwnReadUnparsedCount());
        assertTrue(((RecordingGameMode)f.client.gameMode).slots.isEmpty(),"A locked final page must not be clicked or treated as purchase capacity");
    }

    @Test void mixedNativeOrdersAndLockedSlotsCompleteWithoutLosingOrders() throws Exception {
        var f=fixture(true);fillLockedSlots(f,3);read(f);settle(f);read(f);
        assertEquals(AutomatedExecutionDriver.TerminalOutcome.SUCCEEDED,f.driver.lastTerminalEvent().outcome());
        assertTrue(f.driver.lastOwnOrdersComplete());assertEquals(3,f.driver.lastOwnOrders().size());
        assertEquals(0,f.driver.lastOwnReadUnparsedCount());
        assertTrue(((RecordingGameMode)f.client.gameMode).slots.isEmpty());
    }

    @Test void maxOrdersNameAloneAndUnknownFillerRemainIncomplete() throws Exception {
        for(String name:List.of("Max Orders","Unrecognized native control")) {
            var f=fixture(false);for(int i=0;i<45;i++)f.screen.getMenu().slots.get(i).set(ItemStack.EMPTY);
            var tile=new ItemStack(Items.BARRIER);tile.set(DataComponents.CUSTOM_NAME,Component.literal(name));
            f.screen.getMenu().slots.get(0).set(tile);read(f);settle(f);read(f);
            assertEquals(AutomatedExecutionDriver.TerminalOutcome.ABORTED,f.driver.lastTerminalEvent().outcome());
            assertFalse(f.driver.lastOwnOrdersComplete());assertEquals(1,f.driver.lastOwnReadUnparsedCount());
        }
    }

    @Test void lockedUpgradeTilesAreNeverClickablePlacementSlots() throws Exception {
        var f=fixture(false);fillLockedSlots(f,0);setOperation(f.driver,"PLACE_ORDER");
        read(f);settle(f);read(f);
        assertTrue(((RecordingGameMode)f.client.gameMode).slots.isEmpty(),"A locked tile is not a free native slot or a Create button");
        assertNotEquals(AutomatedExecutionDriver.TerminalOutcome.NONE,f.driver.lastTerminalEvent().outcome());
    }

    @Test void actualOrderNamedMaxOrdersIsStillRetained() throws Exception {
        var f=fixture(false);fillLockedSlots(f,1);
        f.screen.getMenu().slots.get(0).getItem().set(DataComponents.CUSTOM_NAME,Component.literal("Max Orders"));
        read(f);settle(f);read(f);
        assertTrue(f.driver.lastOwnOrdersComplete());assertEquals(1,f.driver.lastOwnOrders().size());
        assertEquals(192,f.driver.lastOwnOrders().get(0).requested());
    }

    @Test void malformedOrderAndOversizedExactCountRemainIncompleteBesideLockedTiles() throws Exception {
        double mode=Tuning.get("orders.max_per_market");
        try {
            Tuning.set("orders.max_per_market",3);
            for(boolean oversized:List.of(false,true)) {
                var f=fixture(false);fillLockedSlots(f,1);var row=f.screen.getMenu().slots.get(0).getItem();
                row.set(DataComponents.CUSTOM_NAME,Component.literal("Max Orders"));
                row.set(DataComponents.LORE,new ItemLore(List.of(Component.literal(oversized?"1.2K requested":"unknown requested"),Component.literal("$100 each"),Component.literal("Buy Donut++ for even more order slots"))));
                read(f);settle(f);read(f);
                assertEquals(AutomatedExecutionDriver.TerminalOutcome.ABORTED,f.driver.lastTerminalEvent().outcome());
                assertFalse(f.driver.lastOwnOrdersComplete());assertEquals(1,f.driver.lastOwnReadUnparsedCount());
            }
        }finally{Tuning.set("orders.max_per_market",mode);}
    }

    @Test void reportedCombinedLockedTileNameAlsoCompletes() throws Exception {
        var f=fixture(false);fillLockedSlots(f,0);
        for(int i=0;i<45;i++) {
            var tile=f.screen.getMenu().slots.get(i).getItem();
            tile.set(DataComponents.CUSTOM_NAME,Component.literal("Max Orders | Buy Donut++ for even more order slots"));
            tile.remove(DataComponents.LORE);
        }
        read(f);settle(f);read(f);assertTrue(f.driver.lastOwnOrdersComplete());
        assertEquals(0,f.driver.lastOwnReadUnparsedCount());assertTrue(f.driver.lastOwnOrders().isEmpty());
    }

    private static void fillLockedSlots(Fixture f,int from) {
        for(int i=from;i<45;i++) {
            var tile=new ItemStack(Items.BARRIER);tile.set(DataComponents.CUSTOM_NAME,Component.literal("Max Orders"));
            tile.set(DataComponents.LORE,new ItemLore(List.of(Component.literal("Buy Donut++ for even more order slots"))));
            f.screen.getMenu().slots.get(i).set(tile);
        }
    }

    @Test void unparsedPageMustNotPublishSuccessfulZeroOpenWhileRetainingAccumulatedRows() throws Exception {
        var f=fixture(false);for(int i=0;i<45;i++)f.screen.getMenu().slots.get(i).set(ItemStack.EMPTY);
        var unknown=new ItemStack(Items.PAPER);unknown.set(DataComponents.CUSTOM_NAME,Component.literal("Unrecognized native control"));f.screen.getMenu().slots.get(22).set(unknown);
        var pages=(OwnOrderPages)field(AutomatedExecutionDriver.class,"ownPages").get(f.driver);
        var rows=java.util.Collections.nCopies(3,new AutomatedExecutionDriver.OwnOrderRow("minecraft:stone","minecraft:stone",192,100,0,1000));
        assertTrue(pages.append(rows));field(AutomatedExecutionDriver.class,"ownPageAccepted").setBoolean(f.driver,true);
        read(f);
        assertEquals(AutomatedExecutionDriver.TerminalOutcome.ABORTED,f.driver.lastTerminalEvent().outcome(),"Unparsed native content must enter paced read recovery instead of being reported as a successful empty book");
        assertFalse(f.driver.lastOwnOrdersComplete());
        assertEquals(3,f.driver.lastOwnOrders().size(),"Incomplete observation must not erase owned rows or manufacture an empty book");
        assertTrue(f.driver.lastTerminalEvent().detail().contains("incomplete"));
        var c=new dev.doughbay.fabric.automation.AutomationSessionController(f.driver);var type=c.getClass();
        field(type,"ownOrders").set(c,rows);field(type,"ownOrdersReadAt").setLong(c,1_000_000L);
        Class<?> action=Class.forName(type.getName()+"$DeskAction"),step=Class.forName(type.getName()+"$DeskStep");
        var ctor=step.getDeclaredConstructor(action,String.class,int.class,long.class,String.class);ctor.setAccessible(true);
        @SuppressWarnings({"unchecked","rawtypes"}) Object readAction=Enum.valueOf((Class)action,"READ");
        field(type,"deskStep").set(c,ctor.newInstance(readAction,"",0,0L,"reading your orders"));
        var observe=type.getDeclaredMethod("observeDeskTerminal",Minecraft.class,long.class);observe.setAccessible(true);observe.invoke(c,f.client,1_000_100L);
        var recovery=field(type,"deskRecovery").get(c);var pending=recovery.getClass().getDeclaredMethod("pending");pending.setAccessible(true);
        assertEquals(true,pending.invoke(recovery),"Incomplete native content must schedule the existing paced recovery");
        assertEquals(1_000_000L,field(type,"ownOrdersReadAt").getLong(c),"An incomplete read must not renew freshness");
        assertEquals(rows,field(type,"ownOrders").get(c));
        assertTrue(((Map<?,?>)field(type,"collectedByKey").get(c)).isEmpty());
        assertEquals(1,f.driver.lastOwnReadUnparsedCount());
        String report=c.bugReportContext(1_000_100L);
        assertTrue(report.contains("own_orders_complete=false"));
        assertTrue(report.contains("own_read_unparsed_count=1"));
        assertFalse(report.contains("Unrecognized native control"),"Report evidence is bounded counts, never native tooltip text");
    }

    @Test void completedOrderCollectsItsPhysicalTailDespiteWholeStackPreference() throws Exception {
        physicalCollection(192,17,175,true,17);
    }
    @Test void unfinishedOrderLeavesOnlyAPhysicalPartStackWaitingWithoutBookingCredits() throws Exception {
        physicalCollection(17,17,0,true,0);
    }
    private static void physicalCollection(int delivered,int physical,int preexisting,boolean whole,int expected) throws Exception {
        double mode=Tuning.get("orders.max_per_market"), pace=Tuning.get("pace.human_steps");
        try {
            Tuning.set("orders.max_per_market",0);Tuning.set("pace.human_steps",0);
            var f=fixture(false);setOperation(f.driver,"COLLECT_ORDER");
            field(AutomatedExecutionDriver.class,"targetItemId").set(f.driver,"minecraft:stone");
            field(AutomatedExecutionDriver.class,"ownTargetUnitPrice").setLong(f.driver,100);
            field(AutomatedExecutionDriver.class,"ownMatchRequested").setInt(f.driver,192);
            field(AutomatedExecutionDriver.class,"collectWholeStacksOnly").setBoolean(f.driver,whole);
            var row=f.screen.getMenu().slots.get(0).getItem();
            row.set(DataComponents.LORE,new ItemLore(List.of(Component.literal("192 requested"),Component.literal("$100 each"),Component.literal(delivered+"/192 delivered"))));
            read(f);settle(f);read(f);
            assertEquals(3,field(AutomatedExecutionDriver.class,"ownStage").getInt(f.driver),"The actual native reader must select the filled order");
            if(preexisting>0)f.client.player.getInventory().add(new ItemStack(Items.STONE,preexisting));
            field(AutomatedExecutionDriver.class,"inventoryCountBefore").setInt(f.driver,preexisting);
            f=collectionMenu(f,"Edit Order",0);
            var button=new ItemStack(Items.CHEST);button.set(DataComponents.CUSTOM_NAME,Component.literal("Collect Items"));
            f.screen.getMenu().slots.get(11).set(button);advance(f);
            assertEquals(5,field(AutomatedExecutionDriver.class,"ownStage").getInt(f.driver));
            f=collectionMenu(f,"Orders -> Collect Items",physical);
            ((RecordingGameMode)f.client.gameMode).moveStacks=true;
            for(int i=0;i<6&&f.driver.lastTerminalEvent().outcome()==AutomatedExecutionDriver.TerminalOutcome.NONE;i++)advance(f);
            assertEquals(preexisting+expected,AutomatedExecutionDriver.inventoryCount(f.client,"minecraft:stone"));
            if(expected>0){
                assertEquals(AutomatedExecutionDriver.TerminalOutcome.SUCCEEDED,f.driver.lastTerminalEvent().outcome());
                assertEquals(expected,f.driver.lastTerminalEvent().itemCount(),"Only newly transferred physical stock may be booked");
            } else {
                assertEquals(0,f.driver.lastTerminalEvent().itemCount());
                assertTrue(f.driver.lastTerminalEvent().detail().contains(AutomatedExecutionDriver.PART_STACK_WAITING));
                assertEquals(physical,f.screen.getMenu().slots.get(0).getItem().getCount());
            }
        }finally{Tuning.set("orders.max_per_market",mode);Tuning.set("pace.human_steps",pace);ServerStrain.reset();}
    }
    private static Fixture collectionMenu(Fixture f,String title,int units)throws Exception{
        var contents=new SimpleContainer(54);
        for(int i=0;units>0;i++){int n=Math.min(64,units);contents.setItem(i,new ItemStack(Items.STONE,n));units-=n;}
        var menu=new ChestMenu(MenuType.GENERIC_9x6,f.client.player.containerMenu.containerId+1,f.client.player.getInventory(),contents,6);
        var screen=allocate(ContainerScreen.class);field(AbstractContainerScreen.class,"menu").set(screen,menu);
        field(Screen.class,"title").set(screen,Component.literal(title));f.client.player.containerMenu=menu;
        field(Gui.class,"screen").set(f.client.gui,screen);return new Fixture(f.driver,f.client,screen);
    }
    private static void advance(Fixture f)throws Exception{
        f.client.player.tickCount+=100;field(AutomatedExecutionDriver.class,"ticksInPhase").setInt(f.driver,100);
        field(AutomatedExecutionDriver.class,"ownSettleUntilTick").setInt(f.driver,0);read(f);
    }
    @Test void fourLostEditOrderMenusMustNotPermanentlySuppressAnAlreadyCompletedOrder() throws Exception {
        missingCollectionNavigation(3,"Loading order...","Edit Order did not open",true);
    }
    @Test void fourLostCollectItemsMenusMustNotPermanentlySuppressAnAlreadyCompletedOrder() throws Exception {
        missingCollectionNavigation(5,"Loading order...","Collect Items did not open",true);
    }
    @SuppressWarnings({"unchecked","rawtypes"})
    @Test void aMatchingOrderAtANewOrdinalMustNotBeReportedAsAbsent() throws Exception {
        double mode=Tuning.get("orders.max_per_market"), pace=Tuning.get("pace.human_steps");
        try {
            Tuning.set("orders.max_per_market",3);Tuning.set("pace.human_steps",0);
            var f=fixture(false);setOperation(f.driver,"COLLECT_ORDER");
            field(AutomatedExecutionDriver.class,"targetItemId").set(f.driver,"minecraft:stone");
            field(AutomatedExecutionDriver.class,"ownTargetUnitPrice").setLong(f.driver,100);
            field(AutomatedExecutionDriver.class,"ownMatchRequested").setInt(f.driver,192);
            // The older matching row left the book after planning. The owned row
            // still exists, but is now ordinal zero rather than ordinal one.
            field(AutomatedExecutionDriver.class,"ownMatchOrdinal").setInt(f.driver,1);
            read(f);settle(f);
            field(AutomatedExecutionDriver.class,"ticksInPhase").setInt(f.driver,200);read(f);
            var terminal=f.driver.lastTerminalEvent();
            assertTrue(terminal.detail().contains("Owned order rows changed before collection"),terminal.detail());
            assertFalse(terminal.detail().contains("not on Your Orders"));
            assertEquals(0,terminal.itemCount());
            assertTrue(((RecordingGameMode)f.client.gameMode).slots.isEmpty(),"A stale ordinal must refresh before any stock click");
            var c=new dev.doughbay.fabric.automation.AutomationSessionController(f.driver);
            Class<?> type=c.getClass(),action=Class.forName(type.getName()+"$DeskAction"),step=Class.forName(type.getName()+"$DeskStep");
            var ctor=step.getDeclaredConstructor(action,String.class,int.class,long.class,String.class);ctor.setAccessible(true);
            field(type,"deskStep").set(c,ctor.newInstance(Enum.valueOf((Class)action,"COLLECT"),"minecraft:stone",192,100L,"collecting owned order"));
            var observe=type.getDeclaredMethod("observeDeskTerminal",Minecraft.class,long.class);observe.setAccessible(true);observe.invoke(c,f.client,1_000_000L);
            assertTrue(((Map<?,?>)field(type,"collectAttempts").get(c)).isEmpty(),"A moved row cannot exhaust physical stock retries");
            assertTrue(((Map<?,?>)field(type,"collectedByKey").get(c)).isEmpty(),"Refreshing a row cannot create collection credits");
            Object recovery=field(type,"deskRecovery").get(c);
            var pending=recovery.getClass().getDeclaredMethod("pending");pending.setAccessible(true);
            assertEquals(true,pending.invoke(recovery),"Use the existing paced recovery read before replanning");
        } finally {Tuning.set("orders.max_per_market",mode);Tuning.set("pace.human_steps",pace);ServerStrain.reset();}
    }
    @Test void anActuallyMissingOrderMustNotBeReclassifiedAsAMovedRow() throws Exception {
        var f=fixture(false);setOperation(f.driver,"COLLECT_ORDER");
        field(AutomatedExecutionDriver.class,"targetItemId").set(f.driver,"minecraft:diamond");
        field(AutomatedExecutionDriver.class,"ownTargetUnitPrice").setLong(f.driver,100);
        field(AutomatedExecutionDriver.class,"ownMatchRequested").setInt(f.driver,192);
        read(f);settle(f);field(AutomatedExecutionDriver.class,"ticksInPhase").setInt(f.driver,200);read(f);
        assertTrue(f.driver.lastTerminalEvent().detail().contains("not on Your Orders"));
        assertTrue(((RecordingGameMode)f.client.gameMode).slots.isEmpty());ServerStrain.reset();
    }
    @Test void actuallyReachedEmptyStockMenusStillConsumeTheBoundedPhysicalAttemptBudget() throws Exception {
        missingCollectionNavigation(5,"Orders -> Collect Items","Nothing arrived from the order",false);
    }
    @SuppressWarnings({"unchecked","rawtypes"})
    @Test void successfulBookReadsMustNotEraseRepeatedCollectMenuBackoff() throws Exception {
        double bid=Tuning.get("orders.bid"),reserve=Tuning.get("orders.reserve_stock");
        try {
            Tuning.set("orders.bid",0);Tuning.set("orders.reserve_stock",0);
            var f=fixture(false);var driver=f.driver;
            var controller=new dev.doughbay.fabric.automation.AutomationSessionController(driver);
            var type=controller.getClass();
            field(type,"policy").set(controller,dev.doughbay.core.automation.ContinuousAutomationPolicy.fromConfigValues(2,100_000,50_000,5_000,12,80,10,8,120,1));
            Class<?> action=Class.forName(type.getName()+"$DeskAction"),step=Class.forName(type.getName()+"$DeskStep");
            var ctor=step.getDeclaredConstructor(action,String.class,int.class,long.class,String.class);ctor.setAccessible(true);
            var observe=type.getDeclaredMethod("observeDeskTerminal",Minecraft.class,long.class);observe.setAccessible(true);
            for(int i=0;i<3;i++) {
                long at=1_000_000L+i*300_000L;
                setOperation(driver,"COLLECT_ORDER");field(AutomatedExecutionDriver.class,"targetItemId").set(driver,"minecraft:stone");
                field(AutomatedExecutionDriver.class,"ownStage").setInt(driver,5);
                f=collectionMenu(f,"Orders -> Edit Order",0);
                field(AutomatedExecutionDriver.class,"ticksInPhase").setInt(driver,200);
                field(AutomatedExecutionDriver.class,"ownSettleUntilTick").setInt(driver,0);read(f);
                field(type,"deskStep").set(controller,ctor.newInstance(Enum.valueOf((Class)action,"COLLECT"),"minecraft:stone",192,100L,"collecting owned goods"));
                observe.invoke(controller,f.client,at);
                Object recovery=field(type,"deskRecovery").get(controller);
                var remaining=recovery.getClass().getDeclaredMethod("remainingSeconds",long.class);remaining.setAccessible(true);
                assertEquals(new long[]{5,15,60}[i],remaining.invoke(recovery,at),
                        "Reading the book does not prove the still-failing stock menu recovered");
                var book=fixture(false);f=new Fixture(driver,book.client,book.screen);
                setOperation(driver,"OWN_ORDERS");field(AutomatedExecutionDriver.class,"ownStage").setInt(driver,1);
                field(OwnOrderPages.class,"candidateAt").setLong(field(AutomatedExecutionDriver.class,"ownPages").get(driver),0);
                var reset=OwnOrderPages.class.getDeclaredMethod("reset");reset.setAccessible(true);reset.invoke(field(AutomatedExecutionDriver.class,"ownPages").get(driver));
                field(AutomatedExecutionDriver.class,"ownPageAccepted").setBoolean(driver,false);
                field(AutomatedExecutionDriver.class,"ownOrdersArrivalNoted").setBoolean(driver,true);
                read(f);settle(f);read(f);
                assertEquals(AutomatedExecutionDriver.TerminalOutcome.SUCCEEDED,driver.lastTerminalEvent().outcome());
                field(type,"deskStep").set(controller,ctor.newInstance(Enum.valueOf((Class)action,"READ"),"",0,0L,"refreshing Your Orders"));
                observe.invoke(controller,f.client,at+250_000L);
            }
            assertTrue(((Map<?,?>)field(type,"collectAttempts").get(controller)).isEmpty(),"Navigation must not consume physical-stock attempts");
            assertTrue(((Map<?,?>)field(type,"collectedByKey").get(controller)).isEmpty(),"No inventory transfer means no credits");
        } finally {Tuning.set("orders.bid",bid);Tuning.set("orders.reserve_stock",reserve);ServerStrain.reset();}
    }
    @Test void missingCollectMenuOnFirstDuplicateMustNotStarveDeliveredSecondRow() throws Exception {
        double mode=Tuning.get("orders.max_per_market"),pace=Tuning.get("pace.human_steps");
        try {
            Tuning.set("orders.max_per_market",3);Tuning.set("pace.human_steps",0);
            var f=fixture(false);setOperation(f.driver,"COLLECT_ORDER");
            field(AutomatedExecutionDriver.class,"targetItemId").set(f.driver,"minecraft:stone");
            field(AutomatedExecutionDriver.class,"ownTargetUnitPrice").setLong(f.driver,100);
            field(AutomatedExecutionDriver.class,"ownMatchRequested").setInt(f.driver,384);
            field(AutomatedExecutionDriver.class,"collectWholeStacksOnly").setBoolean(f.driver,true);
            var book=f.screen;
            for(int i=0;i<45;i++)book.getMenu().slots.get(i).set(ItemStack.EMPTY);
            for(int i=0;i<2;i++) {
                var row=new ItemStack(Items.STONE);
                row.set(DataComponents.LORE,new ItemLore(List.of(Component.literal("384 requested"),
                        Component.literal("$100 each"),Component.literal((i==0?0:374)+"/384 delivered"))));
                book.getMenu().slots.get(i).set(row);
            }
            read(f);settle(f);advance(f);
            assertEquals(List.of(0),((RecordingGameMode)f.client.gameMode).slots);
            f=collectionMenu(f,"Orders -> Edit Order",0);
            var button=new ItemStack(Items.CHEST);button.set(DataComponents.CUSTOM_NAME,Component.literal("Collect Items"));
            f.screen.getMenu().slots.get(11).set(button);advance(f);
            field(AutomatedExecutionDriver.class,"ticksInPhase").setInt(f.driver,200);
            field(AutomatedExecutionDriver.class,"ownSettleUntilTick").setInt(f.driver,0);
            f.client.player.tickCount+=100;
            read(f); // The first row never opens its physical stock menu.
            assertEquals(AutomatedExecutionDriver.TerminalOutcome.NONE,f.driver.lastTerminalEvent().outcome(),
                    "A zero-click menu timeout must inspect the other matching owned row before terminating");
            assertEquals(1,field(AutomatedExecutionDriver.class,"ownMatchOrdinal").getInt(f.driver));
            assertEquals(0,AutomatedExecutionDriver.inventoryCount(f.client,"minecraft:stone"));
            field(Gui.class,"screen").set(f.client.gui,book);f.client.player.containerMenu=book.getMenu();
            field(AutomatedExecutionDriver.class,"ownStage").setInt(f.driver,1);
            field(AutomatedExecutionDriver.class,"ownOrdersArrivalNoted").setBoolean(f.driver,true);
            f=new Fixture(f.driver,f.client,book);advance(f);settle(f);advance(f);
            assertEquals(1,((RecordingGameMode)f.client.gameMode).slots.getLast());
            f=collectionMenu(f,"Orders -> Edit Order",0);f.screen.getMenu().slots.get(11).set(button);advance(f);
            f=collectionMenu(f,"Orders -> Collect Items",374);((RecordingGameMode)f.client.gameMode).moveStacks=true;
            for(int i=0;i<12&&f.driver.lastTerminalEvent().outcome()==AutomatedExecutionDriver.TerminalOutcome.NONE;i++)advance(f);
            assertEquals(AutomatedExecutionDriver.TerminalOutcome.SUCCEEDED,f.driver.lastTerminalEvent().outcome());
            assertEquals(320,f.driver.lastTerminalEvent().itemCount(),"Book only physical whole stacks; leave the unfinished54-unit tail");
            assertEquals(320,AutomatedExecutionDriver.inventoryCount(f.client,"minecraft:stone"));
            assertEquals(54,f.screen.getMenu().slots.get(5).getItem().getCount());
        } finally {Tuning.set("orders.max_per_market",mode);Tuning.set("pace.human_steps",pace);ServerStrain.reset();}
    }
    @Test void physicalPartialStockMustSurviveALaterDuplicateWhoseCollectMenuDoesNotOpen() throws Exception {
        double mode=Tuning.get("orders.max_per_market"),pace=Tuning.get("pace.human_steps");
        try {
            Tuning.set("orders.max_per_market",3);Tuning.set("pace.human_steps",0);
            var f=fixture(false);setOperation(f.driver,"COLLECT_ORDER");
            field(AutomatedExecutionDriver.class,"targetItemId").set(f.driver,"minecraft:stone");
            field(AutomatedExecutionDriver.class,"ownTargetUnitPrice").setLong(f.driver,100);
            field(AutomatedExecutionDriver.class,"ownMatchRequested").setInt(f.driver,384);
            field(AutomatedExecutionDriver.class,"ownMatchOrdinal").setInt(f.driver,1);
            field(AutomatedExecutionDriver.class,"collectWholeStacksOnly").setBoolean(f.driver,true);
            var book=f.screen;
            for(int i=0;i<45;i++)book.getMenu().slots.get(i).set(ItemStack.EMPTY);
            for(int i=0;i<2;i++) {
                var row=new ItemStack(Items.STONE);
                row.set(DataComponents.LORE,new ItemLore(List.of(Component.literal("384 requested"),
                        Component.literal("$100 each"),Component.literal((i==0?0:374)+"/384 delivered"))));
                book.getMenu().slots.get(i).set(row);
            }
            read(f);settle(f);advance(f);
            assertEquals(1,((RecordingGameMode)f.client.gameMode).slots.getLast());
            var button=new ItemStack(Items.CHEST);button.set(DataComponents.CUSTOM_NAME,Component.literal("Collect Items"));
            f=collectionMenu(f,"Orders -> Edit Order",0);f.screen.getMenu().slots.get(15).set(button);advance(f);
            f=collectionMenu(f,"Orders -> Collect Items",54);advance(f);
            assertEquals(54,f.screen.getMenu().slots.get(0).getItem().getCount(),"Physical unfinished tail must stay on the server");
            assertEquals(0,field(AutomatedExecutionDriver.class,"ownMatchOrdinal").getInt(f.driver));
            field(Gui.class,"screen").set(f.client.gui,book);f.client.player.containerMenu=book.getMenu();
            field(AutomatedExecutionDriver.class,"ownStage").setInt(f.driver,1);
            field(AutomatedExecutionDriver.class,"ownOrdersArrivalNoted").setBoolean(f.driver,true);
            f=new Fixture(f.driver,f.client,book);advance(f);settle(f);advance(f);
            f=collectionMenu(f,"Orders -> Edit Order",0);f.screen.getMenu().slots.get(15).set(button);advance(f);
            f.client.player.tickCount+=100;
            field(AutomatedExecutionDriver.class,"ticksInPhase").setInt(f.driver,200);
            field(AutomatedExecutionDriver.class,"ownSettleUntilTick").setInt(f.driver,0);
            read(f); // The empty duplicate leaves Edit Order on screen after Collect.
            assertTrue(f.driver.lastTerminalEvent().detail().contains(AutomatedExecutionDriver.PART_STACK_WAITING),
                    "The native54-unit partial-stock result must not be replaced by a duplicate's navigation timeout: "+f.driver.lastTerminalEvent().detail());
            assertEquals(0,f.driver.lastTerminalEvent().itemCount());
            assertEquals(0,AutomatedExecutionDriver.inventoryCount(f.client,"minecraft:stone"));
            assertTrue(f.driver.collectionRowsExhausted());
            var controller=new dev.doughbay.fabric.automation.AutomationSessionController(f.driver);
            var type=controller.getClass();
            field(type,"policy").set(controller,dev.doughbay.core.automation.ContinuousAutomationPolicy.fromConfigValues(2,100_000,50_000,5_000,12,80,10,8,120,1));
            var recovery=field(type,"deskRecovery").get(controller);
            var failed=recovery.getClass().getDeclaredMethod("navigationFailed",long.class);failed.setAccessible(true);failed.invoke(recovery,999_000L);
            Class<?> action=Class.forName(type.getName()+"$DeskAction"),step=Class.forName(type.getName()+"$DeskStep");
            var ctor=step.getDeclaredConstructor(action,String.class,int.class,long.class,String.class);ctor.setAccessible(true);
            @SuppressWarnings({"unchecked","rawtypes"}) Object collect=Enum.valueOf((Class)action,"COLLECT");
            field(type,"deskStep").set(controller,ctor.newInstance(collect,"minecraft:stone",384,100L,"collecting owned stock"));
            var observe=type.getDeclaredMethod("observeDeskTerminal",Minecraft.class,long.class);observe.setAccessible(true);observe.invoke(controller,f.client,1_000_000L);
            var pending=recovery.getClass().getDeclaredMethod("pending");pending.setAccessible(true);
            assertEquals(false,pending.invoke(recovery),"Native partial stock must leave no global navigation hold");
            assertEquals(1_000_000L,((Map<?,?>)field(type,"partStackAt").get(controller)).get("minecraft:stone|100|384"));
            assertTrue(((Map<?,?>)field(type,"collectedByKey").get(controller)).isEmpty(),"No physical transfer must create no collected credits");
        }finally{Tuning.set("orders.max_per_market",mode);Tuning.set("pace.human_steps",pace);ServerStrain.reset();}
    }
    @SuppressWarnings({"unchecked","rawtypes"})
    private static void missingCollectionNavigation(int stage,String title,String expectedFailure,boolean navigation) throws Exception {
        Map<String,Double> before=new HashMap<>();
        for(String k:List.of("orders.max_per_market","orders.bid","orders.collect","orders.reserve_stock","pace.human_steps"))before.put(k,Tuning.get(k));
        try {
            Tuning.set("orders.max_per_market",0);Tuning.set("orders.bid",0);Tuning.set("orders.collect",1);Tuning.set("orders.reserve_stock",0);Tuning.set("pace.human_steps",0);
            var f=fixture(false);var c=new dev.doughbay.fabric.automation.AutomationSessionController(f.driver);
            var type=c.getClass();field(type,"policy").set(c,dev.doughbay.core.automation.ContinuousAutomationPolicy.fromConfigValues(2,100_000,50_000,5_000,12,80,10,8,120,1));
            var rows=List.of(new AutomatedExecutionDriver.OwnOrderRow("minecraft:stone","minecraft:stone",192,100,192,0,false));
            field(type,"ownOrders").set(c,rows);field(type,"ownOrdersComplete").setBoolean(c,true);field(type,"ownOrdersReadAt").setLong(c,1_000_000);
            Class<?> action=Class.forName(type.getName()+"$DeskAction"),step=Class.forName(type.getName()+"$DeskStep");
            var ctor=step.getDeclaredConstructor(action,String.class,int.class,long.class,String.class);ctor.setAccessible(true);
            var observe=type.getDeclaredMethod("observeDeskTerminal",Minecraft.class,long.class);observe.setAccessible(true);
            for(int i=0;i<4;i++){
                setOperation(f.driver,"COLLECT_ORDER");field(AutomatedExecutionDriver.class,"targetItemId").set(f.driver,"minecraft:stone");
                field(AutomatedExecutionDriver.class,"ownStage").setInt(f.driver,stage);
                f=collectionMenu(f,title,0);
                field(AutomatedExecutionDriver.class,"ticksInPhase").setInt(f.driver,200);field(AutomatedExecutionDriver.class,"ownSettleUntilTick").setInt(f.driver,0);
                read(f);assertTrue(f.driver.lastTerminalEvent().detail().contains(expectedFailure),f.driver.lastTerminalEvent().detail());
                assertEquals(0,f.driver.lastTerminalEvent().itemCount());
                field(type,"deskStep").set(c,ctor.newInstance(Enum.valueOf((Class)action,"COLLECT"),"minecraft:stone",192,100L,"collecting completed order"));
                observe.invoke(c,f.client,1_000_000L+i*300_000L);
            }
            var attempts=(Map<String,Integer>)field(type,"collectAttempts").get(c);
            assertEquals(navigation?null:Integer.valueOf(4),attempts.get("minecraft:stone|100|192"),"Only an actually reached stock page may spend the physical collection budget");
            field(type,"ownOrdersReadAt").setLong(c,3_000_000);
            var plan=type.getDeclaredMethod("planDesk",MarketWatcher.Snapshot.class,long.class);plan.setAccessible(true);plan.invoke(c,null,3_000_000L);
            assertEquals(navigation,((Deque<?>)field(type,"deskQueue").get(c)).stream().anyMatch(x->x.toString().contains("COLLECT")),"Unchanged completed goods recover after navigation failure, while genuinely empty stock remains bounded");
            assertTrue(((Map<?,?>)field(type,"collectedByKey").get(c)).isEmpty(),"No menu or inventory transfer may fabricate collection credits");
            assertTrue(((RecordingGameMode)f.client.gameMode).slots.isEmpty());
        }finally{before.forEach(Tuning::set);ServerStrain.reset();}
    }
    private static void plan(Object controller,AutomatedExecutionDriver driver,long at)throws Exception{
        Class<?> type=controller.getClass();field(type,"ownOrders").set(controller,driver.lastOwnOrders());
        field(type,"ownOrdersComplete").setBoolean(controller,driver.lastOwnOrdersComplete());field(type,"ownOrdersReadAt").setLong(controller,at);
        var method=type.getDeclaredMethod("planDesk",MarketWatcher.Snapshot.class,long.class);method.setAccessible(true);method.invoke(controller,null,at);
    }

    private static void settle(Fixture f)throws Exception{
        field(OwnOrderPages.class,"candidateAt").setLong(field(AutomatedExecutionDriver.class,"ownPages").get(f.driver),System.currentTimeMillis()-1000);
    }
    private static Fixture secondPage(Fixture f)throws Exception{
        var next=fixture(false);var menu=next.screen.getMenu();
        field(net.minecraft.world.inventory.AbstractContainerMenu.class,"containerId").setInt(menu,6);
        for(int i=0;i<45;i++)menu.slots.get(i).set(ItemStack.EMPTY);
        var stack=new ItemStack(Items.DIAMOND);
        stack.set(DataComponents.LORE,new ItemLore(List.of(Component.literal("192 requested"),Component.literal("$200 each"),Component.literal("64/192 delivered"))));
        menu.slots.get(0).set(stack);f.client.player.containerMenu=menu;
        field(Gui.class,"screen").set(f.client.gui,next.screen);
        // The native page has arrived after the driver's human-step settle interval.
        field(AutomatedExecutionDriver.class,"ticksInPhase").setInt(f.driver,100);
        field(AutomatedExecutionDriver.class,"ownSettleUntilTick").setInt(f.driver,0);
        return new Fixture(f.driver,f.client,next.screen);
    }

    private record Fixture(AutomatedExecutionDriver driver,Minecraft client,ContainerScreen screen) {}
    @SuppressWarnings({"unchecked","rawtypes"}) private static Fixture fixture(boolean next) throws Exception {
        Minecraft client=allocate(Minecraft.class);QuietPlayer player=allocate(QuietPlayer.class);
        Inventory inventory=new Inventory(player,new EntityEquipment());
        field(Player.class,"inventory").set(player,inventory);client.player=player;
        field(Player.class,"abilities").set(player,new net.minecraft.world.entity.player.Abilities());
        var level=allocate(net.minecraft.client.multiplayer.ClientLevel.class);
        var connection=allocate(net.minecraft.client.multiplayer.ClientPacketListener.class);
        field(net.minecraft.client.multiplayer.ClientPacketListener.class,"enabledFeatures").set(connection,net.minecraft.world.flag.FeatureFlags.VANILLA_SET);
        field(net.minecraft.client.multiplayer.ClientLevel.class,"connection").set(level,connection);
        field(net.minecraft.world.entity.Entity.class,"level").set(player,level);
        field(Minecraft.class,"options").set(client,allocate(Options.class));
        var contents=new SimpleContainer(54);
        for(int i=0;i<45;i++){
            var stack=new ItemStack(Items.STONE);
            stack.set(DataComponents.LORE,new ItemLore(List.of(Component.literal("192 requested"),
                    Component.literal("$"+(100+i)+" each"),Component.literal("64/192 delivered"))));
            assertTrue(Screen.getTooltipFromItem(client,stack).stream().anyMatch(c->c.getString().contains("192 requested")),"Native fixture tooltip must be readable");
            contents.setItem(i,stack);
        }
        var control=new ItemStack(Items.PAPER);control.set(DataComponents.CUSTOM_NAME,Component.literal(next?"Next Page":"Close"));
        contents.setItem(53,control);
        var menu=new ChestMenu(MenuType.GENERIC_9x6,5,inventory,contents,6);player.containerMenu=menu;
        ContainerScreen screen=allocate(ContainerScreen.class);
        field(AbstractContainerScreen.class,"menu").set(screen,menu);
        field(Screen.class,"title").set(screen,Component.literal("Your Orders"));
        Gui gui=allocate(Gui.class);field(Gui.class,"screen").set(gui,screen);field(Minecraft.class,"gui").set(client,gui);
        client.gameMode=allocate(RecordingGameMode.class);((RecordingGameMode)client.gameMode).slots=new ArrayList<>();
        player.tickCount=100;
        var driver=new AutomatedExecutionDriver();
        setOperation(driver,"OWN_ORDERS");
        field(AutomatedExecutionDriver.class,"ownStage").setInt(driver,1);
        field(AutomatedExecutionDriver.class,"ownOrdersArrivalNoted").setBoolean(driver,true);
        return new Fixture(driver,client,screen);
    }
    private static void read(Fixture f) throws Exception {
        var m=AutomatedExecutionDriver.class.getDeclaredMethod("tickOwnOrdersPage",Minecraft.class,AbstractContainerScreen.class);
        m.setAccessible(true);m.invoke(f.driver,f.client,f.screen);
    }
    private static Field field(Class<?> type,String name) throws Exception {var f=type.getDeclaredField(name);f.setAccessible(true);return f;}
    @SuppressWarnings({"unchecked","rawtypes"})private static void setOperation(AutomatedExecutionDriver driver,String name)throws Exception{Field operation=field(AutomatedExecutionDriver.class,"operation");operation.set(driver,Enum.valueOf((Class)operation.getType(),name));}
    private static <T>T allocate(Class<T> type)throws Exception{return type.cast(((Unsafe)field(Unsafe.class,"theUnsafe").get(null)).allocateInstance(type));}
    private static class QuietPlayer extends LocalPlayer {
        private QuietPlayer(){super(null,null,null,null,null,null,false,null);}
        @Override public void sendSystemMessage(Component message){}
        @Override public net.minecraft.server.permissions.PermissionSet permissions(){return net.minecraft.server.permissions.PermissionSet.NO_PERMISSIONS;}
        @Override public void closeContainer(){}
    }
    private static class RecordingGameMode extends net.minecraft.client.multiplayer.MultiPlayerGameMode{
        List<Integer> slots; boolean moveStacks;
        private RecordingGameMode(){super(null,null);}
        @Override public void handleContainerInput(int window,int slot,int button,net.minecraft.world.inventory.ContainerInput input,Player player){slots.add(slot);if(moveStacks&&input==net.minecraft.world.inventory.ContainerInput.QUICK_MOVE){
            var stack=player.containerMenu.slots.get(slot).getItem();var remaining=stack.copy();player.getInventory().add(remaining);player.containerMenu.slots.get(slot).set(remaining);
        }}
    }
}
