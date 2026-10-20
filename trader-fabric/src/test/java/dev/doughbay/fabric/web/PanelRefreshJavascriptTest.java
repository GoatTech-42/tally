package dev.doughbay.fabric.web;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;
class PanelRefreshJavascriptTest {
 @TempDir Path root;
 @Test void relayFailuresHaveSpecificRetryGuidanceAndSuccessfulReadsStillRefresh()throws Exception{
  Path html=root.resolve("panel.html"),script=root.resolve("panel-refresh.cjs");Files.writeString(html,Pages.APP);
  try(var input=getClass().getResourceAsStream("/panel-refresh.cjs")){assertNotNull(input);Files.copy(input,script);}
  var process=new ProcessBuilder(System.getenv().getOrDefault("NODE_BINARY","node"),script.toString(),html.toString()).redirectErrorStream(true).start();
  if(!process.waitFor(20,TimeUnit.SECONDS)){process.destroyForcibly();fail("Panel refresh JavaScript test timed out");}
  assertEquals(0,process.exitValue(),new String(process.getInputStream().readAllBytes()));
 }
}
