package dev.doughbay.fabric;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.sql.DriverManager;
import static org.junit.jupiter.api.Assertions.*;
class PanelHiveJoinTest {
 @TempDir Path root;
 Path instance(String name,boolean history)throws Exception{
  Path game=Files.createDirectories(root.resolve(name)),data=Files.createDirectories(game.resolve("config/doughbay"));Files.writeString(data.resolve("config.json"),"{\"continuousMaxSessionSpend\":123,\"authorizedExecutionEnabled\":false}");
  try(var c=DriverManager.getConnection("jdbc:sqlite:"+data.resolve("doughbay.db"));var s=c.createStatement()){
   s.execute("CREATE TABLE positions(id INTEGER PRIMARY KEY,client TEXT)");if(history)s.execute("INSERT INTO positions VALUES(1,'Owner'),(2,'Peer')");
  }return game;
 }
 Path data(Path game){return game.resolve("config/doughbay");}
 static class Client implements PanelHiveJoin.Client {
  boolean safe,active=true,connected=true,cursor=true;int pauses,leaves;
  public boolean safe(){return safe;}public boolean active(){return active;}public boolean connected(){return connected;}public boolean cursorEmpty(){return cursor;}
  public void pause(){pauses++;active=false;}public void disconnect(){leaves++;}
 }
 @Test void headlessJoinRegistersExistingHiveWithoutDatabaseChangesAndLeavesOnlyFreshWorld()throws Exception{
  Path source=instance("source",true),fresh=instance("third",false);byte[] original=Files.readAllBytes(data(source).resolve("doughbay.db"));
  Files.writeString(data(source).resolve("orders-bids.txt"),"source-only-orders");
  var target=HiveSetup.create(source,data(source),"Owner");var joining=new PanelHiveJoin(fresh,data(fresh),"Third");var client=new Client();
  assertFalse(joining.step(target,client));assertEquals(0,client.pauses);assertEquals(0,client.leaves);
  client.safe=true;assertFalse(joining.step(target,client));assertEquals(1,client.pauses);assertEquals(0,client.leaves);
  assertFalse(joining.step(target,client));assertFalse(joining.step(target,client));assertEquals(1,client.leaves,"Disconnect must be requested once");
  client.connected=false;assertTrue(joining.step(target,client));assertEquals(target,HiveSetup.configured(fresh));
  assertArrayEquals(original,Files.readAllBytes(data(source).resolve("doughbay.db")));assertEquals("source-only-orders",Files.readString(data(source).resolve("orders-bids.txt")));
  Path own=HiveCombine.configuration(target,"Third");assertFalse(Files.exists(own.resolve("orders-bids.txt")));assertTrue(Files.readString(own.resolve("config.json")).contains("123"));
  assertTrue(joining.status().contains("Restart ONLY this new"));
 }
 @Test void aBotWithHistoryOrAnExistingLinkIsNeverPausedDisconnectedOrRelinked()throws Exception{
  Path source=instance("source",true),used=instance("used",true);var target=HiveSetup.create(source,data(source),"Owner");var client=new Client();client.safe=true;
  assertThrows(java.io.IOException.class,()->new PanelHiveJoin(used,data(used),"Third").step(target,client));assertEquals(0,client.pauses);assertEquals(0,client.leaves);assertNull(HiveSetup.configured(used));
  Path linked=instance("already",false);var before=HiveSetup.create(linked,data(linked),"Third");
  assertThrows(java.io.IOException.class,()->new PanelHiveJoin(linked,data(linked),"Third").step(target,client));assertEquals(before,HiveSetup.configured(linked));assertEquals(0,client.leaves);
 }
 @Test void historyAppearingWhileSetupWaitsIsRecheckedBeforeLeaving()throws Exception{
  Path source=instance("source",true),fresh=instance("fresh",false);var target=HiveSetup.create(source,data(source),"Owner");var joining=new PanelHiveJoin(fresh,data(fresh),"Third");var client=new Client();client.safe=true;
  assertFalse(joining.step(target,client));
  try(var c=DriverManager.getConnection("jdbc:sqlite:"+data(fresh).resolve("doughbay.db"));var s=c.createStatement()){s.execute("INSERT INTO positions VALUES(1,'Third')");}
  assertThrows(java.io.IOException.class,()->joining.step(target,client));assertEquals(0,client.leaves);assertNull(HiveSetup.configured(fresh));
 }
}
