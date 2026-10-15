package dev.doughbay.fabric.proxy;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ProxySettingsTest {
    @TempDir Path root;
    private static final ObjectMapper JSON=new ObjectMapper();
    static Map<String,String> profile(){return Map.of("action","save","name","Test proxy","host","127.0.0.1","port","1080","type","SOCKS5","username","private-user","password","private-secret");}
    @Test void settingsAreLocalPersistedAndNeverReturnCredentials()throws Exception{
        var settings=new ProxySettings(root);assertFalse(settings.state().enabled());assertFalse(settings.blocked());
        assertTrue(JSON.readTree(settings.change(profile())).path("ok").asBoolean());
        String id=settings.state().profiles().getFirst().id();settings.change(Map.of("action","enable","id",id));
        var loaded=new ProxySettings(root);assertTrue(loaded.state().enabled());assertEquals("private-secret",loaded.selected().password());
        assertFalse(loaded.publicJson().contains("private-user"));assertFalse(loaded.publicJson().contains("private-secret"));
        assertFalse(loaded.selected().toString().contains("private-secret"));
        assertFalse(new ProxySettings(root.resolve("other-client")).state().enabled());
        var edit=new HashMap<>(profile());edit.put("id",id);edit.put("username","");edit.put("password","");loaded.change(edit);
        assertEquals("private-secret",loaded.selected().password());
        edit.put("clearCredentials","true");loaded.change(edit);assertEquals("",loaded.selected().password());
    }
    @Test void invalidSettingsCannotSilentlyEnableDirectRouting()throws Exception{
        Files.writeString(root.resolve("gonuts-proxies.json"),"broken");var settings=new ProxySettings(root);
        assertTrue(settings.blocked());assertTrue(settings.state().enabled());
        assertTrue(JSON.readTree(settings.change(Map.of("action","disable"))).path("ok").asBoolean());
        assertFalse(new ProxySettings(root).blocked());
    }
    @Test void deletingAnActiveProfileOrSavingInvalidInputLeavesRoutingIntact()throws Exception{
        var settings=new ProxySettings(root);settings.change(profile());String id=settings.state().profiles().getFirst().id();settings.change(Map.of("action","enable","id",id));
        assertFalse(JSON.readTree(settings.change(Map.of("action","delete","id",id))).path("ok").asBoolean());
        var invalid=new HashMap<>(profile());invalid.put("id",id);invalid.put("port","password-that-must-not-escape");String response=settings.change(invalid);
        assertFalse(JSON.readTree(response).path("ok").asBoolean());assertFalse(response.contains("password-that-must-not-escape"));assertTrue(settings.state().enabled());
        invalid.put("port","1080");invalid.put("type","SOCKS4");assertFalse(JSON.readTree(settings.change(invalid)).path("ok").asBoolean());
    }
}
