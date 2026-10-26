package dev.doughbay.fabric;

import java.lang.reflect.*;
import java.util.*;
import net.minecraft.SharedConstants;
import net.minecraft.client.*;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.*;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import org.junit.jupiter.api.*;
import sun.misc.Unsafe;
import static org.junit.jupiter.api.Assertions.*;

/** Actual native audit navigation; all packets/clicks stay in this offline fixture. */
class NativeRecoveryNavigation209Test {
    @BeforeAll static void bootstrap(){
        SharedConstants.tryDetectVersion();Bootstrap.bootStrap();
        net.minecraft.core.registries.BuiltInRegistries.DATA_COMPONENT_INITIALIZERS
            .build(net.minecraft.data.registries.VanillaRegistries.createLookup())
            .forEach(net.minecraft.core.component.DataComponentInitializers.PendingComponents::apply);
    }
    @Test void auctionChestThenExplicitVacantOwnPageCompletesReadOnly() throws Exception {
        var f=new Fixture();f.read();
        assertEquals(List.of(49),f.clicks());
        assertEquals(AutomatedExecutionDriver.TerminalOutcome.NONE,f.d.lastTerminalEvent().outcome());
        f.page("Auction -> Your Items",true);f.read();
        assertEquals(AutomatedExecutionDriver.TerminalOutcome.SUCCEEDED,f.d.lastTerminalEvent().outcome());
        assertEquals(AutomatedExecutionDriver.OperationIntent.AUDIT,f.d.lastTerminalEvent().intent());
        assertTrue(f.d.lastOwnListings().isEmpty());assertEquals(0,f.d.lastOwnListingsUnreadable());
        assertEquals(List.of(49),f.clicks(),"Reading a vacant book must not collect or list anything");
    }
    @Test void unacknowledgedChestClickExhaustsBoundedRetriesWithoutReportingEmptyBook() throws Exception {
        var f=new Fixture();f.read();
        for(int i=0;i<3;i++){f.mc.player.tickCount+=100;set(f.d,"ticksInPhase",10000);f.read();}
        assertEquals(3,f.clicks().size(),"One initial navigation click and two retries");
        assertEquals(AutomatedExecutionDriver.TerminalOutcome.ABORTED,f.d.lastTerminalEvent().outcome());
        assertTrue(f.d.lastTerminalEvent().detail().contains("did not open"));
    }
    @Test void unrecognizedOwnPageCannotResolveAnAbsentItem() throws Exception {
        var f=new Fixture();f.read();f.page("Auction -> Your Items",false);set(f.d,"ticksInPhase",10000);f.read();
        assertEquals(AutomatedExecutionDriver.TerminalOutcome.ABORTED,f.d.lastTerminalEvent().outcome());
        assertTrue(f.d.lastTerminalEvent().detail().contains("unrecognized"));
        assertEquals(List.of(49),f.clicks());
    }
    static final class Fixture {
        final AutomatedExecutionDriver d=new AutomatedExecutionDriver();final Minecraft mc;ContainerScreen screen;
        Fixture()throws Exception{
            mc=allocate(Minecraft.class);mc.player=allocate(QuietPlayer.class);
            var inv=new Inventory(mc.player,new EntityEquipment());field(Player.class,"inventory").set(mc.player,inv);
            field(Player.class,"abilities").set(mc.player,new Abilities());
            mc.level=allocate(net.minecraft.client.multiplayer.ClientLevel.class);
            var connection=allocate(net.minecraft.client.multiplayer.ClientPacketListener.class);
            field(connection.getClass(),"enabledFeatures").set(connection,net.minecraft.world.flag.FeatureFlags.VANILLA_SET);
            field(mc.level.getClass(),"connection").set(mc.level,connection);
            field(net.minecraft.world.entity.Entity.class,"level").set(mc.player,mc.level);
            field(Minecraft.class,"options").set(mc,allocate(Options.class));field(Minecraft.class,"gui").set(mc,allocate(Gui.class));
            mc.gameMode=allocate(Clicks.class);((Clicks)mc.gameMode).slots=new ArrayList<>();mc.player.tickCount=100;
            var op=field(d.getClass(),"operation");
            @SuppressWarnings({"rawtypes","unchecked"}) Object value=Enum.valueOf((Class)op.getType(),"AUDIT");op.set(d,value);
            set(d,"auditCollectUnsold",false);page("Auction House",false);
            var chest=new ItemStack(Items.CHEST);chest.set(DataComponents.CUSTOM_NAME,Component.literal("Your Items"));screen.getMenu().slots.get(49).set(chest);
        }
        void page(String title,boolean vacant)throws Exception{
            var contents=new SimpleContainer(54);
            if(vacant)for(int i=0;i<45;i++){
                var pane=new ItemStack(Items.STAINED_GLASS_PANE.gray());pane.set(DataComponents.CUSTOM_NAME,Component.literal("List"));
                pane.set(DataComponents.LORE,new ItemLore(List.of(Component.literal("Click to sell an item"))));contents.setItem(i,pane);
            }
            var menu=new ChestMenu(MenuType.GENERIC_9x6,vacant?6:5,mc.player.getInventory(),contents,6);mc.player.containerMenu=menu;
            screen=allocate(ContainerScreen.class);field(AbstractContainerScreen.class,"menu").set(screen,menu);
            field(Screen.class,"title").set(screen,Component.literal(title));field(Gui.class,"screen").set(mc.gui,screen);
            set(d,"stableTicks",20);
        }
        void read()throws Exception{var m=d.getClass().getDeclaredMethod("tickAuditPage",Minecraft.class,AbstractContainerScreen.class);m.setAccessible(true);m.invoke(d,mc,screen);}
        List<Integer> clicks(){return ((Clicks)mc.gameMode).slots;}
    }
    private static class QuietPlayer extends LocalPlayer {
        private QuietPlayer(){super(null,null,null,null,null,null,false,null);}
        @Override public void sendSystemMessage(Component c){}
        @Override public com.mojang.authlib.GameProfile getGameProfile(){return new com.mojang.authlib.GameProfile(new java.util.UUID(0,1),"OfflineFixture");}
        @Override public net.minecraft.server.permissions.PermissionSet permissions(){return net.minecraft.server.permissions.PermissionSet.NO_PERMISSIONS;}
        @Override public void closeContainer(){}
    }
    private static class Clicks extends net.minecraft.client.multiplayer.MultiPlayerGameMode {
        List<Integer> slots;private Clicks(){super(null,null);}
        @Override public void handleContainerInput(int window,int slot,int button,ContainerInput input,Player player){slots.add(slot);}
    }
    private static Field field(Class<?> c,String n)throws Exception{var f=c.getDeclaredField(n);f.setAccessible(true);return f;}
    private static void set(Object o,String n,Object v)throws Exception{field(o.getClass(),n).set(o,v);}
    private static <T>T allocate(Class<T> c)throws Exception{return c.cast(((Unsafe)field(Unsafe.class,"theUnsafe").get(null)).allocateInstance(c));}
}
