package dev.doughbay.fabric.automation;

import dev.doughbay.fabric.AutomatedExecutionDriver;
import dev.doughbay.fabric.MarketWatcher;
import dev.doughbay.fabric.Tuning;
import dev.doughbay.core.automation.ContinuousAutomationPolicy;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CollectionRetryReplenishmentTest {
    @org.junit.jupiter.api.BeforeAll static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
    }
    static final String ITEM="minecraft:stone", KEY=ITEM+"|100|192";
    @SuppressWarnings("unchecked")
    @Test void newNativeDeliveryCanReplenishAfterAnEarlierCollectionWasExhausted() throws Exception {
        var controller=new AutomationSessionController(new AutomatedExecutionDriver());
        set(controller,"policy",ContinuousAutomationPolicy.fromConfigValues(2,100_000,50_000,5_000,12,80,10,8,120,1));
        double bids=Tuning.get("orders.bid"), reserve=Tuning.get("orders.reserve_stock");
        try {
            Tuning.set("orders.bid",0); Tuning.set("orders.reserve_stock",0);
            read(controller,64,1_000_000);
            Map<String,Integer> attempts=(Map<String,Integer>)get(controller,"collectAttempts");
            attempts.put(KEY,4);
            read(controller,64,1_100_000);
            assertEquals(4,attempts.get(KEY),"unchanged native goods must not start a retry loop");
            set(controller,"ownOrdersComplete",false);
            set(controller,"ownOrders",List.of(new AutomatedExecutionDriver.OwnOrderRow(ITEM,ITEM,192,100,128,0,false)));
            set(controller,"ownOrdersReadAt",1_150_000L);
            plan(controller,1_150_000);
            assertEquals(4,attempts.get(KEY),"an incomplete page cannot release the exhausted budget");
            read(controller,128,1_200_000);
            assertNull(attempts.get(KEY),"fresh additional deliveries must not inherit the old exhausted attempt budget");
            assertTrue(((Deque<?>)get(controller,"deskQueue")).stream().anyMatch(s->s.toString().contains("COLLECT")),"the newly delivered stock must be eligible for native collection again");
        } finally { Tuning.set("orders.bid",bids); Tuning.set("orders.reserve_stock",reserve); }
    }
    private static void read(Object c,int delivered,long at) throws Exception {
        set(c,"ownOrders",List.of(new AutomatedExecutionDriver.OwnOrderRow(ITEM,ITEM,192,100,delivered,0,false)));
        set(c,"ownOrdersComplete",true);set(c,"ownOrdersReadAt",at);
        plan(c,at);
    }
    private static void plan(Object c,long at) throws Exception {var m=c.getClass().getDeclaredMethod("planDesk",MarketWatcher.Snapshot.class,long.class);m.setAccessible(true);m.invoke(c,null,at);}
    static Object get(Object c,String key) throws Exception {var f=c.getClass().getDeclaredField(key);f.setAccessible(true);return f.get(c);}
    static void set(Object c,String key,Object v) throws Exception {var f=c.getClass().getDeclaredField(key);f.setAccessible(true);f.set(c,v);}
}
