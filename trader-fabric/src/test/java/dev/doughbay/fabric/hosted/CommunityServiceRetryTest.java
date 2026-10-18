package dev.doughbay.fabric.hosted;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class CommunityServiceRetryTest {
    @Test void registrationFailuresBackOffAndRespectServerDelay() {
        var retry=new CommunityServiceRetry();
        assertTrue(retry.ready(1000));
        assertEquals(11_000,retry.fail(1000,0));
        assertFalse(retry.ready(10_999));
        assertTrue(retry.ready(11_000));
        assertEquals(131_000,retry.fail(11_000,120_000));
        assertFalse(retry.ready(130_999));
        assertEquals(3_731_000,retry.fail(131_000,Long.MAX_VALUE));
    }
    @Test void errorMessagesNeverExposeRemoteBodiesOrLocalPaths() {
        assertEquals("connection failed",CommunityServiceRetry.reason(new java.io.IOException("private path / secret")));
        assertEquals("request timed out",CommunityServiceRetry.reason(new java.net.http.HttpTimeoutException("secret")));
        assertEquals("response or local storage unavailable",CommunityServiceRetry.reason(new IllegalArgumentException("raw response")));
    }
}
