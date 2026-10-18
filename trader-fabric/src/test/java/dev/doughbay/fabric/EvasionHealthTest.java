package dev.doughbay.fabric;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EvasionHealthTest {
    @Test
    void warmupNeedsTheRequestedCommandToHaveActuallyDispatched() {
        assertFalse(EvasionGuard.escapeDispatched("rtp west", 100_000, "rtp west", 99_999));
        assertFalse(EvasionGuard.escapeDispatched("rtp west", 100_000, "home other", 101_000));
        assertFalse(EvasionGuard.escapeDispatched("", 100_000, "", 101_000));
        assertTrue(EvasionGuard.escapeDispatched("rtp west", 100_000, "rtp west", 101_000));
    }
    @Test
    void healthArrivingAfterAJoinIsNotAHit() {
        // Joined believing in full health, told 4.5 a moment later: not damage.
        assertFalse(EvasionGuard.tookDamage(20f, 4.5f, 300));
        assertFalse(EvasionGuard.tookDamage(20f, 4.5f, EvasionGuard.HEALTH_SETTLE_MILLIS - 1));
        // Nothing read yet from this body.
        assertFalse(EvasionGuard.tookDamage(-1f, 4.5f, 60_000));
    }

    @Test
    void airGoingDownUnderWaterIsActedOnBeforeItHurts() {
        // A breath is 300 ticks. A second and a half under with the meter falling is enough.
        assertTrue(EvasionGuard.runningOutOfAir(270, 300, true, EvasionGuard.AIR_SETTLE_MILLIS));
        assertTrue(EvasionGuard.runningOutOfAir(0, 300, true, 20_000));
        // A dip under and straight back up is not.
        assertFalse(EvasionGuard.runningOutOfAir(295, 300, true, 300));
        // A full meter, or a head above water with the meter refilling: nothing to run from.
        assertFalse(EvasionGuard.runningOutOfAir(300, 300, true, 60_000));
        assertFalse(EvasionGuard.runningOutOfAir(120, 300, false, 60_000));
    }

    @Test
    void aHitDoesNotWaitForATradeTheWaySomebodyWalkingUpDoes() {
        // Somebody a hundred blocks off: the listing in flight is let finish, for up to a minute.
        assertTrue(EvasionGuard.waitsForTrade(false, 20_000, EvasionGuard.APPROACH_PATIENCE_MILLIS));
        assertFalse(EvasionGuard.waitsForTrade(false, 60_000, EvasionGuard.APPROACH_PATIENCE_MILLIS));
        // Being hit: a second and a half and no more. A mob kills an unarmoured account in under ten.
        assertTrue(EvasionGuard.waitsForTrade(false, 1_000, EvasionGuard.HURT_PATIENCE_MILLIS));
        assertFalse(EvasionGuard.waitsForTrade(false, 1_500, EvasionGuard.HURT_PATIENCE_MILLIS));
        // Nothing in flight: nothing to wait for either way.
        assertFalse(EvasionGuard.waitsForTrade(true, 0, EvasionGuard.APPROACH_PATIENCE_MILLIS));
    }

    @Test
    void aFallInHealthOnceSettledIs() {
        assertTrue(EvasionGuard.tookDamage(20f, 14f, EvasionGuard.HEALTH_SETTLE_MILLIS));
        assertTrue(EvasionGuard.tookDamage(4.5f, 2.5f, 60_000));
        // Healing and holding steady are neither.
        assertFalse(EvasionGuard.tookDamage(4.5f, 4.5f, 60_000));
        assertFalse(EvasionGuard.tookDamage(4.5f, 9f, 60_000));
    }
}
