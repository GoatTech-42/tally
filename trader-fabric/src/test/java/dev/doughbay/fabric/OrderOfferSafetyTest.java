package dev.doughbay.fabric;

import dev.doughbay.core.analysis.RiskConfig;
import dev.doughbay.core.automation.ContinuousAutomationPolicy;
import dev.doughbay.core.model.*;
import dev.doughbay.fabric.automation.AutomationSessionController;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.lang.reflect.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class OrderOfferSafetyTest {
    @TempDir Path directory;
    @BeforeAll static void bootstrap(){net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();}
    @ParameterizedTest @ValueSource(booleans={false,true})
    void safeActualOfferIsNotRejectedBecauseTheUnusedCeilingIsHigher(boolean sizeBySale) throws Exception {
        var queue=plan(2300,sizeBySale);
        Object order=queue.stream().filter(s->value(s,"action").toString().equals("PLACE")).findFirst().orElse(null);
        assertNotNull(order,"A lower actual offer below the traded reference should survive: "+queue);
        assertTrue((long)value(order,"unitPrice")<=2300);
        assertEquals(0,(int)value(order,"count")%64,"ordinary orders remain whole stacks");
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void actualOfferAboveTheTradedReferenceIsStillRefused(boolean sizeBySale) throws Exception {
        assertTrue(plan(1000,sizeBySale).stream().noneMatch(s->value(s,"action").toString().equals("PLACE")));
    }
    @SuppressWarnings("unchecked") private List<?> plan(double traded,boolean sizeBySale) throws Exception {
        String before=SettingsCode.exportPreset(Map.of(),Map.of());boolean active=KeylessFeed.active();
        Field f=KeylessFeed.class.getDeclaredField("traded");f.setAccessible(true);Object prior=f.get(null);
        Tuning.load(directory);
        try {
            for(var e:Map.of("orders.bid",1.0,"orders.bid_stacks",16.0,"orders.depth_share_pct",100.0,
                    "orders.bid_min_samples",1.0,"orders.bid_min_confidence",0.1,"orders.size_by_sale",sizeBySale?1.0:0.0,
                    "list.singles",0.0,"underdog.enabled",0.0,"hive.split_markets",0.0,"buy.focus",0.0).entrySet())Tuning.set(e.getKey(),e.getValue());
            Tuning.set("prices.only",0);Tuning.set("orders.bid_min_value",0);Tuning.set("orders.bid_max_value",0);
            Tuning.set("orders.bid_on_real_trades",0);Tuning.set("session.wind_down",0);Tuning.set("orders.reserve_stock",0);
            Tuning.setText("items.allow","=diamond_block");Tuning.setText("items.deny","");Tuning.setText("items.liquidate","");Tuning.setText("prices.own","");
            KeylessFeed.setActive(true);f.set(null,Map.of("minecraft:diamond_block|n",traded));
            assertEquals(64,net.minecraft.world.item.Items.DIAMOND_BLOCK.getDefaultMaxStackSize(),"fixture registry stack size");
            long now=System.currentTimeMillis();
            var market=new MarketStats("minecraft:diamond_block",StackBucket.X64,7*86400000L,100,0,4,now-3600000,
                    192000,192000,192000,192000,192000,15,0.02,0.19,0.9,now);
            var snapshot=new MarketWatcher.Snapshot(List.of(),List.of(),List.of(market),List.of(),Map.of(),Map.of(),null,"",1000000000,"fixture",null,now,now,false);
            var c=new AutomationSessionController(new AutomatedExecutionDriver());
            set(c,"policy",ContinuousAutomationPolicy.fromConfigValues(100,75000000,15000000,350,2,10,2,8,240,86));
            set(c,"riskConfig",new RiskConfig(3,350,2,10,25,90,true,true,1,86400000L,24,0.1,1));
            ((Map<String,long[]>)get(c,"orderQuotes")).put("minecraft:diamond_block",new long[]{1000,now});
            Method m=c.getClass().getDeclaredMethod("planDesk",MarketWatcher.Snapshot.class,long.class);m.setAccessible(true);m.invoke(c,snapshot,now);
            return new ArrayList<>((Collection<?>)get(c,"deskQueue"));
        }finally{f.set(null,prior);KeylessFeed.setActive(active);SettingsCode.apply(before);}
    }
    private static Object value(Object o,String n){try{Method m=o.getClass().getDeclaredMethod(n);m.setAccessible(true);return m.invoke(o);}catch(Exception e){throw new RuntimeException(e);}}
    private static Object get(Object o,String n)throws Exception{Field f=o.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(o);}
    private static void set(Object o,String n,Object v)throws Exception{Field f=o.getClass().getDeclaredField(n);f.setAccessible(true);f.set(o,v);}
}
