package dev.doughbay.fabric;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FaultTraceTest {
    @Test void capturedFaultRetainsStageTypeAndVerifiedFramesButNoPrivateMessage()throws Exception{
        var trace=new FaultTrace();
        var error=new NullPointerException("token=SECRET player=PRIVATE C:\\private\\settings.json");
        error.setStackTrace(new StackTraceElement[]{
            new StackTraceElement("dev.doughbay.fabric.MarketWatcher","runKeyless","MarketWatcher.java",670),
            new StackTraceElement("dev.doughbay.fabric.MarketWatcher","fakePrivateMethod","MarketWatcher.java",3),
            new StackTraceElement("private.Account","tokenSECRET","private.txt",5)});
        trace.record(FaultTrace.Stage.MARKET_KEYLESS,error,1000);
        String evidence=trace.report(1500);
        assertTrue(evidence.contains("fault_stage=MARKET_KEYLESS fault_type=NullPointerException fault_age_ms=500 fault_count=1"));
        assertTrue(evidence.contains("at dev.doughbay.fabric.MarketWatcher.runKeyless(MarketWatcher.java:670)"));
        for(String secret:new String[]{"SECRET","PRIVATE","settings.json","fakePrivateMethod","private.Account"})assertFalse(evidence.contains(secret));
        var payload=new BugReportPayload("0.1.2","26.2","A reproducible startup failure","Trading can continue",BugReportDiagnostics.counters(""),"");
        String combined=BugReportEvidence.combine("Controller context (fixed keys, no identities)\n"+evidence,BugReportLog.UNAVAILABLE);
        var json=new com.fasterxml.jackson.databind.ObjectMapper().readTree(payload.json(combined));
        assertEquals(2,json.path("schemaVersion").asInt());assertTrue(json.path("diagnosticLog").asText().contains(evidence));
        assertFalse(json.toString().contains("SECRET"));
    }
    @Test void countAndUnknownFaultsStayBoundedWithoutRetainingThrowable(){
        var trace=new FaultTrace();assertTrue(trace.report(1000).contains("fault_stage=NONE fault_type=NONE fault_age_ms=-1 fault_count=0"));
        for(int i=0;i<1000;i++)trace.record(FaultTrace.Stage.DRIVER_CLICK,new RuntimeException("secret"),1000+i);
        String s=trace.report(5000);assertTrue(s.contains("fault_count=1000"));assertTrue(s.contains("fault_type=OTHER_EXCEPTION"));
        assertTrue(s.lines().count()<=5);assertFalse(s.contains("secret"));assertTrue(s.length()<2000);
        assertTrue(trace.report(1).contains("fault_age_ms=-1"));
    }
    @Test void faultEvidenceIsInTheOutboundBodyAndTheFrozenAutomaticReport()throws Exception{
        var trace=new FaultTrace();trace.record(FaultTrace.Stage.DRIVER_COMMAND,new IllegalStateException("private-token"),1000);
        String evidence=BugReportEvidence.combine("Controller context (fixed keys, no identities)\n"+trace.report(1500),BugReportLog.UNAVAILABLE);
        var payload=new BugReportPayload("0.1.2","26.2","A command failed while trading","Trading resumes safely",BugReportDiagnostics.counters(""),"");
        var request=BugReportTransport.request(payload,"fixture-report-123","private-api-credential",evidence);
        var result=new java.util.concurrent.CompletableFuture<byte[]>();var bytes=new java.io.ByteArrayOutputStream();
        request.bodyPublisher().orElseThrow().subscribe(new java.util.concurrent.Flow.Subscriber<java.nio.ByteBuffer>(){
            public void onSubscribe(java.util.concurrent.Flow.Subscription s){s.request(Long.MAX_VALUE);}
            public void onNext(java.nio.ByteBuffer data){byte[] part=new byte[data.remaining()];data.get(part);bytes.writeBytes(part);}
            public void onError(Throwable error){result.completeExceptionally(error);}
            public void onComplete(){result.complete(bytes.toByteArray());}
        });
        var json=new com.fasterxml.jackson.databind.ObjectMapper();
        assertEquals(evidence,json.readTree(result.get(2,java.util.concurrent.TimeUnit.SECONDS)).path("diagnosticLog").asText());
        var metadata=AutomaticReportMetadata.of("0.1.2","26.2",new AutomaticReportDetector.Candidate(
                AutomaticReportDetector.Failure.PERSISTENCE_WRITER_FAILED,AutomaticReportDetector.Signature.PERSISTENCE_GENERIC_FAILURE));
        var automatic=new BugReportPayload("0.1.2","26.2",metadata.description(),metadata.expected(),BugReportDiagnostics.counters(""),"");
        assertEquals(evidence,json.readTree(automatic.json(evidence,metadata)).path("diagnosticLog").asText());
        assertFalse(bytes.toString(java.nio.charset.StandardCharsets.UTF_8).contains("private"));
        assertTrue(request.headers().firstValue("Authorization").isEmpty());
    }
    @Test void sharedCaptureRetainsFaultWhenTheHistoricalLogIsMissing()throws Exception{
        FaultTrace.INSTANCE.record(FaultTrace.Stage.MARKET_KEYLESS,new NullPointerException("private-user-message"),System.currentTimeMillis());
        String attachment=BugReportEvidence.readForReport(java.nio.file.Path.of("missing-fixture-directory","latest.log"));
        assertTrue(attachment.contains("fault_stage=MARKET_KEYLESS fault_type=NullPointerException"));
        assertTrue(attachment.contains("historical_counters_available=false"));
        assertFalse(attachment.contains("private-user-message"));
    }
}
