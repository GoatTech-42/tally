package dev.doughbay.api;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class RetiredRelayTest {
    @Test void retiredRelayCannotConstructAKeyBearingClient() {
        for(String url:new String[]{"https://gonuts.at","https://GONUTS.AT/v1","https://api.gonuts.at","http://147.189.172.152:8790"}) {
            assertTrue(DonutApiConfig.retiredService(url));
            var error=assertThrows(IllegalArgumentException.class,()->DonutApiConfig.withDefaults(url,"private-canary-key"));
            assertTrue(error.getMessage().contains("retired"));assertFalse(error.getMessage().contains("canary"));
        }
    }
    @Test void spacePaddedRetiredRelaysAreRecognizedImmediately() {
        for(String url:new String[]{" https://gonuts.at ","\thttps://api.gonuts.at\n"," http://147.189.172.152:8790 ","  https://GONUTS.AT/v1  "}) {
            assertTrue(DonutApiConfig.retiredService(url),url);
            assertThrows(IllegalArgumentException.class,()->DonutApiConfig.withDefaults(url,"private-canary-key"));
        }
    }
    @Test void spacePaddedProxiesAndLookalikesRemainAvailable() {
        for(String url:new String[]{" http://localhost:8123 "," https://gonuts.at.example.invalid "," https://hive.example.test "}) {
            assertFalse(DonutApiConfig.retiredService(url),url);assertDoesNotThrow(()->DonutApiConfig.withDefaults(url,"example"));
        }
    }
    @Test void personalApiAndIndependentLocalProxiesRemainAvailable() {
        for(String url:new String[]{DonutApiConfig.DEFAULT_BASE_URL,"http://localhost:8123","https://gonuts.at.example.invalid"}) {
            assertFalse(DonutApiConfig.retiredService(url));assertDoesNotThrow(()->DonutApiConfig.withDefaults(url,"example"));
        }
        assertFalse(DonutApiConfig.retiredService(null));assertFalse(DonutApiConfig.retiredService("invalid"));
    }
}
