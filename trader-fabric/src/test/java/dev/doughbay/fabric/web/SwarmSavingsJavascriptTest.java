package dev.doughbay.fabric.web;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;
class SwarmSavingsJavascriptTest {
 @TempDir Path root;
 @Test void realPanelDistinguishesUnknownSlowAndCalibratedSavings()throws Exception{
  Path html=root.resolve("panel.html"),script=root.resolve("test.cjs");Files.writeString(html,Pages.APP);
  try(var in=getClass().getResourceAsStream("/swarm-savings.cjs")){assertNotNull(in);Files.copy(in,script);}
  var p=new ProcessBuilder(System.getenv().getOrDefault("NODE_BINARY","node"),script.toString(),html.toString()).redirectErrorStream(true).start();
  if(!p.waitFor(20,TimeUnit.SECONDS)){p.destroyForcibly();fail("Panel test timed out");}
  assertEquals(0,p.exitValue(),new String(p.getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));
 }
}
