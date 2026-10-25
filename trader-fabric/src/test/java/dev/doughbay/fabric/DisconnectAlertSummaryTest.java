package dev.doughbay.fabric;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class DisconnectAlertSummaryTest {
 @Test void timeoutDoesNotClaimStaffOrInventLagCause(){
  String detail=DisconnectAlertSummary.detail("donutsmp.net","Timed out.","Pacing unchanged.","Automatic reconnect is disabled.");
  assertEquals("GoNuts — CONNECTION TIMEOUT",DisconnectAlertSummary.title("Timed out."));
  assertTrue(detail.contains("Staff Watch trigger: No"));
  assertTrue(detail.contains("Reported reason: Timed out."));
  assertTrue(detail.contains("does not establish the underlying cause"));
  assertTrue(detail.contains("Next action: Automatic reconnect is disabled."));
  assertFalse(detail.contains("scheduled by Staff Watch"));
 }
 @Test void explicitClickSpamRemainsDistinctFromUnknownLoss(){
  assertEquals("GoNuts — CLICK-SPAM DISCONNECT",DisconnectAlertSummary.title("Kicked for Click-Spam"));
  assertTrue(DisconnectAlertSummary.explanation("Click-Spam").contains("explicitly reports click spam"));
  assertEquals("GoNuts — CONNECTION LOST",DisconnectAlertSummary.title("Connection reset"));
  assertEquals("GoNuts — CONNECTION LOST",DisconnectAlertSummary.title(null));
 }
 @Test void webhookAndDurableQueueUseSameClearFields(){
  var fields=DisconnectAlertSummary.fields("east.donutsmp.net","", "Existing settings retained.","Automatic reconnect was cancelled by the user.");
  String detail=DisconnectAlertSummary.detail("east.donutsmp.net","", "Existing settings retained.","Automatic reconnect was cancelled by the user.");
  for(var field:fields)assertTrue(detail.contains(field[0]+": "+field[1]));
  assertTrue(detail.contains("Reported reason: Not supplied"));
  assertTrue(detail.contains("cancelled by the user"));
 }
}
