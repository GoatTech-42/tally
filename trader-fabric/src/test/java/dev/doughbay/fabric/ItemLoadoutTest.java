package dev.doughbay.fabric;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ItemLoadoutTest {
    @TempDir Path dir;
    @Test void namedItemCopiesSurviveReloadAndDoNotRestoreTradingLimits()throws Exception{
        String before=SettingsCode.exportPreset(Map.of(),Map.of());double slots=Tuning.get("slots.max");
        try{
            Tuning.setText("items.allow","=diamond");Tuning.setText("items.deny","=mace");
            Tuning.set("watch.tracked_managed",1);Tuning.setText("watch.tracked","diamond");
            var store=new SavedPresets(dir,true);var first=store.save("","Diamonds",SettingsCode.exportItemLoadout());
            Tuning.setText("items.allow","boots");Tuning.setText("items.deny","");Tuning.setText("watch.tracked","iron_boots");
            store.save("","Armor",SettingsCode.exportItemLoadout());
            Tuning.set("buy.purchase_cap_pct",8);Tuning.set("orders.outbid_pct",3);Tuning.set("slots.max",18);Tuning.set("pace.human_steps",1);
            var reloaded=new SavedPresets(dir,true);assertEquals(2,reloaded.entries().size());
            assertTrue(SettingsCode.applyItemLoadout(reloaded.get(first.id()).code()).ok());
            assertEquals(Set.of("=diamond"),Tuning.itemSet("items.allow"));assertEquals(Set.of("=mace"),Tuning.itemSet("items.deny"));
            assertEquals(Set.of("diamond"),ItemLists.tracked());
            assertEquals(8,Tuning.get("buy.purchase_cap_pct"));assertEquals(3,Tuning.get("orders.outbid_pct"));
            assertEquals(18,Tuning.get("slots.max"));assertEquals(1,Tuning.get("pace.human_steps"));
            Tuning.setText("items.allow","=emerald");assertEquals(first.code(),reloaded.get(first.id()).code(),"Applying a loadout does not edit its saved copy");
        }finally{SettingsCode.apply(before);Tuning.set("slots.max",slots);}
    }
    @Test void explicitlyEmptySelectionsReplaceOldLists()throws Exception{
        String before=SettingsCode.exportPreset(Map.of(),Map.of());
        try{
            Tuning.setText("items.allow","");Tuning.setText("items.deny","");Tuning.set("watch.tracked_managed",1);Tuning.setText("watch.tracked","");
            String empty=SettingsCode.exportItemLoadout();Tuning.setText("items.allow","boots");Tuning.setText("items.deny","mace");Tuning.setText("watch.tracked","diamond");
            assertTrue(SettingsCode.applyItemLoadout(empty).ok());assertEquals("",Tuning.text("items.allow"));assertEquals("",Tuning.text("items.deny"));
            assertTrue(ItemLists.tracked().isEmpty());assertEquals(1,Tuning.get("watch.tracked_managed"));
        }finally{SettingsCode.apply(before);}
    }
    @Test void fullSettingsCodesCannotBeAppliedOrSavedAsItemLoadouts()throws Exception{
        var store=new SavedPresets(dir,true);String code=SettingsCode.exportItemLoadout();store.save("","Items",code);
        byte[] original=Files.readAllBytes(dir.resolve("item-loadouts.json"));String full=SettingsCode.exportPreset(Map.of("buy.purchase_cap_pct",4d),Map.of("items.allow","=mace"));
        double cap=Tuning.get("buy.purchase_cap_pct");String items=Tuning.text("items.allow");
        assertFalse(SettingsCode.applyItemLoadout(full).ok());assertEquals(cap,Tuning.get("buy.purchase_cap_pct"));assertEquals(items,Tuning.text("items.allow"));
        assertThrows(java.io.IOException.class,()->store.save("","Unsafe",full));assertArrayEquals(original,Files.readAllBytes(dir.resolve("item-loadouts.json")));
        var general=new SavedPresets(dir);general.save("","Items",full);assertEquals(1,store.entries().size());assertEquals(1,general.entries().size());
    }
    @Test void malformedOrOversizedCodesAreRefusedBeforeMutation()throws Exception{
        String items=Tuning.text("items.allow");
        for(String json:List.of("{}","{\"v\":1,\"n\":{\"watch.tracked_managed\":1},\"t\":{\"items.allow\":\"a\",\"items.deny\":\"\",\"watch.tracked\":\"\",\"private\":\"x\"}}","{\"padding\":\""+"x".repeat(66000)+"\"}")){
            assertFalse(SettingsCode.applyItemLoadout(SettingsCode.encode(json.getBytes(java.nio.charset.StandardCharsets.UTF_8))).ok());assertEquals(items,Tuning.text("items.allow"));
        }
    }
}
