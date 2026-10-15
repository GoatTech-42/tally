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
        var changed = new java.util.HashMap<>(form);changed.put("description","A different reproducible failure");
        WebReports.change(changed, preparation, delivery);
        assertFalse(JSON.readTree(WebReports.change(send, preparation, delivery)).path("ok").asBoolean(),"A stale preview cannot authorize a different report");
        awaitReady();
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
    private static void awaitReady() throws Exception {
        long deadline=System.nanoTime()+5_000_000_000L;
        while(JSON.readTree(WebReports.data()).path("busy").asBoolean()&&System.nanoTime()<deadline)Thread.sleep(10);
        assertFalse(JSON.readTree(WebReports.data()).path("busy").asBoolean());
    }
    @Test void oneClickSubmitPreparesAndSendsSelectedEvidenceOnceWithExplicitConsent() throws Exception {
        awaitReady();var calls=new java.util.concurrent.atomic.AtomicInteger();
        WebReports.Preparation preparation=(description,expected,include)->new WebReports.Prepared(
            new BugReportPayload("0.1.0","26.2",description,expected,BugReportDiagnostics.counters("")),include?BugReportLog.sanitize("[10:00:00] DoughBay automation [PAUSED]: details"):"");
        WebReports.Delivery delivery=(payload,key,log)->{
            calls.incrementAndGet();assertFalse(log.isEmpty());
            return CompletableFuture.completedFuture(new BugReportTransport.Result(true,"Sent","https://github.com/cody-raves/Gonuts/issues/2",0));
        };
        var submit=new java.util.HashMap<>(Map.of("action","submit","description","One click report example","expected","No extra preparation button","includeLog","true"));
        assertFalse(JSON.readTree(WebReports.change(submit,preparation,delivery)).path("ok").asBoolean());assertEquals(0,calls.get());
        submit.put("consent","true");WebReports.change(submit,preparation,delivery);awaitReady();
        assertEquals(1,calls.get());assertTrue(JSON.readTree(WebReports.data()).path("issueUrl").asText().endsWith("/2"));
        WebReports.change(submit,preparation,delivery);assertEquals(1,calls.get());
    }
}

