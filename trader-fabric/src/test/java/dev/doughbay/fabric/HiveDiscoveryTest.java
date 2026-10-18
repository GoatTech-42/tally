package dev.doughbay.fabric;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class HiveDiscoveryTest {
    @TempDir Path root;
    private HiveDiscovery client(String instance,String account,String hive) throws Exception {
        return new HiveDiscovery(root.resolve("registry"),root.resolve(instance+"/preferences.json"),instance,account,hive);
    }
    @Test void differentRunningAccountOffersOnceAcrossRestartsWithoutExposingNamesOrPaths() throws Exception {
        long now=System.currentTimeMillis();
        HiveDiscovery a=client("first","Alice",""), b=client("second","Bob","");
        a.refresh(now);b.refresh(now);a.refresh(now);
        assertNotNull(a.take(now));assertNull(a.take(now));
        var restarted=client("first","Alice","");restarted.refresh(now);assertNull(restarted.take(now));
        try(var files=Files.list(root.resolve("registry"))) {
            for(var p:files.toList()) {
                var text=Files.readString(p);assertFalse(text.contains("Alice"));assertFalse(text.contains("Bob"));
                assertFalse(text.contains(root.toString()));
            }
        }
        assertFalse(Files.exists(root.resolve("gonuts-hive.json")),"Discovery has no linking authority");
    }
    @Test void sameAccountSameInstanceAndSameHiveNeverOffer() throws Exception {
        long now=System.currentTimeMillis();
        HiveDiscovery a=client("first","Alice","shared"),b=client("second","ALICE","other"),c=client("third","Bob","shared");
        a.refresh(now);b.refresh(now);c.refresh(now);a.refresh(now);assertNull(a.take(now));
    }
    @Test void staleFutureMalformedAndStoppedPeersDoNotCount() throws Exception {
        long now=System.currentTimeMillis();HiveDiscovery a=client("first","Alice",""),b=client("second","Bob","");
        b.refresh(now-36_000);a.refresh(now);assertNull(a.take(now));
        b.refresh(now+1);a.refresh(now);assertNull(a.take(now));
        b.refresh(now);a.refresh(now);assertNull(a.take(now+36_000),"Do not show a stale queued notice");
        try(var files=Files.list(root.resolve("registry"))) {
            for(var p:files.toList()) {
                String text=Files.readString(p);
                Files.writeString(p,text.replace("\"pid\":"+ProcessHandle.current().pid(),"\"pid\":-1"));
            }
        }
        a.refresh(now);assertNull(a.take(now));
        Files.writeString(root.resolve("registry/"+"f".repeat(64)+".json"),"not json");
        a.refresh(now);assertNull(a.take(now));
    }
    @Test void dontAskAgainPersistsAndNewAccountsOtherwiseRemainDiscoverable() throws Exception {
        long now=System.currentTimeMillis();HiveDiscovery a=client("first","Alice",""),b=client("second","Bob","");
        b.refresh(now);a.refresh(now);assertNotNull(a.take(now));
        var c=client("third","Charlie","");c.refresh(now);a.refresh(now);assertNotNull(a.take(now));
        a.disable();var d=client("fourth","Dana","");d.refresh(now);
        var restarted=client("first","Alice","");restarted.refresh(now);assertNull(restarted.take(now));
    }
    @Test void unreadablePreferencesArePreservedAndNeverSilentlyReset() throws Exception {
        Path file=root.resolve("first/preferences.json");Files.createDirectories(file.getParent());Files.writeString(file,"broken");
        assertThrows(Exception.class,()->client("first","Alice",""));assertEquals("broken",Files.readString(file));
    }
}
