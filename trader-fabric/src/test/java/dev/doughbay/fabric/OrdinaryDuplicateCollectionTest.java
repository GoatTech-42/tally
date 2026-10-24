package dev.doughbay.fabric;

import dev.doughbay.fabric.automation.AutomationSessionController;

import dev.doughbay.core.analysis.RiskConfig;
import dev.doughbay.core.automation.ContinuousAutomationPolicy;
import dev.doughbay.fabric.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.lang.reflect.*;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class OrdinaryDuplicateCollectionTest {
 @TempDir Path directory;
 @BeforeAll static void bootstrap(){net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();}
 @Test void ordinaryPlannerSkipsTheAlreadyCollectedIdenticalCompletedRow()throws Exception {
  String before=SettingsCode.exportPreset(Map.of(),Map.of());Tuning.load(directory);
  try {
   Tuning.set("orders.bid",0);Tuning.set("orders.collect",1);Tuning.set("orders.collect_whole_stacks",1);
   Tuning.set("orders.reserve_stock",0);Tuning.set("session.wind_down",0);Tuning.set("prices.only",0);
   Tuning.setText("prices.own","");
   var c=new AutomationSessionController(new AutomatedExecutionDriver());
   set(c,"policy",ContinuousAutomationPolicy.fromConfigValues(100,75000000,15000000,350,2,10,2,8,240,86));
   set(c,"riskConfig",new RiskConfig(3,350,2,10,25,90,true,true,1,86400000L,24,.1,1));
   String item="minecraft:totem_of_undying";
   var row=new AutomatedExecutionDriver.OwnOrderRow(item,item,3,100,3,1000);
   set(c,"ownOrders",List.of(row,row));
   Field f=c.getClass().getDeclaredField("collectedByKey");f.setAccessible(true);
   ((Map<String,Integer>)f.get(c)).put(item+"|100|3",3);
   Method plan=c.getClass().getDeclaredMethod("planDesk",MarketWatcher.Snapshot.class,long.class);plan.setAccessible(true);
   plan.invoke(c,null,System.currentTimeMillis());
   Field queue=c.getClass().getDeclaredField("deskQueue");queue.setAccessible(true);
   List<?> collects=((Collection<?>)queue.get(c)).stream().filter(s->value(s,"action").toString().equals("COLLECT")).toList();
   assertEquals(1,collects.size());
   assertEquals(1,value(collects.get(0),"ordinal"),"The first identical row has already been collected; select the second row holding goods");
   assertEquals(3,value(collects.get(0),"count"));
  }finally{SettingsCode.apply(before);}
 }
 private static void set(Object o,String name,Object value)throws Exception {Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);f.set(o,value);}
 private static Object value(Object o,String name){try{Method m=o.getClass().getDeclaredMethod(name);m.setAccessible(true);return m.invoke(o);}catch(Exception e){throw new AssertionError(e);}}
}
