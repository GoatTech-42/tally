package dev.doughbay.fabric.automation;

import dev.doughbay.fabric.*;
import dev.doughbay.core.automation.ContinuousAutomationPolicy;
import java.util.*;
import java.lang.reflect.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/** Production bid planning only: records queued intents and never dispatches them to a server. */
class ItemPriorityBid70Test {
    private final Map<String,String> texts=new HashMap<>();private final Map<String,Double> numbers=new HashMap<>();
    @BeforeAll static void bootstrap(){net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();}
    @BeforeEach void setup(){
        for(String k:List.of("prices.own",ItemPriorities.KEY,"items.allow","items.deny","items.liquidate")){texts.put(k,Tuning.text(k));Tuning.setText(k,"");}
        for(String k:List.of("buy.focus","prices.only","orders.bid","orders.max_per_market","orders.bid_slots"))numbers.put(k,Tuning.get(k));
        Tuning.set("buy.focus",0);Tuning.set("prices.only",1);Tuning.set("orders.bid",1);Tuning.set("orders.max_per_market",2);Tuning.set("orders.bid_slots",2);
    }
    @AfterEach void restore(){texts.forEach(Tuning::setText);numbers.forEach(Tuning::set);}
    private static AutomationSessionController controller()throws Exception{
        var c=new AutomationSessionController(new AutomatedExecutionDriver());set(c,"policy",ContinuousAutomationPolicy.fromConfigValues(20,100000,50000,5000,12,80,10,8,120,1));return c;
    }
    @Test void explicitPriorityOrdersActualNewBidsInEitherDirection()throws Exception{
        Tuning.setText("prices.own","diamond 100 200 x1, emerald 100 200 x1");
        for(String high:List.of("diamond","emerald")){
            String low=high.equals("diamond")?"emerald":"diamond";Tuning.setText(ItemPriorities.KEY,high+"=high,"+low+"=low");
            var c=controller();planOwn(c);assertEquals(List.of("minecraft:"+high),placements(c));
        }
    }
    @Test void excludedOrOverCapHighStillAllowsEligibleLowBid()throws Exception{
        Tuning.setText(ItemPriorities.KEY,"diamond=low,emerald=high");
        for(boolean excluded:List.of(true,false)){
            Tuning.setText("items.deny",excluded?"=emerald":"");Tuning.setText("prices.own",excluded?"diamond 100 200 x1, emerald 100 200 x1":"diamond 100 200 x1, emerald 100000 200000 x1");
            var c=controller();planOwn(c);assertEquals(List.of("minecraft:diamond"),placements(c),"Priority cannot override item/capital gates");
        }
    }
    @Test void missingHighQuoteDoesNotReserveTheOnlySlotAgainstAnEligibleLow()throws Exception{
        Tuning.setText(ItemPriorities.KEY,"diamond=low,emerald=high");Tuning.setText("prices.own","diamond 100 200 x1, emerald top 100 200 x1");var c=controller();planOwn(c);assertEquals(List.of("minecraft:diamond"),placements(c));
    }
    @Test void completeNativeBookIsStillRequiredBeforeAnyPrioritizedBid()throws Exception{
        Tuning.setText(ItemPriorities.KEY,"diamond=low,emerald=high");Tuning.setText("prices.own","diamond 100 200 x1, emerald 100 200 x1");var c=controller();long now=System.currentTimeMillis();set(c,"ownOrdersReadAt",now);var snap=new MarketWatcher.Snapshot(List.of(),List.of(),List.of(),List.of(),Map.of(),Map.of(),null,"",-1,"OK",null,now,now,false);
        var m=c.getClass().getDeclaredMethod("planDesk",MarketWatcher.Snapshot.class,long.class);m.setAccessible(true);set(c,"ownOrdersComplete",false);m.invoke(c,snap,now);assertTrue(placements(c).isEmpty());
        set(c,"ownOrdersComplete",true);m.invoke(c,snap,now);assertFalse(placements(c).isEmpty());assertEquals("minecraft:emerald",placements(c).getFirst());
    }
    private static void planOwn(AutomationSessionController c)throws Exception{
        var m=c.getClass().getDeclaredMethod("planOwnPriceOrders",MarketWatcher.Snapshot.class,long.class,Set.class,Set.class,int.class,long.class,int.class,Map.class);m.setAccessible(true);m.invoke(c,null,System.currentTimeMillis(),new HashSet<String>(),new HashSet<String>(),1,50000L,1,Map.of());
    }
    private static List<String> placements(AutomationSessionController c)throws Exception{
        var f=c.getClass().getDeclaredField("deskQueue");f.setAccessible(true);List<String> out=new ArrayList<>();for(Object step:(Deque<?>)f.get(c)){var action=step.getClass().getDeclaredMethod("action");action.setAccessible(true);if(!((Enum<?>)action.invoke(step)).name().equals("PLACE"))continue;var item=step.getClass().getDeclaredMethod("itemId");item.setAccessible(true);out.add((String)item.invoke(step));}return out;
    }
    private static void set(Object o,String n,Object value)throws Exception{var f=o.getClass().getDeclaredField(n);f.setAccessible(true);f.set(o,value);}
}
