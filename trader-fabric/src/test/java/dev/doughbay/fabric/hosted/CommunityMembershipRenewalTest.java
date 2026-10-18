package dev.doughbay.fabric.hosted;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;
class CommunityMembershipRenewalTest {
    @TempDir Path root;
    private CommunityClient client()throws Exception{
        var ctor=CommunityClient.class.getDeclaredConstructor();ctor.setAccessible(true);var c=ctor.newInstance();
        set(c,"store",new CommunityStore(root));set(c,"credential","old-test-credential");
        set(c,"contributor","old-test-member");set(c,"registeredAccount","OldAccount");
        set(c,"enabled",true);set(c,"eligible",true);set(c,"enrolled",true);return c;
    }
    @Test void refusedCredentialStopsSharingAndRetryUsesFreshAuthenticatedEnrollment()throws Exception{
        var c=client();var pending=DashboardProtocol.JSON.createObjectNode().put("key","retained-test-batch");set(c,"pending",pending);
        new CommunityStore(root).write("pending.json",pending);
        c.refuseCredential(403);
        assertFalse(c.view().path("enabled").asBoolean());assertFalse(c.view().path("eligible").asBoolean());
        assertTrue(new CommunityStore(root).read("settings.json").path("credentialRejected").asBoolean());
        assertSame(pending,get(c,"pending"),"auth refusal must not discard the batch");
        assertTrue(c.renewRejectedCredential());
        assertEquals("",get(c,"credential"));assertEquals("",get(c,"registeredAccount"));
        assertFalse((boolean)get(c,"enrolled"));assertSame(pending,get(c,"pending"));
        assertFalse(c.view().path("eligible").asBoolean(),"reset is not enrollment or admission");
        assertEquals("retained-test-batch",new CommunityStore(root).read("pending.json").path("key").asText());
    }
    @Test void disabledLegacyCredentialAlsoRenewsOnTheFirstRetry()throws Exception{
        var c=client();set(c,"enabled",false);
        assertTrue(c.renewRejectedCredential());assertEquals("",get(c,"credential"));
    }
    @Test void healthyMembershipIsNotResetAndOtherHttpErrorsAreNotAuthRefusals()throws Exception{
        var c=client();assertFalse(c.renewRejectedCredential());assertEquals("old-test-credential",get(c,"credential"));
        assertThrows(IllegalArgumentException.class,()->c.refuseCredential(429));
        assertTrue(c.view().path("enabled").asBoolean());
    }
    private static Object get(Object c,String n)throws Exception{var f=c.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(c);}
    private static void set(Object c,String n,Object v)throws Exception{var f=c.getClass().getDeclaredField(n);f.setAccessible(true);f.set(c,v);}
}
