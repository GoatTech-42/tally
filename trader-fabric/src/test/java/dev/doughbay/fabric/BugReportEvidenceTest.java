package dev.doughbay.fabric;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class BugReportEvidenceTest {
    @Test void currentContextRemainsWithinSchemaTwoAttachmentLimits()throws Exception {
        String context="Current controller evidence: state=PAUSED reason_category=EXPOSURE_HOLD\n";
        String text=BugReportEvidence.combine(context,"event omitted\n".repeat(1000));
        assertTrue(text.startsWith(context));assertTrue(text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length<=8192);
        var p=new BugReportPayload("0.1.2","26.2","A reproducible failure","Trading continues",BugReportDiagnostics.counters(""),"");
        // Production sanitized logs are bounded to 100 lines; context adds fewer than 20.
        String real=BugReportEvidence.combine(context,BugReportLog.sanitize("[12:00:00] DoughBay automation [PAUSED]: private\n".repeat(500)));
        var json=new com.fasterxml.jackson.databind.ObjectMapper().readTree(p.json(real));
        assertEquals(2,json.path("schemaVersion").asInt());assertTrue(json.path("diagnosticLog").asText().startsWith(context));
        assertTrue(real.lines().count()<200);
    }
    @Test void fixedReasonCategoriesNeverEchoInput() {
        assertEquals("PERSISTENCE_REFUSED",BugReportReason.classify("A durable automation write was refused: token=secret"));
        assertEquals("FLIP_ALLOWANCE_EXHAUSTED",BugReportReason.classify("the budget you gave it is spent (player secret)"));
        assertEquals("UNCLASSIFIED",BugReportReason.classify("player secret 123,456"));
    }
}
