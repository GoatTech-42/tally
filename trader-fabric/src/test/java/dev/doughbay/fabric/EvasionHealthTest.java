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
    void aFallInHealthOnceSettledIs() {
        assertTrue(EvasionGuard.tookDamage(20f, 14f, EvasionGuard.HEALTH_SETTLE_MILLIS));
        assertTrue(EvasionGuard.tookDamage(4.5f, 2.5f, 60_000));
        // Healing and holding steady are neither.
        assertFalse(EvasionGuard.tookDamage(4.5f, 4.5f, 60_000));
        assertFalse(EvasionGuard.tookDamage(4.5f, 9f, 60_000));
    }
}
