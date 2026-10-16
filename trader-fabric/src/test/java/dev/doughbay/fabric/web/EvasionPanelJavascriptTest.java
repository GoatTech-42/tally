package dev.doughbay.fabric.web;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class EvasionPanelJavascriptTest {
    @TempDir Path root;
    @Test void eventHistoryRefreshPreservesSettingsAndTreatsEventTextAsText()throws Exception{
        String node=System.getenv().getOrDefault("NODE_BINARY","node");
        try{var check=new ProcessBuilder(node,"--version").start();assumeTrue(check.waitFor(5,TimeUnit.SECONDS)&&check.exitValue()==0,"Node required for JavaScript regression");}
        catch(java.io.IOException e){assumeTrue(false,"Node required for JavaScript regression");}
        Path html=root.resolve("panel.html"),script=root.resolve("evasion-panel.cjs");Files.writeString(html,Pages.APP);
        try(var input=getClass().getResourceAsStream("/evasion-panel.cjs")){assertNotNull(input);Files.copy(input,script);}
        var process=new ProcessBuilder(node,script.toString(),html.toString()).redirectErrorStream(true).start();
        if(!process.waitFor(15,TimeUnit.SECONDS)){process.destroyForcibly();fail("Evasion JavaScript test timed out");}
        assertEquals(0,process.exitValue(),new String(process.getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));
    }
}
