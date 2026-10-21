package dev.doughbay.fabric.hosted;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;
class DashboardHiveTest {
 @TempDir Path root;
 @Test void largeLegacyStatusStillAllowsSelectedSwarmRetryWhileRequestsStaySmall() throws Exception {
  var target=new DashboardHive(root);var host=new DashboardHive(root);long now=System.currentTimeMillis();
  target.publish("fixture",new HostedDashboardClient.View(true,true,false,"Fixture","Online",0),now);
  Path status=root.resolve("dashboard-local-ipc").resolve(DashboardHive.id("fixture")+".status");
  var value=(com.fasterxml.jackson.databind.node.ObjectNode)DashboardProtocol.JSON.readTree(Files.readAllBytes(status));
  var community=(com.fasterxml.jackson.databind.node.ObjectNode)value.get("community");
  community.put("enabled",false).put("needsRenewal",true).put("status","Membership needs renewal");
  community.putObject("orderShadow").put("diagnostic","x".repeat(6000));
  Files.write(status,DashboardProtocol.JSON.writeValueAsBytes(value));assertTrue(Files.size(status)>4096);
  assertNotNull(host.status("fixture",now));
  assertTrue(host.status("fixture",now).path("community").path("needsRenewal").asBoolean());
  assertFalse(host.status("fixture",now).path("community").has("orderShadow"));
  assertTrue(host.post("fixture","community_enable","","",now));
  assertEquals("community_enable",target.take("fixture",now).path("action").asText());
  assertNull(host.status("fixture",now+DashboardHive.FRESH_MS+1));
  assertTrue(host.post("fixture","community_enable","","",now));
  Path request=root.resolve("dashboard-local-ipc").resolve(DashboardHive.id("fixture")+".request");
  var body=(com.fasterxml.jackson.databind.node.ObjectNode)DashboardProtocol.JSON.readTree(Files.readAllBytes(request));
  body.put("padding","x".repeat(5000));Files.write(request,DashboardProtocol.JSON.writeValueAsBytes(body));
  assertNull(target.take("fixture",now));
  community.put("padding","x".repeat(DashboardProtocol.MAX_BYTES));
  Files.write(status,DashboardProtocol.JSON.writeValueAsBytes(value));assertNull(host.status("fixture",now));
 }
 @Test void compactCommunityStatusExcludesDiagnosticsAndBoundsText() {
  var source=DashboardProtocol.JSON.createObjectNode().put("enabled",true).put("needsRenewal",true)
      .put("accepted",42).put("status","s".repeat(1000)).put("credential","never copy");
  source.putObject("orderCoverage").put("rows",5000);
  source.putObject("readSavings").put("actualSkippedReads",12).put("estimatedNetSavedMs",-1000)
      .put("credential","never copy").put("scope","private text");
  var summary=DashboardHive.communitySummary(source);
  assertEquals(42,summary.path("accepted").asInt());assertTrue(summary.path("needsRenewal").asBoolean());
  assertEquals(240,summary.path("status").asText().length());assertFalse(summary.has("credential"));
  assertFalse(summary.has("orderCoverage"));
  assertEquals(12,summary.path("readSavings").path("actualSkippedReads").asInt());
  assertEquals(-1000,summary.path("readSavings").path("estimatedNetSavedMs").asLong());
  assertFalse(summary.path("readSavings").has("credential"));
  assertFalse(summary.path("readSavings").has("scope"));
 }
 private static HostedDashboardClient.View unlinked(){return new HostedDashboardClient.View(false,false,false,"","Not linked",0);}
 @Test void routesOnlyToSelectedClientConsumesOnceAndNeverSharesCredential() throws Exception {
  var host=new DashboardHive(root);var a=new DashboardHive(root);var b=new DashboardHive(root);long now=System.currentTimeMillis();
  a.publish("cody_raves",unlinked(),now);b.publish("hangyDangler",unlinked(),now);
  assertTrue(host.post("hangyDangler","link","K7PQ-M3ZD","Alt",now));assertNull(a.take("cody_raves",now));
  var action=b.take("hangyDangler",now);assertEquals(DashboardProtocol.code("K7PQ-M3ZD"),action.path("code").asText());assertEquals("Alt",action.path("name").asText());assertNull(b.take("hangyDangler",now));
  try(var paths=Files.list(root.resolve("dashboard-local-ipc"))){for(Path path:paths.toList())assertFalse(Files.readString(path).contains("gni1_"));}
 }
 @Test void stoppedOrOldBuildClientCannotReceiveCode() throws Exception {
  var host=new DashboardHive(root);var target=new DashboardHive(root);long now=System.currentTimeMillis();
  assertFalse(host.post("Alt","link","K7PQM3ZD","Alt",now));target.publish("Alt",unlinked(),now);
  assertFalse(host.post("Alt","link","K7PQM3ZD","Alt",now+DashboardHive.FRESH_MS+1));
 }
 @Test void queuedRequestsExpireAndCannotReplayAfterTargetRestart() throws Exception {
  var host=new DashboardHive(root);var target=new DashboardHive(root);long now=System.currentTimeMillis();target.publish("Alt",unlinked(),now);
  assertTrue(host.post("Alt","link","K7PQM3ZD","Alt",now));assertNull(new DashboardHive(root).take("Alt",now+1));
  assertTrue(host.post("Alt","link","K7PQM3ZD","Alt",now));assertNull(target.take("Alt",now+DashboardHive.REQUEST_MS+1));
 }
 @Test void pendingCodeIsNotOverwrittenAndExpiredCodesAreRemoved() throws Exception {
  var host=new DashboardHive(root);var target=new DashboardHive(root);long now=System.currentTimeMillis();target.publish("Alt",unlinked(),now);
  assertTrue(host.post("Alt","link","K7PQM3ZD","First",now));assertFalse(host.post("Alt","link","K7PQM3ZD","Second",now));
  host.sweep(now+DashboardHive.REQUEST_MS+1);assertNull(target.take("Alt",now+DashboardHive.REQUEST_MS+1));
  assertFalse(Files.exists(root.resolve("dashboard-local-ipc").resolve(DashboardHive.id("Alt")+".request")));
 }
 @Test void unlinkAndReconnectAreSeparateAccountActions() throws Exception {
  var host=new DashboardHive(root);var target=new DashboardHive(root);long now=System.currentTimeMillis();
  target.publish("Alt",new HostedDashboardClient.View(true,true,false,"Alt","Connected",0),now);
  assertFalse(host.post("Alt","link","K7PQM3ZD","Alt",now));assertTrue(host.post("Alt","unlink","","",now));
  var action=target.take("Alt",now);assertEquals("unlink",action.path("action").asText());assertFalse(action.has("code"));
  assertTrue(host.post("Alt","reconnect","","",now));assertEquals("reconnect",target.take("Alt",now).path("action").asText());
 }
 @Test void accountIdsIgnoreCaseAndNeverContainAccountNames() {assertEquals(DashboardHive.id("Alt"),DashboardHive.id("alt"));assertNotEquals(DashboardHive.id("Alt"),DashboardHive.id("Main"));assertTrue(DashboardHive.id("Alt").matches("[a-f0-9]{64}"));}
}
