package dev.doughbay.fabric.hosted;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;
class CommunityConsentTest {
    @TempDir Path game;
    void set(CommunityClient c,String name,Object value)throws Exception {
        var f=CommunityClient.class.getDeclaredField(name);f.setAccessible(true);f.set(c,value);
    }
    CommunityClient client()throws Exception {
        var ctor=CommunityClient.class.getDeclaredConstructor();ctor.setAccessible(true);return ctor.newInstance();
    }
    @Test void legacySharingAloneNeverEnablesGuidanceAndDashboardLinkRemainsRequired()throws Exception {
        var c=client();
        for(String key:new String[]{"started","enabled","eligible"})set(c,key,true);
        assertFalse(c.guidanceEnabled(true),"legacy enabled=true is not new guidance consent");
        set(c,"guidanceConsent",true);assertTrue(c.guidanceEnabled(true));
        assertFalse(c.guidanceEnabled(false),"only linked dashboard users participate");
        set(c,"eligible",false);assertFalse(c.guidanceEnabled(true));
        set(c,"eligible",true);set(c,"enabled",false);assertFalse(c.guidanceEnabled(true));
    }
    @Test void guidanceConsentPersistsSeparatelyFromContribution()throws Exception {
        var c=client();var store=new CommunityStore(game);set(c,"store",store);set(c,"enabled",true);
        var save=CommunityClient.class.getDeclaredMethod("save");save.setAccessible(true);save.invoke(c);
        assertFalse(store.read("settings.json").path("guidanceEnabled").asBoolean());
        set(c,"guidanceConsent",true);save.invoke(c);
        assertTrue(store.read("settings.json").path("guidanceEnabled").asBoolean());
    }
}
