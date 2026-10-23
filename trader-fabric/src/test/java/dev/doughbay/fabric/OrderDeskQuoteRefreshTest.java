package dev.doughbay.fabric;

import dev.doughbay.core.analysis.RiskConfig;
import dev.doughbay.core.automation.ContinuousAutomationPolicy;
import dev.doughbay.core.model.MarketStats;
import dev.doughbay.core.model.StackBucket;
import dev.doughbay.fabric.automation.AutomationSessionController;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.lang.reflect.*;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class OrderDeskQuoteRefreshTest {
    @TempDir Path directory;
    @BeforeAll static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    @Test void rejectedSingleCandidateDoesNotSuppressItsWholeStackPriceRefresh() throws Exception {
        String before = SettingsCode.exportPreset(Map.of(), Map.of());
        boolean keylessBefore=KeylessFeed.active();
        Tuning.load(directory);
        try {
            for (var e : Map.of("orders.bid",1.0,"orders.bid_min_confidence",0.2,
                    "orders.bid_min_samples",15.0,"orders.bid_on_real_trades",0.0,
                    "list.singles",0.0,"orders.size_by_sale",1.0,"keyless.sweep",1.0,
                    "underdog.enabled",0.0,"hive.split_markets",0.0,"session.wind_down",0.0).entrySet())
                Tuning.set(e.getKey(),e.getValue());
            Tuning.setText("items.allow","=diamond_block");
            Tuning.set("prices.only",0);Tuning.set("buy.focus",0);
            Tuning.set("orders.bid_min_value",0);Tuning.set("orders.bid_max_value",0);
            Tuning.setText("items.deny",""); Tuning.setText("items.liquidate","");
            Tuning.setText("prices.own","");
            KeylessFeed.setActive(true);
            long now=System.currentTimeMillis();
            var single=market(StackBucket.X1,0.205,1247,63_050,now-30*60_000,now);
            var stack=market(StackBucket.X64,0.174,459,3_571_360,now-3*3_600_000,now);
            var snapshot=new MarketWatcher.Snapshot(List.of(),List.of(),List.of(single,stack),List.of(),
                    Map.of(),Map.of(),null,"",1_000_000_000,"test fixture",null,now,now,false);
            var controller=new AutomationSessionController(new AutomatedExecutionDriver());
            set(controller,"policy",ContinuousAutomationPolicy.fromConfigValues(
                    100,75_000_000,15_000_000,350,2,10,2,8,240,86));
            set(controller,"riskConfig",new RiskConfig(3,350,2,10,25,90,true,true,1,
                    24*3_600_000L,24,0.1,1));
            Method plan=controller.getClass().getDeclaredMethod("planDesk",MarketWatcher.Snapshot.class,long.class);
            plan.setAccessible(true);plan.invoke(controller,snapshot,now);
            Field field=controller.getClass().getDeclaredField("deskQueue");field.setAccessible(true);
            Collection<?> queue=(Collection<?>)field.get(controller);
            boolean stackQuote=false,placement=false;
            for(Object step:queue) {
                String action=String.valueOf(value(step,"action"));
                if(action.equals("PLACE"))placement=true;
                if(action.equals("SWEEP")&&value(step,"itemId").equals("minecraft:diamond_block")
                        &&value(step,"count").equals(64))stackQuote=true;
            }
            assertFalse(placement,"No bid may be placed without a valid whole-stack resale price");
            assertTrue(stackQuote,"The rejected single candidate must leave the stack price refresh eligible: "+queue);
        } finally {
            KeylessFeed.setActive(keylessBefore);SettingsCode.apply(before);
        }
    }
    private static MarketStats market(StackBucket bucket,double confidence,int samples,long quick,long newest,long now) {
        return new MarketStats("minecraft:diamond_block",bucket,7*86_400_000L,samples,0,4,newest,
                quick,quick,quick,quick,quick,15,0.02,0.19,confidence,now);
    }
    private static void set(Object object,String name,Object value)throws Exception {
        Field field=object.getClass().getDeclaredField(name);field.setAccessible(true);field.set(object,value);
    }
    private static Object value(Object record,String name)throws Exception {
        Method method=record.getClass().getDeclaredMethod(name);method.setAccessible(true);return method.invoke(record);
    }
}
