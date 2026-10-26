package dev.doughbay.fabric;

import java.lang.management.ManagementFactory;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Repeated production trade gates, with no game launch, server action or live settings. */
class ScanItemFilter391Test {
    private static void keeping(Runnable test) {
        Map<String,String> old=new HashMap<>();
        for(String key:List.of("items.allow","items.deny","items.liquidate","buy.focus_items"))old.put(key,Tuning.text(key));
        double focus=Tuning.get("buy.focus");
        try {test.run();} finally {old.forEach(Tuning::setText);Tuning.set("buy.focus",focus);}
    }

    @Test void repeatedScanGatesDoNotReparseUnchangedLargeSelections() {
        keeping(()->{
            StringJoiner ids=new StringJoiner(", ");
            for(int i=0;i<512;i++)ids.add("=fixture_"+i);
            Tuning.setText("items.allow",ids.toString());Tuning.setText("items.deny","");
            Tuning.setText("items.liquidate","");Tuning.set("buy.focus",0);
            for(int i=0;i<100;i++)assertTrue(Tuning.buyingAllowed("minecraft:fixture_511"));
            var bean=ManagementFactory.getThreadMXBean();
            assertInstanceOf(com.sun.management.ThreadMXBean.class,bean);
            var allocations=(com.sun.management.ThreadMXBean)bean;
            assertTrue(allocations.isThreadAllocatedMemorySupported());
            allocations.setThreadAllocatedMemoryEnabled(true);
            long thread=Thread.currentThread().threadId(),before=allocations.getThreadAllocatedBytes(thread),start=System.nanoTime();
            for(int i=0;i<2000;i++)assertTrue(Tuning.buyingAllowed("minecraft:fixture_511"));
            long bytes=allocations.getThreadAllocatedBytes(thread)-before;
            System.out.println("SCAN_FILTER391 gates=2000 items=512 bytes="+bytes+" elapsedMs="+(System.nanoTime()-start)/1_000_000);
            assertTrue(bytes<16_000_000,"Unchanged scan filters allocated "+bytes+" bytes for 2000 gates");
        });
    }

    @Test void editsImmediatelyChangeExactCategoryDenyLiquidationAndFocusGates() {
        keeping(()->{
            Tuning.setText("items.allow","glass");Tuning.setText("items.deny","");Tuning.setText("items.liquidate","");Tuning.set("buy.focus",0);
            assertTrue(Tuning.buyingAllowed("minecraft:red_stained_glass"));
            Tuning.setText("items.allow","=glass");assertFalse(Tuning.buyingAllowed("minecraft:red_stained_glass"));
            assertTrue(Tuning.buyingAllowed("minecraft:glass"));
            Tuning.setText("items.deny","=glass");assertFalse(Tuning.buyingAllowed("minecraft:glass"));
            Tuning.setText("items.deny","");Tuning.setText("items.liquidate","=glass");assertFalse(Tuning.buyingAllowed("minecraft:glass"));
            Tuning.setText("items.liquidate","");Tuning.setText("buy.focus_items","=diamond");Tuning.set("buy.focus",1);
            assertFalse(Tuning.buyingAllowed("minecraft:glass"));Tuning.setText("buy.focus_items","=glass");assertTrue(Tuning.buyingAllowed("minecraft:glass"));
        });
    }

    @Test void returnedItemSetsRemainIndependentMutableCopies() {
        keeping(()->{Tuning.setText("items.allow","=diamond");var copy=Tuning.itemSet("items.allow");copy.clear();copy.add("=mace");assertEquals(Set.of("=diamond"),Tuning.itemSet("items.allow"));});
    }
}
