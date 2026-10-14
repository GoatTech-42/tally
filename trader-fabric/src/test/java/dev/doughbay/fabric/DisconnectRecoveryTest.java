package dev.doughbay.fabric;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class DisconnectRecoveryTest {
    @TempDir Path directory;

    @Test void onlyExplicitClickSpamChangesPacing() {
        for (String reason : new String[]{"Click-Spam", "Kicked for CLICK SPAM", "click_spam", "ClickSpam"})
            assertTrue(DisconnectRecovery.clickSpam(reason), reason);
        for (String reason : new String[]{"Timed out", "Connection reset", "Chat spam", "Banned", "Server closed"})
            assertFalse(DisconnectRecovery.clickSpam(reason), reason);
    }

    @Test void intentionalDeparturesAreNotKicks() {
        for (String reason : new String[]{"Staff Watch: StaffA triggered a watcher alert.", "GoNuts evasion", "GoNuts restart", "Disconnected", "Quitting"})
            assertTrue(DisconnectRecovery.intentional(reason), reason);
        assertFalse(DisconnectRecovery.intentional("Click-Spam"));
        assertFalse(DisconnectRecovery.intentional("Timed out"));
    }

    @Test void repeatedKicksSlowDownThenStopAtCaps() {
        Map<String, Double> values = new HashMap<>();
        values.put("pace.click_gap_min_sec", .45);
        values.put("pace.click_gap_max_sec", .95);
        var first = DisconnectRecovery.slower(k -> values.getOrDefault(k, 0.0));
        assertEquals(.65, first.get("pace.click_gap_min_sec"), 1e-9);
        assertEquals(1.33, first.get("pace.click_gap_max_sec"), 1e-9);
        assertEquals(1, first.get("pace.human_steps"));
        assertEquals(0, first.get("pace.fast"));
        values.putAll(first);
        for (int i = 0; i < 30; i++) values.putAll(DisconnectRecovery.slower(k -> values.getOrDefault(k, 0.0)));
        assertEquals(1.5, values.get("pace.click_gap_min_sec"));
        assertEquals(3, values.get("pace.click_gap_max_sec"));
        assertEquals(values, DisconnectRecovery.slower(values::get));
        values.put("pace.command_max_sec", 12.0);
        assertEquals(12, DisconnectRecovery.slower(values::get).get("pace.command_max_sec"));
    }

    @Test void recoveryPersistsOnlyToAffectedClientAndPreservesOtherKeys() throws Exception {
        Path local = directory.resolve("first.json");
        Path other = directory.resolve("other.json");
        Files.writeString(local, "{\"unrelated\":\"keep\",\"pace.fast\":1}");
        Files.writeString(other, "{\"pace.fast\":1}");
        Tuning.loadLocalFrom(local);
        try {
            Tuning.setLocalBatch(Map.of("pace.fast", 0.0, "pace.click_gap_min_sec", .8));
            String saved = Files.readString(local);
            assertTrue(saved.contains("keep"));
            assertEquals("{\"pace.fast\":1}", Files.readString(other));
            Tuning.loadLocalFrom(local);
            assertEquals(.8, Tuning.get("pace.click_gap_min_sec"));
            assertEquals(0, Tuning.get("pace.fast"));
            assertTrue(Tuning.thisClientOnly("pace.click_gap_min_sec"));
        } finally { Tuning.loadLocalFrom(null); }
    }

    @Test void createsMissingLocalFileAndRejectsMalformedFileWithoutOverwritingIt() throws Exception {
        Path local = directory.resolve("new.json");
        Tuning.loadLocalFrom(local);
        try {
            Tuning.setLocalBatch(Map.of("pace.fast", 0.0));
            assertTrue(Files.isRegularFile(local));
            Files.writeString(local, "broken json");
            assertThrows(java.io.IOException.class, () -> Tuning.setLocalBatch(Map.of("pace.fast", 1.0)));
            assertEquals("broken json", Files.readString(local));
            assertEquals(0, Tuning.get("pace.fast"));
        } finally { Tuning.loadLocalFrom(null); }
    }

    @Test void laterSettingsSaveDoesNotLeakRecoveryIntoSharedConfig() throws Exception {
        Path shared = directory.resolve("tuning.json");
        Path local = directory.resolve("local.json");
        Files.writeString(shared, "{\"pace.click_gap_min_sec\":0.25}");
        Tuning.load(directory);
        Tuning.loadLocalFrom(local);
        try {
            Tuning.setLocalBatch(Map.of("pace.click_gap_min_sec", .9));
            Tuning.set("pace.recover_click_spam", 1);
            var json = new com.fasterxml.jackson.databind.ObjectMapper().readTree(Files.readString(shared));
            assertEquals(.25, json.get("pace.click_gap_min_sec").asDouble());
            assertEquals(.9, Tuning.get("pace.click_gap_min_sec"));
        } finally { Tuning.loadLocalFrom(null); }
    }
}
