package dev.doughbay.fabric;

import dev.doughbay.fabric.hosted.DashboardProtocol;
import java.net.URI;
import java.net.http.*;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Explicit smoke check only: isolated random capability, no account, no report, always erased. */
@EnabledIfEnvironmentVariable(named="GONUTS_DIAGNOSTICS_LIVE_SMOKE",matches="1")
class MandatoryDiagnosticsLiveTest {
 @TempDir Path root;
 @Test void actualClientFramesSurviveRestartAndAreAcceptedByPublicReceiver()throws Exception{
  var http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).followRedirects(HttpClient.Redirect.NEVER).build();
  long now=System.currentTimeMillis();String key,oldSession;
  try(var original=new DiagnosticOutbox(root,"diagnostics-smoke",now)){
   key=original.credential();oldSession=original.session();
   original.append("LIFECYCLE",List.of("life event=START build=diagnostics-smoke reason=GAME_START"),SettingsCode.exportForReport(),now);
   original.append("DECISION",List.of("dec v=1 at="+now+" scope=DRIVER event=ABORTED code=LIST reason=CONFIRMATION_GUI_TIMEOUT market=NONE requested=-1 selected=64 price=1000 limit=2000"),"",now);
   String twoOrders=SettingsCode.encode("{\"v\":1,\"n\":{\"orders.max_per_market\":2},\"t\":{}}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
   original.append("DECISION",List.of("dec v=1 at="+now+" scope=CONTROLLER event=OBSERVATION code=SET_ORDERS_MAX_PER_MARKET reason=NONE market=NONE requested=0 selected=2000 price=1000 limit=-1"),twoOrders,now);
  }
  try{
   try(var restarted=new DiagnosticOutbox(root,"diagnostics-smoke",now+1)){
    var frame=restarted.batch(System.currentTimeMillis(),32);assertEquals(oldSession,frame.path("sessionId").asText());assertEquals(3,frame.path("events").size());restarted.sent(frame);
    var response=http.send(MandatoryDiagnostics.uploadRequest(frame,key),HttpResponse.BodyHandlers.ofString());assertEquals(200,response.statusCode());
    var ack=DashboardProtocol.JSON.readTree(response.body());assertEquals(3,ack.path("stored").asInt());assertEquals(0,ack.path("refused").asInt());assertTrue(restarted.acknowledge(ack));assertNull(restarted.batch(System.currentTimeMillis(),32));
    var replay=http.send(MandatoryDiagnostics.uploadRequest(frame,key),HttpResponse.BodyHandlers.ofString());assertEquals(3,DashboardProtocol.JSON.readTree(replay.body()).path("duplicate").asInt());
    restarted.append("LIFECYCLE",List.of("life event=START build=diagnostics-smoke reason=GAME_START"),SettingsCode.exportForReport(),System.currentTimeMillis());
    var fresh=restarted.batch(System.currentTimeMillis(),32);assertNotEquals(oldSession,fresh.path("sessionId").asText());restarted.sent(fresh);
    var result=http.send(MandatoryDiagnostics.uploadRequest(fresh,key),HttpResponse.BodyHandlers.ofString());assertEquals(200,result.statusCode());assertTrue(restarted.acknowledge(DashboardProtocol.JSON.readTree(result.body())));
    var status=http.send(HttpRequest.newBuilder(URI.create("https://gonuts.at/v1/bugs/diagnostics/status")).timeout(Duration.ofSeconds(15)).header(MandatoryDiagnostics.HEADER,key).GET().build(),HttpResponse.BodyHandlers.ofString());assertEquals(200,status.statusCode());assertEquals(2,DashboardProtocol.JSON.readTree(status.body()).path("sessions").size());
   }
  }finally{
   var erase=http.send(HttpRequest.newBuilder(URI.create("https://gonuts.at/v1/bugs/diagnostics")).timeout(Duration.ofSeconds(15)).header(MandatoryDiagnostics.HEADER,key).DELETE().build(),HttpResponse.BodyHandlers.discarding());assertEquals(204,erase.statusCode());
  }
 }
}
