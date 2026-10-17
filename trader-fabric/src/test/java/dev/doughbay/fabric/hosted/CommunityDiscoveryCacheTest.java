package dev.doughbay.fabric.hosted;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class CommunityDiscoveryCacheTest {
    @SuppressWarnings("unchecked")
    @Test void plannerReadsOnlyFreshScopedCacheAndFallsBackWithoutWaitingForHttp()throws Exception {
        var constructor=CommunityClient.class.getDeclaredConstructor();constructor.setAccessible(true);var client=constructor.newInstance();
        for(String name:List.of("enabled","connected","eligible","networkSupported")) {
            var field=CommunityClient.class.getDeclaredField(name);field.setAccessible(true);field.set(client,true);
        }
        var field=CommunityClient.class.getDeclaredField("cache");field.setAccessible(true);var cache=(ConcurrentHashMap<String,Object>)field.get(client);
        var cached=Arrays.stream(CommunityClient.class.getDeclaredClasses()).filter(c->c.getSimpleName().equals("Cached")).findFirst().orElseThrow();
        var build=cached.getDeclaredConstructors()[0];build.setAccessible(true);
        long now=2_000_000;String key="minecraft:diamond|64";
        var response=DashboardProtocol.JSON.createObjectNode().put("itemKey",key).put("regionScope","net-donut");
        response.putObject("orderLevels").put("usability","history").putArray("rows").addObject().put("unitPrice",1000).put("remaining",64).put("lastSeenAt",now-1000);
        assertTrue(client.discoveryHints(Set.of(key),now).isEmpty(),"cache miss returns immediately, without starting HTTP");
        cache.put(key,build.newInstance(response,now-1000,now+1000,"net-donut"));
        assertNotNull(client.discoveryHints(Set.of(key),now).get(key));
        assertTrue(client.discoveryHints(Set.of(key),now+1001).isEmpty(),"expired response cannot keep influencing plans");
        cache.put(key,build.newInstance(response,now+1,now+1000,"net-donut"));assertTrue(client.discoveryHints(Set.of(key),now).isEmpty());
        cache.put(key,build.newInstance(response,now-1000,now+1000,"mixed"));assertTrue(client.discoveryHints(Set.of(key),now).isEmpty());
    }
}
