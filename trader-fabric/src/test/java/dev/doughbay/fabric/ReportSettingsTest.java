package dev.doughbay.fabric;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
class ReportSettingsTest {
    @Test void multipleOrderCountIsIncludedInPrivateDiagnosticSettings() throws Exception {
        double before=Tuning.get("orders.max_per_market");
        try {
            Tuning.set("orders.max_per_market",0);String automatic=SettingsCode.exportForReport();
            Tuning.set("orders.max_per_market",2);String two=SettingsCode.exportForReport();
            assertNotEquals(automatic,two);SettingsCode.validateReportCode(two);
            assertTrue(SettingsCode.REPORT_KEYS.contains("orders.max_per_market"));
        } finally {Tuning.set("orders.max_per_market",before);}
    }
    @Test void focusToggleChangesNumericSnapshotWithoutExportingItsItemList() throws Exception {
        double before=Tuning.get("buy.focus");String items=Tuning.text("buy.focus_items");
        try {
            Tuning.set("buy.focus",0);String disabled=SettingsCode.exportForReport();
            Tuning.set("buy.focus",1);String enabled=SettingsCode.exportForReport();
            assertNotEquals(disabled,enabled,"Focus changes must be observable in report history");
            SettingsCode.validateReportCode(enabled);
            Tuning.setText("buy.focus_items","=moss_block");
            assertEquals(enabled,SettingsCode.exportForReport(),"Text item lists must remain excluded");
            assertTrue(SettingsCode.REPORT_KEYS.contains("buy.focus"));
            assertFalse(SettingsCode.REPORT_KEYS.contains("buy.focus_items"));
        } finally {Tuning.set("buy.focus",before);Tuning.setText("buy.focus_items",items);}
    }
    @Test void numericSnapshotHasNoTextAndIncludesAccountLimits() throws Exception {
        String code=SettingsCode.exportForReport();
        SettingsCode.validateReportCode(code);
        assertTrue(code.length()<12000);
        assertTrue(SettingsCode.REPORT_KEYS.contains("slots.max"));
        assertTrue(SettingsCode.REPORT_KEYS.contains("evasion.enabled"));
        assertFalse(SettingsCode.REPORT_KEYS.contains("webhook.url"));
        assertFalse(SettingsCode.REPORT_KEYS.contains("evasion.homes"));
    }
    @Test void rejectsSecretsEvenInsideAValidEncodedCode() throws Exception {
        for (String json : new String[]{
            "{\"v\":1,\"n\":{},\"t\":{\"webhook.url\":\"secret\"}}",
            "{\"v\":1,\"n\":{\"discord.id\":123},\"t\":{}}",
            "{\"v\":1,\"n\":{\"slots.max\":\"token\"},\"t\":{}}",
            "{\"v\":1,\"n\":{},\"t\":{},\"secret\":1}",
            "{\"v\":1,\"n\":{},\"t\":{\"secret\":\"hidden\"},\"t\":{}}",
            "{\"v\":1,\"n\":{},\"t\":{}} {\"secret\":1}"})
            assertThrows(IllegalArgumentException.class,()->SettingsCode.validateReportCode(SettingsCode.encode(json.getBytes(StandardCharsets.UTF_8))));
    }
    @Test void payloadFreezesSettingsForRetriesAndShowsThemInPreview() throws Exception {
        var counters=Map.<String,Object>of("state","IDLE","sqlite_errors",0,"uncompleted_desk_actions",0,"disconnect_notices",0,"evasion_notices",0,"recovered_purchase_pauses",0);
        var payload=new BugReportPayload("test","26.2","A reproducible failure","Expected behavior",counters);
        byte[] first=payload.json();
        assertArrayEquals(first,payload.json());
        assertEquals(payload.settingsCode(),new ObjectMapper().readTree(first).path("settingsCode").asText());
        assertTrue(payload.preview().contains(payload.settingsCode()));
        assertFalse(new ObjectMapper().readTree(payload.withoutSettings().json()).has("settingsCode"));
    }
    @Test void capabilityRequiresExactFormatAndBound() throws Exception {
        var mapper=new ObjectMapper();
        assertTrue(BugReportTransport.advertisesSettingsSupport(mapper.readTree("{\"settingsCode\":{\"supported\":true,\"format\":\"DB1-numeric-v1\",\"maxChars\":12000}}")));
        assertFalse(BugReportTransport.advertisesSettingsSupport(mapper.readTree("{}")));
    }
}
