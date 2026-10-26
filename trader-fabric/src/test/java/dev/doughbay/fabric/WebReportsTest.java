package dev.doughbay.fabric;
import dev.doughbay.fabric.hosted.DashboardProtocol;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class WebReportsTest {
    @Test void legacyReportActionsCannotCreatePreviewOrSubmission() throws Exception {
        for(String action:new String[]{"prepare","submit","send","dismiss"}) {
            var result=DashboardProtocol.JSON.readTree(WebReports.change(Map.of("action",action,"consent","true","reviewed","true")));
            assertFalse(result.path("ok").asBoolean());assertTrue(result.path("retired").asBoolean());
            var data=DashboardProtocol.JSON.readTree(WebReports.data());assertFalse(data.path("alert").asBoolean());
            assertFalse(data.has("preview"));assertFalse(data.has("id"));assertFalse(data.has("issueUrl"));
        }
    }
    @Test void repeatedFailureObservationsDoNotCreateReportPrompts() throws Exception {
        var failure=new AutomaticReportDetector.Observation(true,true,true,false,true,true,false,AutomaticReportDetector.Signature.RETAIL_CHILD_ID_COLLISION);
        for(long at:new long[]{1,120001,300001})WebReports.observe(failure,at,"legacy");
        var data=DashboardProtocol.JSON.readTree(WebReports.data());assertFalse(data.path("alert").asBoolean());assertTrue(data.path("retired").asBoolean());
    }
}
