package dev.doughbay.fabric.hosted;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.nio.file.attribute.*;
import static org.junit.jupiter.api.Assertions.*;
class CommunityStoreTest {
 @TempDir Path root;
 @Test void credentialsRemainPerInstallationWithRestrictedAccess()throws Exception{
  var a=new CommunityStore(root.resolve("main"));var b=new CommunityStore(root.resolve("alt"));String token="gnc1_"+"A".repeat(43);
  a.write("settings.json",DashboardProtocol.JSON.createObjectNode().put("credential",token).put("enabled",true));assertEquals(token,a.read("settings.json").path("credential").asText());assertNull(b.read("settings.json"));
  var path=root.resolve("main/gonuts-community/settings.json");var acl=Files.getFileAttributeView(path,AclFileAttributeView.class);if(acl!=null)assertEquals(1,acl.getAcl().size());else assertEquals("rw-------",PosixFilePermissions.toString(Files.getPosixFilePermissions(path)));
 }
 @Test void durableBatchKeepsKeyAndRecordIdentityAcrossRestart()throws Exception{
  var a=new CommunityStore(root);var batch=DashboardProtocol.JSON.createObjectNode().put("key","stable_batch_key");var body=batch.putObject("body").put("batchCreatedAt",123).put("sentAt",123);body.putArray("records").add(CommunityWire.payment("Payer","Recipient",50000,100));
  a.write("pending.json",batch);var restored=new CommunityStore(root).read("pending.json");restored.withObject("body").put("sentAt",999);
  assertEquals(batch.path("key"),restored.path("key"));assertEquals(batch.path("body").path("records").toString(),restored.path("body").path("records").toString());assertEquals(123,restored.path("body").path("batchCreatedAt").asLong());a.remove("pending.json");assertNull(a.read("pending.json"));
 }
 @Test void filesAreNotAnArbitraryPathInterface(){var s=new CommunityStore(root);assertThrows(IllegalArgumentException.class,()->s.read("../installation.json"));assertThrows(IllegalArgumentException.class,()->s.remove("../token"));}
}
