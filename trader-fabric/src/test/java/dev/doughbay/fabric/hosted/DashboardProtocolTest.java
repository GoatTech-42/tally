package dev.doughbay.fabric.hosted;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class DashboardProtocolTest {
 @Test void nativeReadOperationsAreNotReportedAsIdle(){
  var reading=DashboardProtocol.telemetry(1000,"READING_ORDERS","Account",1,20,100,1,"build",false,900);
  assertEquals("SCANNING",reading.path("state").asText());assertEquals("Reading orders",reading.path("detail").asText());assertEquals("Reading orders",reading.path("lastAction").asText());assertTrue(reading.path("controls").path("pause").asBoolean());
  var audit=DashboardProtocol.telemetry(1000,"AUDITING_SLOTS","Account",1,20,100,1,"build",false,900);
  assertEquals("MONITORING",audit.path("state").asText());assertEquals("Checking own listings",audit.path("detail").asText());
 }
 @Test void contractCodeVectorsAndInvalidInput(){assertEquals("K7PQ-M3ZD",DashboardProtocol.code("k7pq m3zd"));assertEquals("LOLO-LOLO",DashboardProtocol.code("1010-1010"));assertThrows(IllegalArgumentException.class,()->DashboardProtocol.code("ABC"));assertThrows(IllegalArgumentException.class,()->DashboardProtocol.code("ABCD/EFG"));}
 @Test void credentialsNeverBecomeNamesOrEndpoints(){assertEquals("GoNuts client",DashboardProtocol.label("gni1_"+"A".repeat(43)));assertEquals("GoNuts client",DashboardProtocol.label("C:/Users/private"));assertEquals("GoNuts client",DashboardProtocol.label("/home/private"));assertEquals("Laptop (main)",DashboardProtocol.label("Laptop (main)"));assertEquals("gonuts.at",DashboardProtocol.SOCKET.getHost());assertEquals("wss",DashboardProtocol.SOCKET.getScheme());}
 @Test void projectionIsBoundedAndDoesNotForwardRawControllerDetail(){var j=DashboardProtocol.telemetry(1000,"DRUNK","Account",-5,Long.MAX_VALUE,Long.MAX_VALUE,-1,"build",false,900);assertEquals("UNKNOWN",j.path("state").asText());assertEquals(0,j.path("listed").asInt());assertEquals(1_000_000,j.path("slots").asInt());assertEquals(1_000_000_000_000L,j.path("bank").asLong());assertFalse(j.has("log"));assertFalse(j.has("credential"));assertEquals("Idle",j.path("detail").asText());assertFalse(j.path("controls").path("send_crash_report").asBoolean());}
 @Test void helloAdvertisesOnlyImplementedCapabilities(){var h=DashboardProtocol.hello("0.1.1","Laptop","hive_123");assertEquals(1,h.path("protocols").get(0).asInt());assertEquals(12,h.path("capabilities").size());assertTrue(h.path("capabilities").toString().contains("diagnostic_history"));assertTrue(h.path("capabilities").toString().contains("settings_analysis"));assertTrue(h.path("capabilities").toString().contains("read_savings"));assertTrue(h.path("capabilities").toString().contains("swarm_staff"));assertTrue(h.path("capabilities").toString().contains("swarm_rank"));assertTrue(h.path("capabilities").toString().contains("swarm_enable"));assertTrue(h.path("capabilities").toString().contains("swarm_disable"));assertFalse(h.toString().contains("send_crash_report"));assertFalse(DashboardProtocol.pairing("K7PQ-M3ZD","Laptop","build","hive_123").has("type"));}
 @Test void malformedAndOversizeFramesAreRejected(){assertThrows(Exception.class,()->DashboardProtocol.parse("[]"));assertThrows(Exception.class,()->DashboardProtocol.parse("x".repeat(32769)));}
 @Test void onlyPanelCanUseExpandedFrameBudget() throws Exception {
  var panel=DashboardProtocol.JSON.createObjectNode().put("type","panel").put("padding","x".repeat(40_000));assertEquals("panel",DashboardProtocol.parse(panel.toString()).path("type").asText());
  panel.put("type","command");assertThrows(Exception.class,()->DashboardProtocol.parse(panel.toString()));
  panel.put("type","panel").put("padding","x".repeat(DashboardProtocol.MAX_PANEL_FRAME_BYTES));assertThrows(Exception.class,()->DashboardProtocol.parse(panel.toString()));
 }
 @Test void reconnectBackoffAndJitterMatchContract(){assertEquals(700,DashboardProtocol.backoff(0,.7));assertEquals(2600,DashboardProtocol.backoff(1,1.3));assertEquals(60_000,DashboardProtocol.backoff(50,1));assertEquals(78_000,DashboardProtocol.backoff(50,1.3));}
}
