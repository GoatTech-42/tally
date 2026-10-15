package dev.doughbay.fabric;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import static org.junit.jupiter.api.Assertions.*;

class WebReportsTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    @Test void reviewIsRequiredAndRetriesReuseFrozenBodyWhileOldPreviewsCannotSend() throws Exception {
        var calls = new ArrayList<String>();
        var response = new CompletableFuture<BugReportTransport.Result>();
        WebReports.Delivery delivery = (payload, key, log) -> { calls.add(key + payload.preview() + log); return response; };
        WebReports.Preparation preparation = (description, expected, include) -> new WebReports.Prepared(
            new BugReportPayload("0.1.0", "26.2", description, expected, BugReportDiagnostics.counters("")), "");
        var form = Map.of("action", "prepare", "description", "A reproducible failure", "expected", "Trading should continue");
        WebReports.change(form, preparation, delivery);
        long deadline = System.nanoTime() + 5_000_000_000L;
        while (JSON.readTree(WebReports.data()).path("busy").asBoolean() && System.nanoTime() < deadline) Thread.sleep(10);
        var ready = JSON.readTree(WebReports.data()); assertFalse(ready.path("busy").asBoolean());
        String id = ready.path("id").asText(); assertTrue(ready.path("preview").asText().contains("A reproducible failure"));
        assertTrue(calls.isEmpty());
        assertFalse(JSON.readTree(WebReports.change(Map.of("action","send","id",id),preparation,delivery)).path("ok").asBoolean());
        var send = Map.of("action","send","id",id,"reviewed","true");
        WebReports.change(send, preparation, delivery); assertEquals(1,calls.size());
        WebReports.change(send, preparation, delivery); assertEquals(1,calls.size(), "No concurrent duplicate send");
        response.complete(new BugReportTransport.Result(false,"Retry later","",0));
        WebReports.change(send, preparation, (payload,key,log) -> {
            assertEquals(calls.getFirst(), key + payload.preview() + log);
            return CompletableFuture.completedFuture(new BugReportTransport.Result(true,"Sent","https://github.com/cody-raves/Gonuts/issues/1",0));
        });
        assertTrue(JSON.readTree(WebReports.data()).path("issueUrl").asText().endsWith("/1"));
        WebReports.change(send, preparation, delivery); assertEquals(1,calls.size(), "Already sent draft is not resubmitted");
        WebReports.change(form, preparation, delivery);
        assertFalse(JSON.readTree(WebReports.change(send, preparation, delivery)).path("ok").asBoolean(),"A stale preview cannot authorize a different report");
    }
    @Test void localNoticeWorksWithoutUploadConsentAndDismissesOnlyMatchingIncident() throws Exception {
        var healthy = new AutomaticReportDetector.Observation(false,true,true,false,true,false,false,null);
        var failed = new AutomaticReportDetector.Observation(false,true,true,false,true,true,false,AutomaticReportDetector.Signature.RETAIL_CHILD_ID_COLLISION);
        WebReports.observe(healthy,0,"0.1.0"); WebReports.observe(failed,1,"0.1.0"); WebReports.observe(failed,120001,"0.1.0");
        var notice = JSON.readTree(WebReports.data()); assertTrue(notice.path("alert").asBoolean());
        WebReports.change(Map.of("action","dismiss","incident","wrong"));
        assertTrue(JSON.readTree(WebReports.data()).path("alert").asBoolean());
        WebReports.change(Map.of("action","dismiss","incident",notice.path("incident").asText()));
        WebReports.observe(failed,130001,"0.1.0");
        assertFalse(JSON.readTree(WebReports.data()).path("alert").asBoolean(),"Repeated ticks must not re-open the same notice");
    }
}

