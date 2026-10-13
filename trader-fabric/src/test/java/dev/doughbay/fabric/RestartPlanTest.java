package dev.doughbay.fabric;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RestartPlanTest {
    @Test
    void theSideDoorsAreEastAndWestOfTheHomeAddressUnlessListed() {
        assertEquals(List.of("east.donutsmp.net", "west.donutsmp.net"), RestartPlan.hops("", "donutsmp.net"));
        assertEquals(List.of("east.donutsmp.net", "west.donutsmp.net"), RestartPlan.hops(null, "DonutSMP.net:25565"));
        // Joined through a name of its own: the doors are still the network's, not that name's.
        assertEquals(List.of("east.donutsmp.net", "west.donutsmp.net"), RestartPlan.hops("  ", "play.donutsmp.net"));
        // The player's own list, in the player's order.
        assertEquals(List.of("west.donutsmp.net", "east.donutsmp.net"),
                RestartPlan.hops("West.donutsmp.net; east.donutsmp.net, west.donutsmp.net", "donutsmp.net"));
    }

    @Test
    void anAccountIsNeverSentToSomeOtherServer() {
        // A typing mistake, another network, the home address itself: none of them is a door.
        assertEquals(List.of("east.donutsmp.net"),
                RestartPlan.hops("east.donutsmp.net, donutsmp.net, mc.example.org, donutsmp.net.example.org", "donutsmp.net"));
        assertEquals(List.of(), RestartPlan.hops("mc.example.org", "donutsmp.net"));
        // Nothing to go east of: a bare address, a local name, nothing at all.
        assertEquals(List.of(), RestartPlan.hops("", "192.168.1.20:25565"));
        assertEquals(List.of(), RestartPlan.hops("", "localhost"));
        assertEquals(List.of(), RestartPlan.hops("", ""));
        assertTrue(RestartPlan.sameSite("east.donutsmp.net:25565", "DonutSMP.net"));
        assertFalse(RestartPlan.sameSite("donutsmp.net.example.org", "donutsmp.net"));
        assertFalse(RestartPlan.sameSite("10.0.0.1", "10.0.0.1"));
    }

    @Test
    void theWaitingRoomIsKnownByItsOwnLine() {
        assertTrue(RestartPlan.saysLimbo("Connected to proxy limbo. Run /leave to get out."));
        assertFalse(RestartPlan.saysLimbo("WARNING: Servers are updating, do not teleport or you will lose your location"));
        assertFalse(RestartPlan.saysLimbo(null));
    }

    @Test
    void beingPutBackIsBeingOutOfTheWaitingRoomWithTheSidebarUpAndStayingThere() {
        long moved = 1_000_000L;
        // Still in the world it was parked in, however long ago and whatever shows.
        assertFalse(RestartPlan.backFromLimbo(false, moved, true, moved + 60_000L));
        // Out of it, but only just: it has to stay.
        assertFalse(RestartPlan.backFromLimbo(true, moved, true, moved + 3_000L));
        // Out and settled, but nothing a world would show.
        assertFalse(RestartPlan.backFromLimbo(true, moved, false, moved + 60_000L));
        assertTrue(RestartPlan.backFromLimbo(true, moved, true, moved + RestartPlan.SETTLE_MILLIS));
    }

    @Test
    void leaveHasWorkedWhenWeAreOutOfTheWaitingRoomAndStayOut() {
        long moved = 2_000_000L;
        assertTrue(RestartPlan.worldAfterLeave(true, moved, moved + RestartPlan.LEAVE_SETTLE_MILLIS));
        // Not moved: still in the waiting room, whenever the command went out.
        assertFalse(RestartPlan.worldAfterLeave(false, moved, moved + 60_000L));
        // Only just arrived.
        assertFalse(RestartPlan.worldAfterLeave(true, moved, moved + 500L));
    }

    @Test
    void aFreshJoinIsOnlyBelievedWhenNothingHasSaidRestartSince() {
        long joined = 5_000_000L;
        assertTrue(RestartPlan.homeProven(joined, joined - 30_000L, true, joined + RestartPlan.SETTLE_MILLIS));
        // Told about the restart again after joining: parked again.
        assertFalse(RestartPlan.homeProven(joined, joined + 1_000L, true, joined + 60_000L));
        assertFalse(RestartPlan.homeProven(joined, joined - 30_000L, false, joined + 60_000L));
        assertFalse(RestartPlan.homeProven(joined, joined - 30_000L, true, joined + 2_000L));
        assertFalse(RestartPlan.homeProven(0, 0, true, 60_000L));
    }
}
