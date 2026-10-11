package dev.doughbay.fabric.automation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WholeStackCollectTest {
    private static boolean whole(int pending, int stack, boolean complete, int requested) {
        return AutomationSessionController.worthCollecting(pending, stack, complete, requested, true, true);
    }

    private static boolean older(int pending, int stack, boolean complete, int requested) {
        return AutomationSessionController.worthCollecting(pending, stack, complete, requested, true, false);
    }

    @Test
    void aPartStackIsLeftOnAnOrderThatIsStillFilling() {
        // Three, fourteen and fifty-three of a stack of 64: each used to get a trip.
        assertFalse(whole(3, 64, false, 64));
        assertFalse(whole(14, 64, false, 64));
        assertFalse(whole(53, 64, false, 192));
        assertFalse(whole(63, 64, false, 64));
        assertTrue(whole(64, 64, false, 192));
        assertTrue(whole(100, 64, false, 192));
    }

    @Test
    void anOrderThatIsCompletelyFilledIsCollectedWhateverIsOnIt() {
        assertTrue(whole(12, 64, true, 12));
        assertTrue(whole(3, 64, true, 67));
        assertTrue(whole(64, 64, true, 64));
        // Nothing waiting is nothing to collect, finished or not.
        assertFalse(whole(0, 64, true, 64));
    }

    @Test
    void aWholeStackIsWhatTheItemStacksTo() {
        // Ender pearls stack to 16 and a totem to one.
        assertFalse(whole(15, 16, false, 64));
        assertTrue(whole(16, 16, false, 64));
        assertTrue(whole(1, 1, false, 5));
    }

    @Test
    void halfOfASmallOrderNoLongerGetsATrip() {
        // A dozen for the single slots, half delivered: the older rule went for it.
        assertTrue(older(6, 64, false, 12));
        assertFalse(whole(6, 64, false, 12));
        assertTrue(whole(12, 64, true, 12));
    }

    @Test
    void withTheSettingOffItIsAsItWas() {
        assertTrue(older(16, 64, false, 64));
        assertFalse(older(15, 64, false, 64));
        assertTrue(older(3, 64, true, 64));
    }

    @Test
    void anOrderBeingPulledGivesUpItsPartStack() {
        assertTrue(AutomationSessionController.takesAll("taking 14 minecraft:deepslate_diamond_ore before pulling the order (outbid)"));
        assertTrue(AutomationSessionController.takesAll("the order was cancelled with goods still on it"));
        assertTrue(AutomationSessionController.takesAll("a delivery was waiting"));
        assertFalse(AutomationSessionController.takesAll("collecting 128 minecraft:tnt"));
        assertFalse(AutomationSessionController.takesAll(null));
    }
}
