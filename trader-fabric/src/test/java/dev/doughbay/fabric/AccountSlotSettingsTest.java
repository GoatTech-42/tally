package dev.doughbay.fabric;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AccountSlotSettingsTest {
    private static final ObjectMapper JSON=new ObjectMapper();
    @TempDir Path root;
    private final Map<String,Object> saved=new HashMap<>();
    private Object field(String name) throws Exception {var f=Tuning.class.getDeclaredField(name);f.setAccessible(true);return f.get(null);}
    private void field(String name,Object value) throws Exception {var f=Tuning.class.getDeclaredField(name);f.setAccessible(true);f.set(null,value);}
    @SuppressWarnings("unchecked") private Map<String,Double> values() throws Exception {return (Map<String,Double>)field("VALUES");}
    @BeforeEach void remember() throws Exception {
        for(String name:List.of("file","localFile","seenModifiedAt","seenLocalModifiedAt"))saved.put(name,field(name));
        saved.put("VALUES",new HashMap<>((Map<?,?>)field("VALUES")));
        saved.put("TEXTS",new HashMap<>((Map<?,?>)field("TEXTS")));
        saved.put("LOCAL_KEYS",new HashSet<>((Set<?>)field("LOCAL_KEYS")));
    }
    @AfterEach @SuppressWarnings({"rawtypes","unchecked"}) void restore() throws Exception {
        for(String name:List.of("file","localFile","seenModifiedAt","seenLocalModifiedAt"))field(name,saved.get(name));
        for(String name:List.of("VALUES","TEXTS")){var map=(Map)field(name);map.clear();map.putAll((Map)saved.get(name));}
        var keys=(Set)field("LOCAL_KEYS");keys.clear();keys.addAll((Set)saved.get("LOCAL_KEYS"));
    }
    private Path shared() throws Exception {
        Path dir=Files.createDirectories(root.resolve("shared"));
        Files.writeString(dir.resolve("tuning.json"),"{\"slots.max\":18,\"orders.bid_slots\":18,\"list.singles\":0}");
        return dir;
    }
    private void activate(Path shared,Path local) throws Exception {
        values().clear();field("file",shared.resolve("tuning.json"));field("localFile",null);
        var config=JSON.readTree(Files.readString(shared.resolve("tuning.json")));
        config.fields().forEachRemaining(e->{try{values().put(e.getKey(),e.getValue().asDouble());}catch(Exception ex){throw new RuntimeException(ex);}});
        Tuning.loadLocalFrom(local);
    }
    @Test void beginnerDisplayHelpOnlyChangesThisAccountsTwoDisplayPreferences() throws Exception {
        Path shared=shared(),one=root.resolve("one.json"),two=root.resolve("two.json");
        activate(shared,one);
        Tuning.setLocalBatch(Map.of("quiet.hide_containers",1.0,"quiet.hide_hotbar",1.0,
                "privacy.hide_name",1.0,"slots.max",18.0,"session.auto_resume",0.0));
        byte[] common=Files.readAllBytes(shared.resolve("tuning.json"));
        BeginnerHelp.showGameUi();
        assertEquals(0,Tuning.get("quiet.hide_containers"));assertEquals(0,Tuning.get("quiet.hide_hotbar"));
        assertEquals(1,Tuning.get("privacy.hide_name"));assertEquals(18,Tuning.get("slots.max"));
        assertEquals(0,Tuning.get("session.auto_resume"));
        assertArrayEquals(common,Files.readAllBytes(shared.resolve("tuning.json")));
        activate(shared,two);Tuning.setLocalBatch(Map.of("quiet.hide_containers",1.0,"quiet.hide_hotbar",1.0));
        activate(shared,one);assertEquals(0,Tuning.get("quiet.hide_containers"));
        activate(shared,two);assertEquals(1,Tuning.get("quiet.hide_containers"));assertEquals(1,Tuning.get("quiet.hide_hotbar"));
    }
    @Test void manuallyChosenCapacityCannotBeOverwrittenByMenuOrApiLearning() throws Exception {
        Path shared=shared(),local=root.resolve("own.json");activate(shared,local);
        Tuning.set("slots.max",18);Tuning.set("orders.bid_slots",18);
        assertFalse(RankCapacity.apply(new RankCapacity.PageCapacity(90,true)),
                "A later menu observation must not replace the operator's explicit limit");
        Tuning.learnAccountCapacity(90);
        assertEquals(18,Tuning.get("slots.max"));assertEquals(18,Tuning.get("orders.bid_slots"));
        activate(shared,local);Tuning.learnAccountCapacity(90);
        assertEquals(18,Tuning.get("slots.max"));assertEquals(18,Tuning.get("orders.bid_slots"));
    }
    @Test void manualCapacityModeIsLocalAndExplicitReenableAllowsRankUpgrade() throws Exception {
        Path shared=shared(),one=root.resolve("one.json"),two=root.resolve("two.json");
        activate(shared,one);Tuning.set("slots.max",18);Tuning.set("orders.bid_slots",12);
        assertEquals(0,Tuning.get("slots.auto_detect"));
        assertTrue(Tuning.thisClientOnly("slots.auto_detect"));
        activate(shared,two);assertEquals(1,Tuning.get("slots.auto_detect"));
        assertTrue(RankCapacity.apply(new RankCapacity.PageCapacity(90,true)));
        activate(shared,one);assertEquals(0,Tuning.get("slots.auto_detect"));
        assertFalse(RankCapacity.apply(new RankCapacity.PageCapacity(90,true)));
        assertEquals(18,Tuning.get("slots.max"));assertEquals(12,Tuning.get("orders.bid_slots"));
        Tuning.set("slots.auto_detect",1);
        assertTrue(RankCapacity.apply(new RankCapacity.PageCapacity(90,true)));
        activate(shared,one);assertEquals(1,Tuning.get("slots.auto_detect"));
        assertEquals(90,Tuning.get("slots.max"));assertEquals(90,Tuning.get("orders.bid_slots"));
        var common=JSON.readTree(Files.readString(shared.resolve("tuning.json")));
        assertFalse(common.has("slots.auto_detect"),"No Hive-wide detection policy may be saved");
    }
    @Test void existingLocalCountsArePreservedAndDetectionRemainsOnUntilOperatorChooses() throws Exception {
        Path shared=shared(),local=root.resolve("own.json");
        Files.writeString(local,"{\"slots.max\":18,\"orders.bid_slots\":18}");activate(shared,local);
        assertEquals(18,Tuning.get("slots.max"));assertEquals(1,Tuning.get("slots.auto_detect"));
        Tuning.set("orders.bid_slots",18);assertEquals(0,Tuning.get("slots.auto_detect"));
        activate(shared,local);assertEquals(0,Tuning.get("slots.auto_detect"));
        assertFalse(RankCapacity.apply(new RankCapacity.PageCapacity(45,false)));
        Tuning.reset("slots.max");Tuning.learnAccountCapacity(90);
        assertEquals(Tuning.setting("slots.max").defaultValue(),Tuning.get("slots.max"));
        assertEquals(18,Tuning.get("orders.bid_slots"));
    }
    @Test void settingSlotsOnAFreshClientWritesLocallyInsteadOfChangingTheHive() throws Exception {
        Path shared=shared(),local=root.resolve("alt-local.json");activate(shared,local);
        Tuning.set("slots.max",45);Tuning.set("orders.bid_slots",45);
        var common=JSON.readTree(Files.readString(shared.resolve("tuning.json")));
        assertEquals(18,common.path("slots.max").asInt());assertEquals(18,common.path("orders.bid_slots").asInt());
        var own=JSON.readTree(Files.readString(local));
        assertEquals(45,own.path("slots.max").asInt());assertEquals(45,own.path("orders.bid_slots").asInt());
        assertTrue(Tuning.thisClientOnly("slots.max"));assertTrue(Tuning.thisClientOnly("orders.bid_slots"));
    }
    @Test void twoAccountsKeepDifferentLimitsAcrossReloadsAndSharedExternalEdits() throws Exception {
        Path shared=shared(),one=root.resolve("one.json"),two=root.resolve("two.json");
        activate(shared,one);Tuning.set("slots.max",18);Tuning.set("orders.bid_slots",18);
        activate(shared,two);Tuning.set("slots.max",45);Tuning.set("orders.bid_slots",30);
        Files.writeString(shared.resolve("tuning.json"),"{\"slots.max\":18,\"orders.bid_slots\":18,\"list.singles\":1}");
        Files.setLastModifiedTime(shared.resolve("tuning.json"),java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis()+5000));
        Tuning.pollExternalChanges();
        assertEquals(45,Tuning.get("slots.max"));assertEquals(30,Tuning.get("orders.bid_slots"));assertEquals(1,Tuning.get("list.singles"));
        activate(shared,one);assertEquals(18,Tuning.get("slots.max"));assertEquals(18,Tuning.get("orders.bid_slots"));
        activate(shared,two);assertEquals(45,Tuning.get("slots.max"));assertEquals(30,Tuning.get("orders.bid_slots"));
    }
    @Test void existingLocalLimitsAndUnrelatedOptionsSurviveSavingAndReset() throws Exception {
        Path shared=shared(),local=root.resolve("own.json");
        Files.writeString(local,"{\"slots.max\":90,\"orders.bid_slots\":70,\"join.auto\":0}");
        activate(shared,local);Tuning.set("list.singles",0);assertEquals(90,Tuning.get("slots.max"));
        Tuning.reset("slots.max");
        var own=JSON.readTree(Files.readString(local));
        assertEquals(Tuning.setting("slots.max").defaultValue(),own.path("slots.max").asDouble());
        assertEquals(70,own.path("orders.bid_slots").asInt());assertEquals(0,own.path("join.auto").asInt());
        assertEquals(18,JSON.readTree(Files.readString(shared.resolve("tuning.json"))).path("slots.max").asInt());
    }
    @Test void localLimitsStillLoadWhenTheSharedSettingsFileIsMissing() throws Exception {
        Path shared=shared(),local=root.resolve("own.json");
        Files.writeString(local,"{\"slots.max\":90,\"orders.bid_slots\":70}");activate(shared,local);
        values().clear();Files.delete(shared.resolve("tuning.json"));Tuning.load(shared);
        assertEquals(90,Tuning.get("slots.max"));assertEquals(70,Tuning.get("orders.bid_slots"));
    }
    @Test void anEditedLocalFileCannotMakeAClientBorrowSharedSlotCountsAgain() throws Exception {
        Path shared=shared(),local=root.resolve("own.json");activate(shared,local);
        Tuning.set("slots.max",45);Tuning.set("orders.bid_slots",30);
        Files.writeString(local,"{\"orders.bid_slots\":30}");
        Files.writeString(shared.resolve("tuning.json"),"{\"slots.max\":18,\"orders.bid_slots\":18,\"list.singles\":1}");
        Files.setLastModifiedTime(shared.resolve("tuning.json"),java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis()+5000));
        Tuning.pollExternalChanges();
        assertEquals(45,Tuning.get("slots.max"));assertEquals(30,Tuning.get("orders.bid_slots"));
        assertEquals(45,JSON.readTree(Files.readString(local)).path("slots.max").asInt());
    }
    @Test void malformedLocalSettingsAreNotOverwrittenOrReplacedBySharedLimits() throws Exception {
        Path shared=shared(),local=root.resolve("own.json");activate(shared,local);
        Tuning.set("slots.max",45);Tuning.set("orders.bid_slots",30);
        Files.writeString(local,"{broken");
        Files.writeString(shared.resolve("tuning.json"),"{\"slots.max\":18,\"orders.bid_slots\":18,\"list.singles\":1}");
        Files.setLastModifiedTime(shared.resolve("tuning.json"),java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis()+5000));
        Tuning.pollExternalChanges();
        assertEquals(45,Tuning.get("slots.max"));assertEquals(30,Tuning.get("orders.bid_slots"));
        assertEquals("{broken",Files.readString(local));
        assertEquals(0,Tuning.get("slots.auto_detect"));
        Tuning.learnAccountCapacity(90);
        assertEquals(45,Tuning.get("slots.max"));assertEquals(30,Tuning.get("orders.bid_slots"));
    }
    @Test void malformedLocalReloadCannotReenableDetectionOrOverwriteTheBrokenFile() throws Exception {
        Path shared=shared(),local=root.resolve("own.json");activate(shared,local);
        Tuning.set("slots.max",18);Tuning.set("orders.bid_slots",12);
        Files.writeString(local,"{broken");Tuning.loadLocalFrom(local);
        Tuning.learnAccountCapacity(90);
        assertEquals(0,Tuning.get("slots.auto_detect"));
        assertEquals(18,Tuning.get("slots.max"));assertEquals(12,Tuning.get("orders.bid_slots"));
        assertEquals("{broken",Files.readString(local));
    }
    @Test void confirmedRankUpgradeUpdatesBothLimitsOnlyForTheOwningAccount() throws Exception {
        Path shared=shared(),one=root.resolve("one.json"),two=root.resolve("two.json");
        activate(shared,one); Tuning.learnAccountCapacity(18);
        activate(shared,two); assertTrue(RankCapacity.apply(new RankCapacity.PageCapacity(90,true)));
        assertEquals(90,Tuning.get("slots.max")); assertEquals(90,Tuning.get("orders.bid_slots"));
        activate(shared,one); assertEquals(18,Tuning.get("slots.max")); assertEquals(18,Tuning.get("orders.bid_slots"));
        activate(shared,two); assertEquals(90,Tuning.get("slots.max")); assertEquals(90,Tuning.get("orders.bid_slots"));
        assertEquals(18,JSON.readTree(Files.readString(shared.resolve("tuning.json"))).path("slots.max").asInt());
    }
    @Test void partialRankEvidenceNeverDowngradesEitherLimitButExactEvidenceCan() throws Exception {
        Path shared=shared(),local=root.resolve("own.json"); activate(shared,local);
        Tuning.learnAccountCapacity(90);
        assertFalse(RankCapacity.apply(new RankCapacity.PageCapacity(45,false)));
        assertEquals(90,Tuning.get("slots.max")); assertEquals(90,Tuning.get("orders.bid_slots"));
        assertTrue(RankCapacity.apply(new RankCapacity.PageCapacity(45,true)));
        assertEquals(45,Tuning.get("slots.max")); assertEquals(45,Tuning.get("orders.bid_slots"));
        Tuning.learnAccountCapacity(0); Tuning.learnAccountCapacity(180);
        assertEquals(45,Tuning.get("slots.max")); assertEquals(45,Tuning.get("orders.bid_slots"));
    }
}
