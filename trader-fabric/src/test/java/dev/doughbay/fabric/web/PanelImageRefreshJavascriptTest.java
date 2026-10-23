package dev.doughbay.fabric.web;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;
class PanelImageRefreshJavascriptTest {
 @TempDir Path root;
 @Test void failedRefreshKeepsPriorImageAndLateOldAccountFramesAreDiscarded()throws Exception{
  Path html=root.resolve("panel.html"),script=root.resolve("test.cjs");Files.writeString(html,Pages.APP);
  try(var in=getClass().getResourceAsStream("/panel-image-refresh.cjs")){assertNotNull(in);Files.copy(in,script);}
  var process=new ProcessBuilder(System.getenv().getOrDefault("NODE_BINARY","node"),script.toString(),html.toString()).redirectErrorStream(true).start();
  if(!process.waitFor(20,TimeUnit.SECONDS)){process.destroyForcibly();fail("Image refresh test timed out");}
  assertEquals(0,process.exitValue(),new String(process.getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));
 }
}
