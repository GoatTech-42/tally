package dev.doughbay.fabric;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class EvasionToggleTest {
    @TempDir Path root;
    private final Map<String,Object> saved=new HashMap<>();
    private Object field(String name)throws Exception{var f=Tuning.class.getDeclaredField(name);f.setAccessible(true);return f.get(null);}
    private void field(String name,Object value)throws Exception{var f=Tuning.class.getDeclaredField(name);f.setAccessible(true);f.set(null,value);}
    @BeforeEach void prepare()throws Exception{
        for(String name:List.of("file","localFile","seenModifiedAt","seenLocalModifiedAt"))saved.put(name,field(name));
        for(String name:List.of("VALUES","TEXTS"))saved.put(name,new HashMap<>((Map<?,?>)field(name)));
        saved.put("LOCAL_KEYS",new HashSet<>((Set<?>)field("LOCAL_KEYS")));
        field("file",null);field("localFile",null);
        Tuning.loadLocalFrom(root.resolve("own.json"));
    }
    @AfterEach @SuppressWarnings({"rawtypes","unchecked"}) void restore()throws Exception{
        for(String name:List.of("file","localFile","seenModifiedAt","seenLocalModifiedAt"))field(name,saved.get(name));
        for(String name:List.of("VALUES","TEXTS")){var map=(Map)field(name);map.clear();map.putAll((Map)saved.get(name));}
        var keys=(Set)field("LOCAL_KEYS");keys.clear();keys.addAll((Set)saved.get("LOCAL_KEYS"));
    }
    private EvasionGuard guard(String legacy)throws Exception{
        Path path=root.resolve("evasion.txt");if(legacy!=null)Files.writeString(path,legacy);
        return new EvasionGuard(path);
    }
    @Test void staffRegionAndDisconnectAlertsReachTheConfiguredWebhookPathWithoutAFeedOrWorld()throws Exception {
        var prior=EvasionGuard.ledgerPath();EvasionGuard.setLedger(null);
        try {
            for(String key:List.of("webhook.enabled","webhook.alerts","webhook.alert_evasion"))Tuning.set(key,1);
            var titles=new ArrayList<String>();var fields=new ArrayList<List<String[]>>();
            var guard=new EvasionGuard(root.resolve("unused.txt"),(title,rows,who)->{titles.add(title);fields.add(rows);});
            guard.noteStaffRegionEscape("TestStaff");
            guard.noteStaffEscape("TestStaff","test-server",120000);
            assertEquals(List.of("STAFF WATCH — REGION ESCAPE","STAFF WATCH — DISCONNECTED"),titles);
            assertTrue(fields.getFirst().stream().anyMatch(row->row[1].contains("preparing a safe region change")));
            assertEquals(2,guard.events().size());
        } finally {EvasionGuard.setLedger(prior);}
    }
    @Test void eachWebhookOptOutStillSuppressesStaffWebhookDelivery()throws Exception {
        var prior=EvasionGuard.ledgerPath();EvasionGuard.setLedger(null);
        try {
            var titles=new ArrayList<String>();
            var guard=new EvasionGuard(root.resolve("unused.txt"),(title,rows,who)->titles.add(title));
            for(String disabled:List.of("webhook.enabled","webhook.alerts","webhook.alert_evasion")) {
                for(String key:List.of("webhook.enabled","webhook.alerts","webhook.alert_evasion"))Tuning.set(key,1);
                Tuning.set(disabled,0);guard.noteStaffRegionEscape("TestStaff");
            }
            assertTrue(titles.isEmpty());assertEquals(3,guard.events().size());
        } finally {EvasionGuard.setLedger(prior);}
    }
    @Test void staffWebhookFailureCannotPreventLocalHistoryOrProtectiveFlow()throws Exception {
        var prior=EvasionGuard.ledgerPath();EvasionGuard.setLedger(null);
        try {
            for(String key:List.of("webhook.enabled","webhook.alerts","webhook.alert_evasion"))Tuning.set(key,1);
            var guard=new EvasionGuard(root.resolve("unused.txt"),(title,rows,who)->{throw new IllegalStateException("test failure");});
            assertDoesNotThrow(()->guard.noteStaffRegionEscape("TestStaff"));
            assertEquals(1,guard.events().size());
        } finally {EvasionGuard.setLedger(prior);}
    }
    @Test void importingEnabledSettingsAfterAnExplicitOffUpdatesTheActualGuard()throws Exception{
        Tuning.set("evasion.enabled",0);var guard=guard("off");
        var json=new ObjectMapper().createObjectNode().put("v",1);json.putObject("n").put("evasion.enabled",1);
        assertTrue(SettingsCode.apply(SettingsCode.encode(json.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8))).ok());
        assertTrue(guard.enabled());assertTrue(guard.status().startsWith("on,"));
    }
    @Test void restoringDefaultsUpdatesTheGuardAsWellAsTheDisplayedSetting()throws Exception{
        Tuning.set("evasion.enabled",0);var guard=guard("off");
        Tuning.applyPreset("Default");assertEquals(1,Tuning.get("evasion.enabled"));assertTrue(guard.enabled());
    }
    @Test void genericDisableMakesStatusAndRuntimeEligibilityAgree()throws Exception{
        Tuning.set("evasion.enabled",1);var guard=guard("on");assertTrue(guard.enabled());
        Tuning.set("evasion.enabled",0);assertFalse(guard.enabled());assertEquals("off",guard.status());
    }
    @Test void migrationPreservesOffRatherThanSilentlyEnablingAnExistingClient()throws Exception{
        Tuning.set("evasion.enabled",1);var guard=guard("off");
        assertFalse(guard.enabled());assertEquals(0,Tuning.get("evasion.enabled"));
        assertTrue(Tuning.thisClientOnly("evasion.enabled"));
        assertEquals(0,new ObjectMapper().readTree(Files.readString(root.resolve("own.json"))).path("evasion.enabled").asInt());
    }
    @Test void staleLegacyFileCannotUndoAnImportedChoiceAfterRestart()throws Exception{
        Tuning.set("evasion.enabled",1);guard("off");Tuning.set("evasion.enabled",1);
        Tuning.loadLocalFrom(root.resolve("own.json"));assertTrue(guard("off").enabled());
    }
    @Test void missingLegacyFileStaysOffUntilTheUserChoosesOn()throws Exception{
        Tuning.set("evasion.enabled",1);var guard=guard(null);assertFalse(guard.enabled());
        guard.setEnabled(true);assertEquals(1,Tuning.get("evasion.enabled"));assertTrue(guard.enabled());
        guard.setEnabled(false);assertEquals(0,Tuning.get("evasion.enabled"));assertFalse(guard.enabled());
    }
    @Test void hiveAccountsKeepTheirOwnChoicesAcrossSharedEditsAndReloads()throws Exception{
        Tuning.set("evasion.enabled",1);guard("on");
        Path second=root.resolve("alt.json");Tuning.loadLocalFrom(second);guard("off");
        assertFalse(guard("off").enabled());
        Path shared=Files.createDirectories(root.resolve("shared"));
        Files.writeString(shared.resolve("tuning.json"),"{\"evasion.enabled\":1}");
        Tuning.load(shared);assertEquals(0,Tuning.get("evasion.enabled"));
        Tuning.loadLocalFrom(root.resolve("own.json"));assertTrue(guard("off").enabled());
        Tuning.loadLocalFrom(second);assertFalse(guard("on").enabled());
    }
    @Test void unreadableLocalSettingsFailClosedWithoutOverwritingThem()throws Exception{
        Files.writeString(root.resolve("own.json"),"{broken");Tuning.set("evasion.enabled",1);
        assertFalse(guard("on").enabled());assertEquals("{broken",Files.readString(root.resolve("own.json")));
    }
    @Test void genericDisableClearsTheRuntimePauseFlagOnTheNextTick()throws Exception{
        Tuning.set("evasion.enabled",1);var guard=guard("on");
        var triggered=EvasionGuard.class.getDeclaredField("triggered");triggered.setAccessible(true);triggered.set(guard,true);
        Tuning.set("evasion.enabled",0);guard.tick(null,null);assertFalse(guard.triggered());
    }
}
