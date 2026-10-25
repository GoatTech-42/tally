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
