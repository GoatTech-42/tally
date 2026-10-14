package dev.doughbay.fabric;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RestartRouteTest {
    private static final long START = 1_000_000L;
    private static final String EAST = "east.donutsmp.net", WEST = "west.donutsmp.net";
    private static final Object LIMBO = new Object(), REGION = new Object();

    private RestartRoute route() {
        var r = new RestartRoute();
        r.start(List.of(EAST, WEST), LIMBO, START);
        return r;
    }
    private RestartRoute.World world(long at, boolean inWorld, String address, Object level,
                                     boolean balance, long limbo, boolean connecting, boolean safe) {
        return new RestartRoute.World(at, inWorld, inWorld || connecting, connecting,
                !inWorld && !connecting, address, level, balance, limbo, true, safe);
    }
    private RestartRoute.World parked(long at, boolean safe) {
        return world(at, true, "donutsmp.net", LIMBO, false, START - 2_000L, false, safe);
    }
    private RestartRoute.World offline(long at) { return world(at, false, "", null, false, 0, false, true); }
    private long connectEast(RestartRoute r) {
        assertEquals(RestartRoute.Action.DISCONNECT, r.tick(parked(START + 4_000L, true)).action());
        var join = r.tick(offline(START + 5_000L));
        assertEquals(RestartRoute.Action.CONNECT, join.action());
        assertEquals(EAST, join.address());
        return START + 5_000L;
    }

    @Test void disconnectsBeforeDirectRegionalJoinAndStaysThere() {
        var r = route();long joined = connectEast(r);
        assertEquals(RestartRoute.Action.NONE, r.tick(world(joined + 1_000L, true, EAST, REGION, true, START-2_000L, false, true)).action());
        var ready = r.tick(world(joined + 13_000L, true, EAST, REGION, true, START-2_000L, false, true));
        assertEquals(RestartRoute.Action.WORLD_READY, ready.action());
        assertEquals(EAST, ready.address());
        assertFalse(r.active());
        assertEquals(RestartRoute.Action.NONE, r.tick(world(joined+60_000L, true, EAST, REGION, true, 0, false, true)).action(),
                "must not bounce back to donutsmp.net after a working regional join");
    }

    @Test void waitsForDisconnectCompletionInsteadOfOverlappingConnections() {
        var r = route();r.tick(parked(START+4_000L,true));
        assertEquals(RestartRoute.Action.NONE, r.tick(parked(START+5_000L,true)).action());
        assertEquals(RestartRoute.Action.DISCONNECT, r.tick(parked(START+14_000L,true)).action());
        assertEquals(RestartRoute.Action.CONNECT, r.tick(offline(START+15_000L)).action());
    }

    @Test void noDisconnectWhileAnInventoryOrDriverOperationIsUnsafe() {
        var r = route();
        assertEquals(RestartRoute.Action.NONE, r.tick(parked(START+5_000L,false)).action());
        assertEquals(RestartRoute.Action.NONE, r.tick(parked(START+12_000L,false)).action());
        assertEquals(RestartRoute.Action.DISCONNECT, r.tick(parked(START+13_000L,true)).action());
    }

    @Test void naturallyReleasedWorldIsKeptBeforeAnyDisconnect() {
        var r = route();
        assertEquals(RestartRoute.Action.RELEASED, r.tick(world(START+4_000L,true,"donutsmp.net",REGION,true,0,false,true)).action());
        assertFalse(r.active());
    }

    @Test void timedOutEastAttemptSwitchesToWestAfterDelayAndKeepsRetrying() {
        var r = route();long joined = connectEast(r);
        var timeout = r.tick(world(joined+45_000L,false,"",null,false,0,true,true));
        assertEquals(RestartRoute.Action.DISCONNECT, timeout.action());
        assertEquals(WEST, timeout.address());
        assertEquals(RestartRoute.Action.NONE, r.tick(offline(joined+64_999L)).action());
        assertEquals(WEST, r.tick(offline(joined+65_000L)).address());
        var westFail=r.tick(offline(joined+67_000L));
        assertEquals(RestartRoute.Action.DISCONNECT, westFail.action());
        assertEquals(EAST, westFail.address());
        var retry=r.tick(offline(joined+87_000L));
        assertEquals(RestartRoute.Action.CONNECT, retry.action());
        assertEquals(EAST, retry.address());
    }

    @Test void freshLimboOnEastTriggersWestEvenWithABalanceShowing() {
        var r = route();long joined=connectEast(r);
        r.tick(world(joined+1_000L,true,EAST,REGION,true,joined+500L,false,true));
        var retry=r.tick(world(joined+2_000L,true,EAST,REGION,true,joined+500L,false,true));
        assertEquals(RestartRoute.Action.DISCONNECT, retry.action());
        assertEquals(WEST, retry.address());
    }

    @Test void successfulWestFallbackDoesNotReturnToMain() {
        var r=route();long joined=connectEast(r);
        r.tick(offline(joined+2_000L));
        assertEquals(WEST,r.tick(offline(joined+22_000L)).address());
        r.tick(world(joined+23_000L,true,WEST,REGION,true,0,false,true));
        var ready=r.tick(world(joined+35_000L,true,WEST,REGION,true,0,false,true));
        assertEquals(RestartRoute.Action.WORLD_READY,ready.action());
        assertEquals(WEST,ready.address());
        assertFalse(r.active());
    }

    @Test void previousConnectionsCachedBalanceCannotProveTheNewWorld() {
        var r = route();long joined=connectEast(r);
        r.tick(world(joined+1_000L,true,EAST,REGION,false,0,false,true));
        assertEquals(RestartRoute.Action.NONE,r.tick(world(joined+13_000L,true,EAST,REGION,false,0,false,true)).action());
        assertTrue(r.active());
        assertEquals(RestartRoute.Action.WORLD_READY,r.tick(world(joined+14_000L,true,EAST,REGION,true,0,false,true)).action());
    }

    @Test void anotherManualConnectionIsNotTornDown() {
        var r=route();long joined=connectEast(r);
        assertEquals(RestartRoute.Action.CANCEL,r.tick(world(joined+1_000L,true,"example.org",REGION,true,0,false,true)).action());
        assertFalse(r.active());
    }

    @Test void worldChangesResetTheStabilityWindow() {
        var r=route();long joined=connectEast(r);
        r.tick(world(joined+1_000L,true,EAST,REGION,true,0,false,true));
        Object destination=new Object();
        assertEquals(RestartRoute.Action.NONE,r.tick(world(joined+10_000L,true,EAST,destination,true,0,false,true)).action());
        assertEquals(RestartRoute.Action.NONE,r.tick(world(joined+13_000L,true,EAST,destination,true,0,false,true)).action());
        assertEquals(RestartRoute.Action.WORLD_READY,r.tick(world(joined+22_000L,true,EAST,destination,true,0,false,true)).action());
    }

    @Test void staffWatchCancellationDropsRestartConnectionOwnership() {
        var recovery=new RestartRecovery();
        var field=assertDoesNotThrow(()->RestartRecovery.class.getDeclaredField("route"));
        field.setAccessible(true);
        var r=assertDoesNotThrow(()->(RestartRoute)field.get(recovery));
        r.start(List.of(EAST,WEST),LIMBO,START);
        assertTrue(recovery.active());
        recovery.yieldToStaffWatch();
        assertFalse(recovery.active());
        assertEquals(RestartRoute.Action.NONE,r.tick(offline(START+10_000L)).action());
    }
}
