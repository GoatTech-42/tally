package dev.doughbay.fabric.discord;
import dev.doughbay.fabric.AutomatedExecutionDriver;
import dev.doughbay.fabric.automation.AutomationSessionController;
import dev.doughbay.storage.Database;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import java.nio.file.Path;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
class PanelCacheIntegrationTest {
 @TempDir Path root;
 @Test void backgroundPreviewLeavesSelectionUntouchedAndRejectsAnotherScope() throws Exception {
  var ledger=root.resolve("ledger.db");try(var ignored=new Database(ledger)){}
  var panel=DiscordBridge.panelOnly(LoggerFactory.getLogger("test"),root,ledger,new AutomationSessionController(new AutomatedExecutionDriver()));
  var mapper=new com.fasterxml.jackson.databind.ObjectMapper();
  var state=mapper.readTree(panel.panelState());String scope=state.path("cacheScope").asText();assertFalse(scope.isEmpty());
  assertTrue(mapper.readTree(panel.panelPreview(Map.of("scope","another account","page","ORDERS"))).path("scopeChanged").asBoolean());
  assertTrue(mapper.readTree(panel.panelPreview(Map.of("scope",scope,"page","TUNE"))).path("unsupported").asBoolean());
  panel.panelPreview(Map.of("scope",scope,"page","ORDERS"));
  assertEquals("MONEY",mapper.readTree(panel.panelState()).path("pageId").asText(),"Background rendering must never select another tab");
  long deadline=System.currentTimeMillis()+10000;com.fasterxml.jackson.databind.JsonNode frame;
  do{frame=mapper.readTree(panel.panelPreview(Map.of("scope",scope,"page","ORDERS")));if(frame.path("pending").asBoolean())Thread.sleep(25);else break;}while(System.currentTimeMillis()<deadline);
  assertFalse(frame.path("pending").asBoolean(),"The queued report should finish without a foreground page action");
  String encoded=frame.path("image").asText().replace("data:image/png;base64,","");
  if(frame.has("chunks")){
   var parts=new StringBuilder();for(int n=0;n<frame.path("chunks").asInt();n++){
    String response=panel.panelPreview(Map.of("scope",scope,"page","ORDERS","frame",frame.path("frame").asText(),"chunk",Integer.toString(n)));
    assertTrue(response.length()<256*1024);var part=mapper.readTree(response);assertEquals(n,part.path("chunk").asInt());parts.append(part.path("data").asText());
   }encoded=parts.toString();assertEquals(frame.path("imageChars").asInt(),encoded.length());
  }
  var original=java.util.Base64.getDecoder().decode(encoded);assertEquals(1920,javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(original)).getWidth());
  assertEquals(scope,frame.path("scope").asText());
  assertEquals("MONEY",mapper.readTree(panel.panelState()).path("pageId").asText());assertTrue(frame.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length<256*1024);
 }
}
