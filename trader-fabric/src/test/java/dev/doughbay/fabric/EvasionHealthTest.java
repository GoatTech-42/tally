package dev.doughbay.fabric;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EvasionHealthTest {
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
    void aFallInHealthOnceSettledIs() {
        assertTrue(EvasionGuard.tookDamage(20f, 14f, EvasionGuard.HEALTH_SETTLE_MILLIS));
        assertTrue(EvasionGuard.tookDamage(4.5f, 2.5f, 60_000));
        // Healing and holding steady are neither.
        assertFalse(EvasionGuard.tookDamage(4.5f, 4.5f, 60_000));
        assertFalse(EvasionGuard.tookDamage(4.5f, 9f, 60_000));
    }
}
