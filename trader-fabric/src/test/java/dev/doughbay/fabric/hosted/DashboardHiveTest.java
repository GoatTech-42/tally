package dev.doughbay.fabric.hosted;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;
class DashboardHiveTest {
 @TempDir Path root;
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
