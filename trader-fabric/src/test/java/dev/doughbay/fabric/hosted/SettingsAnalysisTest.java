package dev.doughbay.fabric.hosted;

import dev.doughbay.fabric.SettingsCode;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SettingsAnalysisTest {
    @Test void hourlyCaptureKeepsTheObservationTimeAndDoesNotPollSettingsOnEveryTick() {
        var analysis = new SettingsAnalysis(); var reads = new AtomicInteger();
        java.util.function.Supplier<String> code = () -> { reads.incrementAndGet(); return "DB1-ABC"; };
        analysis.capture("fixture", true, 1000, code);
        var first = analysis.pending("fixture", 1, true, true, 1000);
        assertEquals(1000, first.path("sampledAt").asLong());
        analysis.sent("fixture", 1, 1000);
        analysis.capture("fixture", true, 1000 + SettingsAnalysis.HOUR - 1, code);
        assertEquals(1, reads.get());
        assertNull(analysis.pending("fixture", 1, true, true, 2000));
        analysis.capture("fixture", true, 1000 + SettingsAnalysis.HOUR, code);
        assertEquals(2, reads.get());
        assertEquals(1000 + SettingsAnalysis.HOUR,
                analysis.pending("fixture", 1, true, true, 1000 + SettingsAnalysis.HOUR).path("sampledAt").asLong());
    }

    @Test void negotiationSharingAccountAndAgeAreRequiredAndUnsentFramesCanRetry() {
        var analysis = new SettingsAnalysis(); analysis.capture("fixture", true, 1000, () -> "DB1-ABC");
        assertNull(analysis.pending("fixture", 1, false, true, 1000));
        assertNull(analysis.pending("fixture", 1, true, false, 1000));
        assertNull(analysis.pending("other", 1, true, true, 1000));
        assertNull(analysis.pending("fixture", 1, true, true, 999));
        assertNull(analysis.pending("fixture", 1, true, true, 1000 + SettingsAnalysis.HOUR));
        assertNotNull(analysis.pending("fixture", 1, true, true, 1100));
        assertNotNull(analysis.pending("fixture", 1, true, true, 1200));
        analysis.sent("other", 1, 1000);
        assertNotNull(analysis.pending("fixture", 1, true, true, 1300));
        analysis.sent("fixture", 1, 1000);
        assertNull(analysis.pending("fixture", 1, true, true, 1400));
        assertNotNull(analysis.pending("fixture", 2, true, true, 1500), "Reconnect resends for server deduplication");
    }

    @Test void disableAndAccountChangesCannotReuseAnotherAccountsSnapshot() {
        var analysis = new SettingsAnalysis(); analysis.capture("first", true, 1000, () -> "DB1-ABC");
        analysis.capture("first", false, 1100, () -> fail("Disabled sharing must not capture settings"));
        assertNull(analysis.pending("first", 1, true, true, 1200));
        analysis.capture("second", true, 1300, () -> "DB1-DEF");
        assertNull(analysis.pending("first", 1, true, true, 1400));
        assertEquals("DB1-DEF", analysis.pending("second", 1, true, true, 1400).path("code").asText());
        analysis.clear(); assertNull(analysis.pending("second", 1, true, true, 1500));
    }

    @Test void strictNegotiationRejectsCoercionsAndOverflow() {
        for (String json : new String[]{"{}", "{\"settingsAnalysisSchema\":\"1\"}",
                "{\"settingsAnalysisSchema\":1.0}", "{\"settingsAnalysisSchema\":4294967297}"})
            assertDoesNotThrow(() -> assertFalse(SettingsAnalysis.negotiated(DashboardProtocol.JSON.readTree(json))));
        assertTrue(SettingsAnalysis.negotiated(DashboardProtocol.JSON.createObjectNode().put("settingsAnalysisSchema", 1)));
        assertTrue(DashboardProtocol.CAPABILITIES.contains("settings_analysis"));
    }

    @Test void wireUsesOnlyTheExistingNumericReportCodeAndCopiesCannotMutateStoredSnapshot() {
        var analysis = new SettingsAnalysis(); String code = SettingsCode.exportForReport();
        analysis.capture("private-local-identity", true, 1000, () -> code);
        var frame = analysis.pending("private-local-identity", 1, true, true, 1000);
        assertEquals(3, frame.size()); assertEquals(code, frame.path("code").asText());
        assertFalse(frame.toString().contains("private-local-identity"));
        frame.put("code", "modified");
        assertEquals(code, analysis.pending("private-local-identity", 1, true, true, 1000).path("code").asText());
        analysis.capture("other", true, 2000, () -> "secret or invalid code");
        assertNull(analysis.pending("other", 1, true, true, 2000));
    }
}
