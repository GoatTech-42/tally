package dev.doughbay.fabric;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
class ReportLogAvailabilityTest {
 @TempDir Path root;
 @Test void unreadableLogDoesNotBlockReportOrExposeFilePath(){
  String result=BugReportLog.readForReport(root.resolve("private-player-name/latest.log"));
  assertEquals(BugReportLog.UNAVAILABLE,result);assertFalse(result.contains("private-player-name"));
  var payload=new BugReportPayload("0.1.0","26.2","Log unavailable example","Reporting should still work",BugReportDiagnostics.counters(""));
  assertDoesNotThrow(()->payload.json(result));
 }
 @Test void automaticAttachmentStillUsesThePrivacyFilter() throws Exception {
  Path log=root.resolve("latest.log");Files.writeString(log,"[12:00:00] [CHAT] PrivatePlayer my password is secret\n[12:00:01] DoughBay automation [PAUSED]: private-user");
  String result=BugReportLog.readForReport(log);assertTrue(result.contains("automation state=PAUSED"));assertFalse(result.contains("PrivatePlayer"));assertFalse(result.contains("secret"));assertFalse(result.contains("private-user"));
 }
}
