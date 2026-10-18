package dev.doughbay.fabric;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ItemSelectionMigrationTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    @TempDir Path directory;
    private final Map<String, Object> saved = new HashMap<>();
    private Object field(String name) throws Exception { var f=Tuning.class.getDeclaredField(name); f.setAccessible(true); return f.get(null); }
    private void field(String name,Object value) throws Exception { var f=Tuning.class.getDeclaredField(name); f.setAccessible(true); f.set(null,value); }
    @BeforeEach void isolate() throws Exception {
        for(String name:List.of("file","localFile","seenModifiedAt","seenLocalModifiedAt")) saved.put(name,field(name));
        for(String name:List.of("VALUES","TEXTS")) saved.put(name,new HashMap<>((Map<?,?>)field(name)));
        saved.put("LOCAL_KEYS",new HashSet<>((Set<?>)field("LOCAL_KEYS")));
        field("file",null); field("localFile",null);
    }
    @AfterEach @SuppressWarnings({"rawtypes","unchecked"}) void restore() throws Exception {
        for(String name:List.of("file","localFile","seenModifiedAt","seenLocalModifiedAt")) field(name,saved.get(name));
        for(String name:List.of("VALUES","TEXTS")){ var m=(Map)field(name);m.clear();m.putAll((Map)saved.get(name)); }
        var keys=(Set)field("LOCAL_KEYS");keys.clear();keys.addAll((Set)saved.get("LOCAL_KEYS"));
    }
    @Test void visibleSelectionsAndExclusionsSurviveAndConfigTrackingIsSeeded() {
        ObjectNode root=JSON.createObjectNode().put("items.buy_focus","=glass, boots")
                .put("items.allow","breeze_rod").put("items.deny","iron_boots")
                .put("watch.tracked","emerald").put("watch.tracked_managed",0).put("session.spend_cap",12345);
        assertTrue(ItemSelectionMigration.migrate(root,List.of("minecraft:diamond"),
                List.of("minecraft:glass","minecraft:white_stained_glass","minecraft:iron_boots")));
        assertFalse(root.has("items.buy_focus"));
        assertEquals("breeze_rod, =glass, boots",root.path("items.allow").asText());
        String tracked=root.path("watch.tracked").asText();
        assertTrue(tracked.contains("diamond"));assertTrue(tracked.contains("emerald"));assertTrue(tracked.contains("iron_boots"));
        assertFalse(tracked.contains("=glass"));assertFalse(tracked.contains("white_stained_glass"));
        assertEquals("iron_boots",root.path("items.deny").asText());assertEquals(12345,root.path("session.spend_cap").asInt());
        ObjectNode once=root.deepCopy();assertFalse(ItemSelectionMigration.migrate(root,List.of("new_config_item"),List.of()));assertEquals(once,root);
        root.put("items.allow","diamond").put("watch.tracked","diamond").put("items.buy_focus","breeze_rod");
        assertTrue(ItemSelectionMigration.migrate(root,List.of(),List.of()));
        assertEquals("diamond",root.path("items.allow").asText());assertEquals("diamond",root.path("watch.tracked").asText());
    }
    @Test void managedEmptyWatchDoesNotResurrectConfigItemsAndFocusOnlyDoesNotBecomeEverything() {
        ObjectNode root=JSON.createObjectNode().put("items.buy_focus","=breeze_rod")
                .put("items.allow","").put("watch.tracked","").put("watch.tracked_managed",1);
        ItemSelectionMigration.migrate(root,List.of("diamond"),List.of());
        assertEquals("=breeze_rod",root.path("items.allow").asText());
        assertEquals("breeze_rod",root.path("watch.tracked").asText());
    }
    @Test void emptyLegacyFilterDoesNotNarrowUnrestrictedTrading() {
        ObjectNode root=JSON.createObjectNode().put("items.buy_focus","").put("items.allow","").put("watch.tracked_managed",0);
        ItemSelectionMigration.migrate(root,List.of("diamond"),List.of());
        assertEquals("",root.path("items.allow").asText());assertEquals(0,root.path("watch.tracked_managed").asInt());
        assertFalse(root.has("watch.tracked"));
    }
    @Test void fileMigrationPersistsOnceAndItemsEditsRemainAuthoritativeAfterReload() throws Exception {
        ObjectNode root=JSON.createObjectNode().put("items.buy_focus","=emerald")
                .put("items.allow","emerald, breeze_rod").put("items.deny","").put("items.liquidate","")
                .put("watch.tracked","emerald").put("watch.tracked_managed",1).put("buy.purchase_cap_pct",2);
        Path file=directory.resolve("tuning.json");Files.writeString(file,root.toString());
        Tuning.load(directory);
        assertFalse(JSON.readTree(Files.readString(file)).has("items.buy_focus"));
        assertTrue(Files.exists(directory.resolve("tuning.before-item-selection-migration.json")));
        assertTrue(Tuning.buyingAllowed("minecraft:breeze_rod"));
        ItemLists.include(List.of("diamond"));ItemLists.track(List.of("diamond"),true);
        ItemLists.exclude(List.of("emerald"));Tuning.load(directory);
        assertTrue(Tuning.buyingAllowed("minecraft:diamond"));assertFalse(Tuning.buyingAllowed("minecraft:emerald"));
        assertTrue(ItemLists.tracked().contains("diamond"));assertEquals(2,Tuning.get("buy.purchase_cap_pct"));
        assertTrue(Tuning.LISTS.stream().noneMatch(l->l.key().equals("items.buy_focus")));
    }
    @Test void oldPresetCodesConvertSelectionsWithoutReinstatingTheHiddenGate() throws Exception {
        ObjectNode code=JSON.createObjectNode().put("v",1);code.putObject("n").put("watch.tracked_managed",1);
        code.putObject("t").put("items.buy_focus","=emerald").put("items.allow","breeze_rod")
                .put("items.deny","").put("items.liquidate","").put("watch.tracked","breeze_rod");
        assertTrue(SettingsCode.apply(SettingsCode.encode(code.toString().getBytes(StandardCharsets.UTF_8))).ok());
        assertTrue(Tuning.buyingAllowed("minecraft:breeze_rod"));assertTrue(Tuning.buyingAllowed("minecraft:emerald"));
        assertTrue(ItemLists.tracked().contains("emerald"));
        ItemLists.include(List.of("diamond"));assertTrue(Tuning.buyingAllowed("minecraft:diamond"));
        assertFalse(Tuning.buyingAllowed("minecraft:mace"));
    }
}
