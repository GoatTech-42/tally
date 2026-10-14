package dev.doughbay.fabric;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;

class BugReportTransportTest {
    private static BugReportPayload payload() {
        return new BugReportPayload("0.1.1-staffwatch+26.2", "26.2", "A listing did not complete", "It should finish",
            BugReportDiagnostics.counters("DoughBay automation [SCANNING]: private message\nSQLiteException: private"));
    }
    @Test void exactReceiverSchemaContainsOnlyValidatedFields() throws Exception {
        var body = new ObjectMapper().readTree(payload().json());
        assertEquals(6, body.size()); assertEquals(1, body.path("schemaVersion").asInt());
        assertEquals("26.2", body.path("minecraftVersion").asText());
        assertEquals(6, body.path("diagnostics").size()); assertEquals(1, body.path("diagnostics").path("sqlite_errors").asLong());
        assertFalse(new String(payload().json(), java.nio.charset.StandardCharsets.UTF_8).contains("private"));
    }
    @Test void missingStateIsUppercaseUnknown() {
        assertEquals("UNKNOWN", BugReportDiagnostics.counters("").get("state"));
        var fromChat = BugReportDiagnostics.counters("[CHAT] DoughBay automation [PAUSED]: SQLiteException");
        assertEquals("UNKNOWN", fromChat.get("state"));
        assertEquals(0L, fromChat.get("sqlite_errors"));
    }
    @Test void wrongOriginNeverAuthorizesCredentialSharing() {
        assertFalse(BugReportTransport.mayUseToken("https://api.donutsmp.net", "secret"));
        assertFalse(BugReportTransport.mayUseToken("https://gonuts.at.attacker.net", "secret"));
        assertFalse(BugReportTransport.mayUseToken("https://user@gonuts.at", "secret"));
        assertFalse(BugReportTransport.mayUseToken("https://gonuts.at", ""));
        assertFalse(BugReportTransport.mayUseToken("https://gonuts.at", "secret\r\nheader"));
        assertTrue(BugReportTransport.mayUseToken("https://gonuts.at", "install-secret"));
        assertTrue(BugReportTransport.mayUseToken("http://gonuts.at", "install-secret"));
        var blocked = new BugReportTransport().send(payload(), "retry-key-123", "https://api.donutsmp.net", "official-key").join();
        assertFalse(blocked.filed()); assertFalse(blocked.message().contains("official-key"));
    }
    @Test void httpsFixedDestinationAndRetryKeyRemainStable() {
        var a = BugReportTransport.request(payload(), "01234567-1234-1234-1234-012345678901", "test-install");
        var b = BugReportTransport.request(payload(), "01234567-1234-1234-1234-012345678901", "test-install");
        assertEquals("https://gonuts.at/v1/bugs", a.uri().toString());
        assertEquals(a.headers().firstValue("Idempotency-Key"), b.headers().firstValue("Idempotency-Key"));
        assertThrows(IllegalArgumentException.class, () -> BugReportTransport.request(payload(), "bad", "test"));
    }
    @Test void successAndDuplicateReturnOnlyTrustedIssueLinks() {
        String body = "{\"reportId\":\"br_test\",\"issueUrl\":\"https://github.com/cody-raves/Gonuts/issues/123\",\"duplicate\":false}";
        assertTrue(BugReportTransport.interpret(201, body, "", 0).filed());
        assertTrue(BugReportTransport.interpret(200, body.replace("false", "true"), "", 0).message().contains("Already"));
        assertFalse(BugReportTransport.interpret(201, body.replace("github.com", "attacker.net"), "", 0).filed());
        assertFalse(BugReportTransport.interpret(201, "not json", "", 0).filed());
    }
    @Test void rateLimitAndUnavailableStatusesDoNotEchoServerContent() {
        var rate = BugReportTransport.interpret(429, "secret echoed by server", "120", 1000);
        assertEquals(121000, rate.retryAtMillis()); assertFalse(rate.message().contains("secret"));
        assertEquals(3601000, BugReportTransport.interpret(429, "", "invalid", 1000).retryAtMillis());
        assertEquals(61000, BugReportTransport.interpret(503, "private", "", 1000).retryAtMillis());
        assertTrue(BugReportTransport.interpret(503, "", "", 0).message().contains("temporarily"));
        assertTrue(BugReportTransport.interpret(401, "", "", 0).message().contains("token"));
    }
    @Test void schemaRejectsRawLogsUnknownFieldsAndNonIntegerCounters() {
        assertThrows(IllegalArgumentException.class, () -> new BugReportPayload("0.1.0", "26.2", "short", "yes", payload().diagnostics()));
        assertThrows(IllegalArgumentException.class, () -> new BugReportPayload("0.1.0", "26.2", "[12:00:00] test\n[12:00:01] test\n[12:00:02] test", "yes", payload().diagnostics()));
        var counters = new java.util.HashMap<>(payload().diagnostics()); counters.put("sqlite_errors", 1.5);
        assertThrows(IllegalArgumentException.class, () -> new BugReportPayload("0.1.0", "26.2", "Description long enough", "yes", counters));
        counters.put("sqlite_errors", 1L); counters.put("unknown", 1L);
        assertThrows(IllegalArgumentException.class, () -> new BugReportPayload("0.1.0", "26.2", "Description long enough", "yes", counters));
    }
}
