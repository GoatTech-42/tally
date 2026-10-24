package dev.doughbay.fabric;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ReportSystemCoverageTest {
    @Test void bundledReasonContractMatchesTheActualClosedReasonEnum() throws Exception {
        try(var in=DecisionTrace.class.getResourceAsStream("/report-decision-contract.json")) {
            var json=new com.fasterxml.jackson.databind.ObjectMapper().readTree(in);
            var declared=new java.util.TreeSet<String>();json.path("decisionReasons").forEach(v->declared.add(v.asText()));
            var actual=new java.util.TreeSet<String>();for(var reason:DecisionTrace.Reason.values())actual.add(reason.name());
            assertEquals(actual,declared);
        }
    }
    @Test void sizingInputsSurviveReport260StyleRejectionAndDriverFlood() {
        var trace=new DecisionTrace();long at=1_790_000_000_000L;
        trace.record(ActionTrace.Scope.DESK,ActionTrace.Event.OBSERVATION,DecisionTrace.Code.ORDER_DEPTH,
                DecisionTrace.Reason.DEPTH_CAP,"minecraft:cooked_beef",16,3,3317,200000000,at++);
        trace.record(ActionTrace.Scope.DESK,ActionTrace.Event.OBSERVATION,DecisionTrace.Code.ORDER_SALES,
                DecisionTrace.Reason.ADAPTIVE_BATCH,"minecraft:cooked_beef",16,1,0,1,at++);
        trace.record(ActionTrace.Scope.DESK,ActionTrace.Event.REQUEST,DecisionTrace.Code.ORDER_PLAN,
                DecisionTrace.Reason.SINGLE_ALLOTMENT,"minecraft:cooked_beef",1024,1,3317,200000000,at++);
        for(int i=0;i<3000;i++) {
            trace.record(ActionTrace.Scope.DESK,ActionTrace.Event.REFUSED,DecisionGate.GATE_ABOVE_WHAT_IT_REALLY_TRADES_AT,
                    DecisionTrace.Reason.UNCLASSIFIED,i%2==0?"minecraft:beacon":"minecraft:diamond_block",1024,0,-1,-1,at++);
            trace.record(ActionTrace.Scope.DRIVER,ActionTrace.Event.PHASE,ActionTrace.Code.API,
                    DecisionTrace.Reason.NONE,"minecraft:obsidian",-1,i%2,2800,5482,at++);
        }
        String report=BugReportEvidence.assemble(CONTEXT,BugReportLog.UNAVAILABLE,trace,
                ReportTopic.infer("Bot stuck on buying beacons","Dosent make orders"),at);
        assertTrue(report.contains("code=ORDER_PLAN reason=SINGLE_ALLOTMENT market=minecraft:cooked_beef requested=1024 selected=1"));
        assertTrue(report.contains("code=ORDER_DEPTH"));assertTrue(report.contains("code=ORDER_SALES"));
        assertTrue(report.getBytes(StandardCharsets.UTF_8).length<=BugReportLog.MAX_BYTES);
        assertTrue(AutomaticReportOutbox.validExcerpt(report));
        // Pinned decisions retain their real timestamps and expire; an old plan
        // must not be presented as current evidence indefinitely.
        assertFalse(trace.report(at+16*60_000L,ReportTopic.FLIPS,11000).contains("code=ORDER_PLAN"));
    }
    private static final String CONTEXT="Controller context (fixed keys, no identities)\nctx state=BUYING\nctx driver_action=BUY\n";
    private void event(DecisionTrace trace,ActionTrace.Scope scope,long at){
        trace.record(scope,ActionTrace.Event.REFUSED,ActionTrace.Code.API,DecisionTrace.Reason.IO_FAILURE,"NONE",-1,-1,-1,-1,at);
    }
    @Test void generatedReportRetainsAllSubsystemsDespiteARelevantTopicBurst() {
        var trace=new DecisionTrace();long at=1_790_000_000_000L;
        for(var scope:ActionTrace.Scope.values())event(trace,scope,at++);
        for(int i=0;i<3000;i++)trace.record(ActionTrace.Scope.DESK,ActionTrace.Event.REQUEST,DecisionTrace.Code.ORDER_PLAN,
                DecisionTrace.Reason.ADAPTIVE_BATCH,"minecraft:diamond",1024,i,6300,1000000,at++);
        String report=BugReportEvidence.assemble(CONTEXT,BugReportLog.UNAVAILABLE,trace,ReportTopic.FLIPS,at);
        for(var scope:ActionTrace.Scope.values())assertTrue(report.contains("scope="+scope+" "),scope.name());
        assertTrue(report.contains("evicted="));assertTrue(report.contains("history_omitted="));
        assertTrue(report.getBytes(StandardCharsets.UTF_8).length<=BugReportLog.MAX_BYTES);
        assertTrue(report.lines().count()<=200);assertTrue(AutomaticReportOutbox.validExcerpt(report));
    }
    @Test void actualManualAndAutomaticPayloadsRetainTheTinyOrderDecisionAndWatch() throws Exception {
        var trace=new DecisionTrace();
        trace.record(ActionTrace.Scope.DESK,ActionTrace.Event.REQUEST,DecisionTrace.Code.ORDER_PLAN,
                DecisionTrace.Reason.SINGLE_ALLOTMENT,"minecraft:diamond",1024,1,6300,200000000,1_790_000_000_000L);
        trace.record(ActionTrace.Scope.MARKET,ActionTrace.Event.REQUEST,DecisionTrace.Code.WATCH_SELECTED,
                DecisionTrace.Reason.NONE,"minecraft:beacon",24,1,-1,-1,1_790_000_000_001L);
        String report=BugReportEvidence.assemble(CONTEXT,BugReportLog.UNAVAILABLE,trace,
                ReportTopic.infer("Stuck on buying item","Tries to buy beacon and does not create orders"),1_790_000_000_002L);
        assertTrue(report.contains("market=minecraft:diamond requested=1024 selected=1 price=6300 limit=200000000"));
        assertTrue(report.contains("reason=SINGLE_ALLOTMENT"));assertTrue(report.contains("code=WATCH_SELECTED"));
        var manual=new BugReportPayload("0.1.2","26.2","Stuck on buying item","Does not create orders",BugReportDiagnostics.counters(""),"");
        String json=new String(manual.json(report),StandardCharsets.UTF_8);
        assertTrue(json.contains("requested=1024 selected=1"));
        var fixtures=java.nio.file.Path.of("build/report-fixtures");java.nio.file.Files.createDirectories(fixtures);
        java.nio.file.Files.writeString(fixtures.resolve("report257-system-evidence.json"),json,StandardCharsets.UTF_8);
        var meta=AutomaticReportMetadata.of("0.1.2","26.2",new AutomaticReportDetector.Candidate(AutomaticReportDetector.Failure.PERSISTENCE_WRITER_FAILED,AutomaticReportDetector.Signature.PERSISTENCE_GENERIC_FAILURE));
        var automatic=new BugReportPayload("0.1.2","26.2",meta.description(),meta.expected(),BugReportDiagnostics.counters(""),"");
        var pending=new AutomaticReportOutbox.Pending("fixture-report-system",meta.fingerprint(),new String(automatic.json(report,meta),StandardCharsets.UTF_8),1_790_000_000_002L,0,1_790_000_000_002L);
        assertTrue(pending.body().contains("code=WATCH_SELECTED"));
    }
    @Test void expectedFieldAffectsPriorityButCannotExcludeOtherSubsystemEvidence() {
        assertEquals(ReportTopic.FLIPS,ReportTopic.infer("Stuck on buying item","Does not create orders"));
        var trace=new DecisionTrace();event(trace,ActionTrace.Scope.HIVE,100);event(trace,ActionTrace.Scope.PERSISTENCE,101);
        String report=BugReportEvidence.assemble(CONTEXT,BugReportLog.UNAVAILABLE,trace,ReportTopic.AUCTION,102);
        assertTrue(report.contains("scope=HIVE"));assertTrue(report.contains("scope=PERSISTENCE"));
    }
    @Test void fileSanitizerRetainsDecisionFactsAndRejectsPrivateCanaries() {
        String line="dec v=1 at=100 scope=DESK event=REQUEST code=ORDER_PLAN reason=SINGLE_ALLOTMENT market=minecraft:diamond requested=1024 selected=1 price=6300 limit=200000000";
        assertTrue(DecisionTrace.valid(line));
        String safe=BugReportLog.sanitize("[12:00:00] [Render thread/INFO] GoNuts decision "+line+"\n[CHAT] PRIVATE_PLAYER\nBearer PRIVATE_TOKEN\nC:\\Users\\PRIVATE\\home");
        assertTrue(safe.contains(line));assertFalse(safe.contains("PRIVATE"));
        assertFalse(DecisionTrace.valid(line.replace("SINGLE_ALLOTMENT","PRIVATE_PLAYER")));
        assertFalse(DecisionTrace.valid(line+" token=PRIVATE_TOKEN"));
        assertFalse(DecisionTrace.validCoverage("cov v=1 captured_at=9999999999999999999 first_at=0 last_at=0 retained=0 omitted=0 evicted=0"));
    }
    @Test void settingsObservationRecordsKnownNumericChangesWithoutTextSettings() {
        var trace=new DecisionTrace();double before=Tuning.get("orders.bid_stacks");
        try {
            trace.observeSettings(1000);Tuning.set("orders.bid_stacks",before==16?15:16);trace.observeSettings(2100);
            String report=trace.report(2200,ReportTopic.GENERAL,11000);
            assertTrue(report.contains("code=SET_ORDERS_BID_STACKS"));assertTrue(report.contains("price=1000"));
            assertFalse(report.contains("webhook"));
        }finally{Tuning.set("orders.bid_stacks",before);}
    }
    @Test void separateOrderLimitHasAnExplicitClosedChangeToken() {
        var trace=new DecisionTrace();double before=Tuning.get("orders.max_per_market");
        try {
            Tuning.set("orders.max_per_market",0);trace.observeSettings(1000);
            Tuning.set("orders.max_per_market",2);trace.observeSettings(2100);
            String report=trace.report(2200,ReportTopic.GENERAL,11000);
            assertTrue(report.contains("code=SET_ORDERS_MAX_PER_MARKET"));
            assertTrue(report.contains("selected=2000"));
        } finally {Tuning.set("orders.max_per_market",before);}
    }
}

