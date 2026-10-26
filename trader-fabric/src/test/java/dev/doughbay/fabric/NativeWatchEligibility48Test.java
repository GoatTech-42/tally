package dev.doughbay.fabric;

import java.lang.reflect.*;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/** Report48 investigation: drive the production selector against native auction rows.
 * The recording fixture never sends clicks or purchases to a server. */
class NativeWatchEligibility48Test {
    @BeforeAll static void bootstrap(){NativeRecoveryNavigation209Test.bootstrap();}
    @Test void qualifiedPlainStackReachesConfirmationWithoutClaimingPurchase()throws Exception{
        var f=fixture();row(f,0,64,"$900","");read(f);
        assertEquals(List.of(0),f.clicks());
        assertEquals("WAITING_FOR_CONFIRMATION",((Enum<?>)get(f.d,"phase")).name());
        assertEquals("minecraft:diamond",get(f.d,"targetItemId"));assertEquals(64,get(f.d,"targetCount"));
        assertEquals(0,f.d.lastTerminalEvent().sequence(),"Clicking a row is not confirmed buying");
    }
    @Test void aRefusedFirstRowDoesNotHideALaterQualifiedRow()throws Exception{
        var f=fixture();row(f,0,64,"$1100","");row(f,1,64,"$900","");read(f);
        assertEquals(List.of(1),f.clicks());
    }
    @Test void priceQuantityAndManagedSellerGuardsNeverBecomePurchases()throws Exception{
        for(int variant=0;variant<4;variant++){
            var f=fixture();row(f,0,variant==1?32:64,variant==0?"$1100":variant==2?"not a price":"$900",variant==3?"OfflineFixture":"");
            set(f.d,"ticksInPhase",10000);read(f);
            assertTrue(f.clicks().isEmpty(),"Unsafe row variant "+variant+" must not be clicked");
            assertEquals(AutomatedExecutionDriver.TerminalOutcome.ABORTED,f.d.lastTerminalEvent().outcome());
            assertFalse(f.d.lastTerminalEvent().confirmationSubmitted());
            assertTrue(f.d.lastTerminalEvent().detail().contains("nothing was charged"));
        }
    }
    private static NativeRecoveryNavigation209Test.Fixture fixture()throws Exception{
        var f=new NativeRecoveryNavigation209Test.Fixture();f.page("Auction (Page 1)",false);
        var op=field(f.d,"operation");@SuppressWarnings({"unchecked","rawtypes"}) var buy=Enum.valueOf((Class)op.getType(),"BUY");op.set(f.d,buy);
        set(f.d,"watching",true);set(f.d,"watchTargets",List.of(new AutomatedExecutionDriver.WatchTarget("minecraft:diamond",64,1000)));
        return f;
    }
    private static void row(NativeRecoveryNavigation209Test.Fixture f,int slot,int count,String price,String seller){
        var stack=new ItemStack(Items.DIAMOND,count);var lore=new java.util.ArrayList<Component>();
        lore.add(Component.literal("Price: "+price));if(!seller.isEmpty())lore.add(Component.literal("Seller: "+seller));
        stack.set(DataComponents.LORE,new ItemLore(lore));f.screen.getMenu().slots.get(slot).set(stack);
    }
    private static void read(NativeRecoveryNavigation209Test.Fixture f)throws Exception{
        var m=f.d.getClass().getDeclaredMethod("tickWatchPage",Minecraft.class,AbstractContainerScreen.class);m.setAccessible(true);m.invoke(f.d,f.mc,f.screen);
    }
    private static Field field(Object o,String n)throws Exception{var f=o.getClass().getDeclaredField(n);f.setAccessible(true);return f;}
    private static Object get(Object o,String n)throws Exception{return field(o,n).get(o);}
    private static void set(Object o,String n,Object v)throws Exception{field(o,n).set(o,v);}
}
