package dev.doughbay.fabric;

import dev.doughbay.core.analysis.RiskConfig;
import dev.doughbay.core.automation.ContinuousAutomationPolicy;
import dev.doughbay.core.model.*;
import dev.doughbay.fabric.automation.AutomationSessionController;
import java.lang.reflect.*;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class NormalMultipleOrderPlannerTest {
    @TempDir Path directory;
    static final String ITEM="minecraft:stone";
    @BeforeAll static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
    }
    @Test void normalModeCanPlaceSecondOrderAtOurPriceButNotThirdOrWhileOutbid() throws Exception {
        String before=SettingsCode.exportPreset(Map.of(),Map.of());boolean keyless=KeylessFeed.active();
        Tuning.load(directory);
        try {
            for(var entry:Map.ofEntries(Map.entry("orders.bid",1.0),Map.entry("orders.reserve_stock",0.0),
                    Map.entry("orders.max_per_market",2.0),Map.entry("orders.bid_stacks",16.0),
                    Map.entry("orders.bid_slots",90.0),Map.entry("orders.size_by_sale",0.0),
                    Map.entry("orders.bid_min_confidence",0.0),Map.entry("orders.bid_min_samples",0.0),
                    Map.entry("orders.bid_on_real_trades",0.0),Map.entry("orders.bid_min_value",0.0),
                    Map.entry("orders.bid_max_value",0.0),Map.entry("orders.depth_share_pct",100.0),
                    Map.entry("orders.bid_min_room",0.0),Map.entry("list.singles",0.0),
                    Map.entry("prices.only",0.0),Map.entry("buy.focus",0.0),Map.entry("underdog.enabled",0.0),
                    Map.entry("hive.split_markets",0.0),Map.entry("session.wind_down",0.0)).entrySet())
                Tuning.set(entry.getKey(),entry.getValue());
            Tuning.setText("items.allow","=stone");Tuning.setText("items.deny","");
            Tuning.setText("items.liquidate","");Tuning.setText("prices.own","");KeylessFeed.setActive(false);
            long now=System.currentTimeMillis();
            var market=new MarketStats(ITEM,StackBucket.X64,86400000,100,0,4,now,
                    12800,12800,12800,12800,12800,100,0.02,0.01,0.9,now);
            var snapshot=new MarketWatcher.Snapshot(List.of(),List.of(),List.of(market),List.of(),
                    Map.of(),Map.of(),null,"",1_000_000_000,"test",null,now,now,false);
            var controller=new AutomationSessionController(new AutomatedExecutionDriver());
            set(controller,"policy",ContinuousAutomationPolicy.fromConfigValues(100,75_000_000,15_000_000,350,2,10,2,8,240,86));
            set(controller,"riskConfig",new RiskConfig(3,350,2,10,25,90,true,true,1,86400000L,24,0.1,1));
            var row=new AutomatedExecutionDriver.OwnOrderRow(ITEM,ITEM,960,100,0,7*86400000L);
            set(controller,"ownOrders",List.of(row));set(controller,"ownOrdersReadAt",now);
            set(controller,"ownOrdersComplete",true);
            @SuppressWarnings("unchecked") var quotes=(Map<String,long[]>)get(controller,"orderQuotes");
            quotes.put(ITEM,new long[]{100,now});
            var second=places(controller,snapshot,now);
            assertEquals(1,second.size(),"Reserve Stock is off; normal mode must actually queue the second order");
            assertEquals(100L,value(second.getFirst(),"unitPrice"),"Do not bid against our first order");
            assertTrue((int)value(second.getFirst(),"count")>0);
            assertTrue(((Collection<?>)get(controller,"deskQueue")).stream().noneMatch(s->{try{return value(s,"action").toString().equals("CANCEL");}catch(Exception e){throw new RuntimeException(e);}}),
                    "A competitive first order must stay open beside the second");
            set(controller,"ownOrders",List.of(row,row));
            assertTrue(places(controller,snapshot,now).isEmpty(),"The third order is blocked");
            set(controller,"ownOrders",List.of(row));quotes.put(ITEM,new long[]{110,now});
            assertTrue(places(controller,snapshot,now).isEmpty(),"Chase the existing order before funding another at a higher price");
            quotes.put(ITEM,new long[]{100,now});set(controller,"ownOrdersComplete",false);
            assertTrue(places(controller,snapshot,now).isEmpty(),"No spending against a partial native read");
            set(controller,"ownOrdersComplete",true);Tuning.set("orders.max_per_market",0);
            assertTrue(places(controller,snapshot,now).isEmpty(),"Auto preserves the previous one-order behavior");
            @SuppressWarnings("unchecked") var refused=(Set<String>)get(controller,"deskUncancellable");
            refused.add(ITEM+"|100|960");Tuning.setText("items.deny","=stone");
            places(controller,snapshot,now);
            for(Object step:(Collection<?>)get(controller,"deskQueue"))
                assertNotEquals("CANCEL",value(step,"action").toString(),"Requested-count identity must retain the cancellation retry limit");
        } finally { KeylessFeed.setActive(keyless);SettingsCode.apply(before); }
    }
    static List<?> places(Object c,MarketWatcher.Snapshot snapshot,long now)throws Exception {
        Method method=c.getClass().getDeclaredMethod("planDesk",MarketWatcher.Snapshot.class,long.class);
        method.setAccessible(true);method.invoke(c,snapshot,now);
        List<Object> result=new ArrayList<>();for(Object step:(Collection<?>)get(c,"deskQueue"))
            if(value(step,"action").toString().equals("PLACE"))result.add(step);
        return result;
    }
    static void set(Object o,String key,Object value)throws Exception {Field f=o.getClass().getDeclaredField(key);f.setAccessible(true);f.set(o,value);}
    static Object get(Object o,String key)throws Exception {Field f=o.getClass().getDeclaredField(key);f.setAccessible(true);return f.get(o);}
    static Object value(Object o,String key)throws Exception {Method m=o.getClass().getDeclaredMethod(key);m.setAccessible(true);return m.invoke(o);}
}
