package dev.doughbay.fabric;

import dev.doughbay.fabric.hosted.DashboardProtocol;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DiagnosticHistoryTest {
    private static final long NOW=1_000_000;
    private ObjectNode welcome(){return DashboardProtocol.JSON.createObjectNode().put("diagnosticHistorySchema",1)
            .put("swarmClientId","sc_"+"a".repeat(32)).put("diagnosticReportToken","drt_"+"b".repeat(40)).put("diagnosticReportExpiresAt",NOW+86_400_000);}
    private DiagnosticHistory history(){var h=new DiagnosticHistory();h.welcome(welcome(),NOW);h.token(welcome().put("token","drt_"+"b".repeat(40)).put("expiresAt",NOW+86_400_000),NOW);return h;}
    private void capture(DiagnosticHistory h,long now,String state){h.capture("fixture",true,now,()->"ctx captured_at_ms="+now+"\nctx state="+state+"\n",SettingsCode::exportForReport);}
    @Test void unnegotiatedOrDisabledNeverCapturesOrRunsSuppliers(){
        var h=new DiagnosticHistory();var calls=new AtomicInteger();
        h.capture("fixture",true,NOW,()->{calls.incrementAndGet();return "ctx state=SCANNING";},SettingsCode::exportForReport);
        assertNull(h.batch("fixture",true,NOW));assertEquals(0,calls.get());
        h.welcome(welcome(),NOW);capture(h,NOW,"SCANNING");assertNotNull(h.batch("fixture",true,NOW));
        assertNull(h.batch("fixture",false,NOW));assertEquals("",h.reportToken("fixture",false,NOW));
    }
    @Test void retriesUntilDeliveredSequenceAcknowledgedAndCannotAckAnotherSessionOrFuture(){
        var h=history();capture(h,NOW,"SCANNING");var b=h.batch("fixture",true,NOW);
        assertEquals(b,h.batch("fixture",true,NOW));
        assertEquals("diagnostic_history",b.path("type").asText());assertEquals(4,b.size());assertFalse(b.toString().contains("fixture"));
        var ack=DashboardProtocol.JSON.createObjectNode().put("sessionId",b.path("sessionId").asText()).put("throughSequence",1);
        h.acknowledge(ack);assertNotNull(h.batch("fixture",true,NOW)); // never sent
        h.sent(b);h.acknowledge(ack.deepCopy().put("throughSequence",999));assertNotNull(h.batch("fixture",true,NOW));
        h.acknowledge(ack.deepCopy().put("sessionId","other"));assertNotNull(h.batch("fixture",true,NOW));
        h.acknowledge(ack);assertNull(h.batch("fixture",true,NOW));
    }
    @Test void newerGameThreadCaptureSurvivesAnOlderWorkerTimestamp(){
        var h=history();capture(h,NOW+10,"SCANNING");
        // The worker captured its clock before the game thread queued this event.
        assertNull(h.batch("fixture",true,NOW));
        assertEquals(0,h.droppedEvents());
        var batch=h.batch("fixture",true,NOW+20);assertNotNull(batch);
        assertEquals(1,batch.path("events").get(0).path("sequence").asLong());
        assertEquals(NOW+10,batch.path("events").get(0).path("observedAt").asLong());
    }
    @Test void excludesFreeTextUnknownFieldsDuplicatesAndCredentials(){
        String safe=DiagnosticHistory.sanitizeContext("[CHAT] private-user: secret\nctx password=secret\nctx state=BUYING\nctx state=SCANNING\nctx fault_type=secret\nctx armed=true\nctx armed=anything\n/home/private Bearer SECRET");
        assertEquals("ctx state=BUYING\nctx armed=true\n",safe);
    }
    @Test void samplerDetectsChangesButIgnoresOnlyAgeProgressAndHeartbeatsEveryMinute(){
        var h=history();capture(h,NOW,"SCANNING");var b=h.batch("fixture",true,NOW);h.sent(b);h.acknowledge(b.deepCopy().put("throughSequence",1));
        capture(h,NOW+4999,"BUYING");assertNull(h.batch("fixture",true,NOW+4999));
        capture(h,NOW+5000,"SCANNING");assertNull(h.batch("fixture",true,NOW+5000));
        capture(h,NOW+30000,"BUYING");assertNotNull(h.batch("fixture",true,NOW+30000));
        var c=h.batch("fixture",true,NOW+30000);h.sent(c);h.acknowledge(c.deepCopy().put("throughSequence",2));
        capture(h,NOW+90000,"BUYING");assertNotNull(h.batch("fixture",true,NOW+90000));
    }
    @Test void settingsChangeIsCapturedWithItsObservedVersionWithoutHourlyWait(){
        var h=history();double before=Tuning.get("orders.pages");
        try {capture(h,NOW,"SCANNING");String original=h.batch("fixture",true,NOW).path("events").get(0).path("settingsCode").asText();
            Tuning.set("orders.pages",before==1?2:1);capture(h,NOW+5000,"SCANNING");
            var events=h.batch("fixture",true,NOW+5000).path("events");assertEquals(2,events.size());
            assertNotEquals(original,events.get(1).path("settingsCode").asText());assertEquals(NOW+5000,events.get(1).path("observedAt").asLong());
        } finally {Tuning.set("orders.pages",before);}
    }
    @Test void outboxAndWireAreBoundedAndEvictionIsExplicit(){
        var h=history();for(int i=0;i<270;i++)capture(h,NOW+i*30000L,i%2==0?"BUYING":"SCANNING");
        var b=h.batch("fixture",true,NOW+270*30000L);assertEquals(14,h.droppedEvents());
        assertTrue(b.path("events").size()<=8);assertTrue(b.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length<=DiagnosticHistory.MAX_BATCH_BYTES);
        assertEquals(15,b.path("events").get(0).path("sequence").asLong());
    }
    @Test void retentionDisconnectAndAccountSwitchCannotLeakPriorClientHistory(){
        var h=history();capture(h,NOW,"SCANNING");assertFalse(h.reportToken("fixture",true,NOW).isEmpty());
        h.disconnected();assertEquals("",h.reportToken("fixture",true,NOW));assertNull(h.batch("fixture",true,NOW));
        h.welcome(welcome(),NOW+1000);assertNotNull(h.batch("fixture",true,NOW+1000));
        assertNull(h.batch("fixture",true,NOW+DiagnosticHistory.RETENTION_MS+1));
        capture(h,NOW+1000,"BUYING");h.capture("other",true,NOW+2000,()->"ctx state=BUYING",SettingsCode::exportForReport);
        assertNull(h.batch("other",true,NOW+2000));assertEquals("",h.reportToken("other",true,NOW+2000));
    }
    @Test void rejectsUnsafeSettingsAndProducerFailureDoesNotEscape(){
        var h=history();assertDoesNotThrow(()->h.capture("fixture",true,NOW,()->{throw new IllegalStateException("private");},SettingsCode::exportForReport));
        h.capture("fixture",true,NOW+5000,()->"ctx state=SCANNING",()->"DB1-SECRET");assertNull(h.batch("fixture",true,NOW+5000));
    }
    @Test void nativeControllerEvidenceProducesBackendCompatibleFixture() throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
        var controller=new dev.doughbay.fabric.automation.AutomationSessionController(new AutomatedExecutionDriver());
        var h=history();h.capture("fixture",true,NOW,()->FaultTrace.INSTANCE.report(NOW)+controller.bugReportContext(NOW),SettingsCode::exportForReport);
        var frame=h.batch("fixture",true,NOW);assertNotNull(frame);
        String context=frame.path("events").get(0).path("context").asText();
        assertTrue(context.lines().count()<=16);assertTrue(context.contains("ctx state="));assertTrue(context.contains("fault_type="));
        assertEquals(context,DiagnosticHistory.sanitizeContext(context));
        if("1".equals(System.getenv("GONUTS_DIAGNOSTIC_FIXTURE"))){
            var file=java.nio.file.Path.of(System.getenv("LOCALAPPDATA"),"GoNuts","diagnostic-history-build","native-client-fixture.json");
            java.nio.file.Files.createDirectories(file.getParent());java.nio.file.Files.writeString(file,frame.toPrettyString());
        }
    }

    @Test void reportTokenLifetimeUsesServerDurationWhenClocksDiffer(){
        var h=history();var token=welcome().put("token","drt_"+"c".repeat(43)).put("issuedAt",9_000_000_000L).put("expiresAt",9_000_000_000L+86_400_000);
        h.token(token,NOW);capture(h,NOW,"SCANNING");assertFalse(h.reportToken("fixture",true,NOW+1000).isEmpty());
        assertEquals("",h.reportToken("fixture",true,NOW+86_400_000));
    }

}
