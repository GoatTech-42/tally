package dev.doughbay.fabric;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class DiagnosticTimeoutReasonTest {
 @Test void actualGuiTimeoutCauseSurvivesActionEvidenceInsteadOfBecomingUnclassified(){
  String reason=BugReportReason.classify("No confirmation GUI appeared within 7 seconds; last dispatched command omitted");
  assertEquals("CONFIRMATION_GUI_TIMEOUT",reason);
  var trace=new ActionTrace();trace.record(ActionTrace.Scope.DRIVER,ActionTrace.Event.ABORTED,AutomatedExecutionDriver.OperationIntent.LIST,reason,64,1000,0,1000);
  String evidence=trace.report(2000);assertTrue(evidence.contains("reason=CONFIRMATION_GUI_TIMEOUT"),evidence);
  assertEquals(reason,DecisionTrace.actionReason(reason).name());
 }
}
