package dev.doughbay.fabric;

import org.junit.jupiter.api.Test;
import java.util.Locale;
import java.util.HashSet;
import static org.junit.jupiter.api.Assertions.*;

class OutbidDecisionContractTest {
    @Test void everyReportSafeNumericSettingHasAClosedDecisionCode() throws Exception {
        var codes=new HashSet<String>();
        try(var in=DecisionTrace.class.getResourceAsStream("/report-decision-contract.json")) {
            for(var code:new com.fasterxml.jackson.databind.ObjectMapper().readTree(in).path("decisionCodes")) codes.add(code.asText());
        }
        for(String key:SettingsCode.REPORT_KEYS) assertTrue(codes.contains("SET_"+key.toUpperCase(Locale.ROOT).replace('.', '_')),key);
        assertTrue(DecisionTrace.valid("dec v=1 at=123 scope=CONTROLLER event=OBSERVATION code=SET_ORDERS_OUTBID_PCT reason=NONE market=NONE requested=1000 selected=3000 price=1000 limit=-1"));
        assertFalse(DecisionTrace.valid("dec v=1 at=123 scope=CONTROLLER event=OBSERVATION code=SET_ORDERS_OUTBID_PCT_UNKNOWN reason=NONE market=NONE requested=1000 selected=3000 price=1000 limit=-1"));
    }
}
