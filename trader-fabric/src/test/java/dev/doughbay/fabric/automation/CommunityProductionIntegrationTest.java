package dev.doughbay.fabric.automation;
import dev.doughbay.fabric.AutomatedExecutionDriver;
import dev.doughbay.fabric.OrderBook;
import java.lang.reflect.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class CommunityProductionIntegrationTest {
    void set(Object c,String name,Object v)throws Exception {Field f=c.getClass().getDeclaredField(name);f.setAccessible(true);f.set(c,v);}
    Object get(Object c,String name)throws Exception {Field f=c.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(c);}
    @SuppressWarnings("unchecked") Map<String,Long> bids(AutomationSessionController c,List<OrderBook.Order> rows,long now)throws Exception {
        Method m=c.getClass().getDeclaredMethod("tradingBestBids",List.class,long.class);m.setAccessible(true);return (Map<String,Long>)m.invoke(c,rows,now);
    }
    @Test void startupTelemetryDoesNotRequireAnInitializedTradingPolicy() {
        var c=new AutomationSessionController(new AutomatedExecutionDriver());
        assertDoesNotThrow(()->{for(int i=0;i<45;i++)c.tick(null,null);});
    }
    @Test void liveBidOverridesHistoricalBookOnlyInExplicitCommunityExperiment()throws Exception {
        var c=new AutomationSessionController(new AutomatedExecutionDriver());long now=100000;
        var rows=List.of(new OrderBook.Order(now-90000,"minecraft:stone","minecraft:stone","",List.of(),9000000,0,64,1));
        set(c,"orderQuotes",new HashMap<>(Map.of("minecraft:stone",new long[]{100,now-1000})));
        assertEquals(9000000L,bids(c,rows,now).get("minecraft:stone"));
        set(c,"experimentCommunityEnabled",true);
        assertEquals(100L,bids(c,rows,now).get("minecraft:stone"));
        assertFalse(bids(c,rows,now+31000).containsKey("minecraft:stone"),"expired quote cannot fall back to historical price");
    }
    @Test void requestingModeDoesNotSwitchDuringAnInFlightOperation()throws Exception {
        var c=new AutomationSessionController(new AutomatedExecutionDriver());set(c,"state",AutomationSessionController.State.BUYING);
        assertTrue(c.requestExperimentMode(true,System.currentTimeMillis()+3600000).startsWith("OK:"));
        assertEquals(false,get(c,"experimentCommunityEnabled"));assertEquals(true,get(c,"experimentRequestedMode"));
        assertTrue(c.requestExperimentPauseAtIdle().startsWith("OK:"));assertEquals(AutomationSessionController.State.BUYING,get(c,"state"));
        Method tick=c.getClass().getDeclaredMethod("tickExperimentBoundary",net.minecraft.client.Minecraft.class,long.class);tick.setAccessible(true);
        assertEquals(false,tick.invoke(c,null,System.currentTimeMillis()));
        assertEquals(AutomationSessionController.State.BUYING,get(c,"state"));assertEquals(false,get(c,"experimentCommunityEnabled"));
    }
    @Test void publicSwarmOptInUsesOnlyFreshNativeQuotesJustLikeThePrivateValidation()throws Exception {
        var c=new AutomationSessionController(new AutomatedExecutionDriver());long now=100000;
        var rows=List.of(new OrderBook.Order(now-90000,"minecraft:stone","minecraft:stone","",List.of(),9000000,0,64,1));
        set(c,"orderQuotes",new HashMap<>(Map.of("minecraft:stone",new long[]{100,now-1000})));
        set(c,"publicCommunityEnabled",true);
        assertEquals(100L,bids(c,rows,now).get("minecraft:stone"));
        assertFalse(bids(c,rows,now+31000).containsKey("minecraft:stone"));
        assertEquals(false,get(c,"experimentCommunityEnabled"),"public opt-in does not depend on private experiment controls");
    }
    @Test void expiredOrUnboundedExperimentCannotBeEnabled() {
        var c=new AutomationSessionController(new AutomatedExecutionDriver());
        assertTrue(c.requestExperimentMode(true,0).startsWith("Refused:"));
        assertTrue(c.requestExperimentMode(true,System.currentTimeMillis()+13*3600000L).startsWith("Refused:"));
    }
    @Test void anExplicitSourceReadMustNotBeReplacedByDiscoveryReuse()throws Exception {
        var c=new AutomationSessionController(new AutomatedExecutionDriver());set(c,"state",AutomationSessionController.State.SCANNING);
        set(c,"experimentCommunityEnabled",true);
        assertTrue(c.requestFullOrderSourceRead().startsWith("OK:"));
        assertEquals(true,get(c,"experimentForceSourceRead"));assertEquals(0L,get(c,"nextFullSweepAt"));
    }
}
