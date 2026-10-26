package dev.doughbay.fabric;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class AutomaticReportConsentTest {
    @TempDir Path root;
    @AfterEach void clear(){Tuning.loadLocalFrom(null);}
    @Test void legacyConsentCannotEnableRetiredReporting() throws Exception {
        Path local=root.resolve("doughbay-local.json");
        Tuning.set("reports.auto_critical",1);
        Tuning.loadLocalFrom(local);
        assertEquals(0,Tuning.get("reports.auto_critical"));assertTrue(Tuning.thisClientOnly("reports.auto_critical"));
        Tuning.set("reports.auto_critical",1);
        assertTrue(Files.readString(local).contains("reports.auto_critical"));
        Tuning.loadLocalFrom(local);assertEquals(0,Tuning.get("reports.auto_critical"));
        Tuning.loadLocalFrom(root.resolve("another-install.json"));assertEquals(0,Tuning.get("reports.auto_critical"));
    }
    @Test void deletedLocalConsentTurnsAutomaticReportingOff() throws Exception {
        Path local=root.resolve("doughbay-local.json");Tuning.loadLocalFrom(local);Tuning.set("reports.auto_critical",1);
        Files.delete(local);Tuning.loadLocalFrom(local);assertEquals(0,Tuning.get("reports.auto_critical"));
    }
    @Test void importingAnotherPersonsSettingsCannotEnableReporting() throws Exception {
        Tuning.loadLocalFrom(root.resolve("doughbay-local.json"));
        var result=SettingsCode.apply(SettingsCode.encode("{\"v\":1,\"n\":{\"reports.auto_critical\":1,\"liveness.alert\":1}}".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        assertTrue(result.ok());assertEquals(0,Tuning.get("reports.auto_critical"));assertTrue(result.kept()>0);
        assertTrue(SettingsCode.staysBehind("reports.auto_critical"));
    }
}
