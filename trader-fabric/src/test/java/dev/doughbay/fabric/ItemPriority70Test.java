package dev.doughbay.fabric;

import java.lang.reflect.Field;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ItemPriority70Test {
    @TempDir Path dir;
    private Map<String,Double> numbers;private Map<String,String> texts;private Set<String> localKeys;private Object file,local;
    private static Field field(String name)throws Exception{var f=Tuning.class.getDeclaredField(name);f.setAccessible(true);return f;}
    @SuppressWarnings("unchecked") private static <T>T value(String name)throws Exception{return (T)field(name).get(null);}
    @BeforeEach void isolate()throws Exception{
        numbers=new HashMap<>(ItemPriority70Test.<Map<String,Double>>value("VALUES"));texts=new HashMap<>(ItemPriority70Test.<Map<String,String>>value("TEXTS"));localKeys=new HashSet<>(ItemPriority70Test.<Set<String>>value("LOCAL_KEYS"));file=value("file");local=value("localFile");field("file").set(null,null);field("localFile").set(null,null);
        Tuning.setText(ItemPriorities.KEY,"");Tuning.setText("items.allow","");Tuning.setText("items.deny","");
    }
    @AfterEach void restore()throws Exception{
        Map<String,Double> n=value("VALUES");n.clear();n.putAll(numbers);Map<String,String> t=value("TEXTS");t.clear();t.putAll(texts);Set<String> k=value("LOCAL_KEYS");k.clear();k.addAll(localKeys);field("file").set(null,file);field("localFile").set(null,local);
    }
    @Test void prioritiesSurviveRealSettingsFileReloadWithoutChangingItemFiltersOrLimits()throws Exception{
        Tuning.load(dir);Tuning.setText("items.allow","=diamond, =emerald");Tuning.setText("items.deny","=mace");Tuning.set("buy.purchase_cap_pct",11);
        ItemPriorities.set(List.of("minecraft:diamond"),ItemPriorities.Level.HIGH);ItemPriorities.set(List.of("emerald"),ItemPriorities.Level.LOW);
        assertTrue(Files.readString(dir.resolve("tuning.json")).contains("diamond=high"));
        field("file").set(null,null);Tuning.setText(ItemPriorities.KEY,"");Tuning.load(dir);
        assertEquals(ItemPriorities.Level.HIGH,ItemPriorities.level("minecraft:diamond"));assertEquals(ItemPriorities.Level.LOW,ItemPriorities.level("emerald"));
        assertEquals(Set.of("=diamond","=emerald"),Tuning.itemSet("items.allow"));assertEquals(Set.of("=mace"),Tuning.itemSet("items.deny"));assertEquals(11,Tuning.get("buy.purchase_cap_pct"));
        ItemPriorities.set(List.of("diamond"),ItemPriorities.Level.STANDARD);field("file").set(null,null);Tuning.setText(ItemPriorities.KEY,"diamond=low");Tuning.load(dir);assertEquals(ItemPriorities.Level.STANDARD,ItemPriorities.level("diamond"));
    }
    @Test void stablePreferenceKeepsLowEnabledAndExistingOrderWithinEachLevel(){
        ItemPriorities.set(List.of("diamond"),ItemPriorities.Level.LOW);ItemPriorities.set(List.of("emerald"),ItemPriorities.Level.HIGH);
        var eligible=new ArrayList<>(List.of("diamond","stone","emerald","obsidian"));ItemPriorities.prioritize(eligible,x->x);
        assertEquals(List.of("emerald","stone","obsidian","diamond"),eligible);assertTrue(Tuning.itemAllowed("diamond"));
        ItemPriorities.set(List.of("diamond","emerald"),ItemPriorities.Level.STANDARD);var defaults=new ArrayList<>(List.of("diamond","stone","emerald"));ItemPriorities.prioritize(defaults,x->x);assertEquals(List.of("diamond","stone","emerald"),defaults);
    }
    @Test void exactItemsAndClosedLevelsDoNotCreateCategoryRules(){
        Tuning.setText(ItemPriorities.KEY,"glass=low, stained_glass=high, diamond=unknown, stone=high;stone=standard; =emerald=HIGH");
        assertEquals(ItemPriorities.Level.LOW,ItemPriorities.level("glass"));assertEquals(ItemPriorities.Level.HIGH,ItemPriorities.level("stained_glass"));assertEquals(ItemPriorities.Level.STANDARD,ItemPriorities.level("white_stained_glass"));assertEquals(ItemPriorities.Level.STANDARD,ItemPriorities.level("diamond"));assertEquals(ItemPriorities.Level.STANDARD,ItemPriorities.level("stone"));assertEquals(ItemPriorities.Level.HIGH,ItemPriorities.level("minecraft:emerald"));
    }
    @Test void fullSavedSetupCarriesPriorities(){
        ItemPriorities.set(List.of("diamond"),ItemPriorities.Level.HIGH);String saved=SettingsCode.exportPreset(Map.of(),Map.of());ItemPriorities.set(List.of("diamond"),ItemPriorities.Level.LOW);assertTrue(SettingsCode.apply(saved).ok());assertEquals(ItemPriorities.Level.HIGH,ItemPriorities.level("diamond"));
    }
}
