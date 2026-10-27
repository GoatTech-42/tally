package dev.doughbay.fabric;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class JoinRecoveryTest {
    @Test void networkFailuresRotateThroughAllRestartRegionsWithoutTheStickyGenericHost() {
        String host = "donutsmp.net";
        for (String expected : new String[]{"east", "west", "asia", "oceania", "eu-central", "eu-west", "east"}) {
            host = JoinRecovery.next(host, "", "Failed to connect: Connection timed out: getsockopt");
            assertEquals(expected + ".donutsmp.net", host);
        }
    }
    @Test void customOrderAndPortsArePreservedAndForeignHostsAreExcluded() {
        String routes = "asia.donutsmp.net:25566,foreign.example,west.donutsmp.net";
        assertEquals("asia.donutsmp.net:25566", JoinRecovery.next("donutsmp.net", routes, "Connection refused"));
        assertEquals("west.donutsmp.net", JoinRecovery.next("asia.donutsmp.net:25566", routes, "Connection refused"));
        assertEquals("asia.donutsmp.net:25566", JoinRecovery.next("west.donutsmp.net", routes, "Connection refused"));
        assertEquals("other.example", JoinRecovery.next("other.example", routes, "Connection refused"));
    }
    @Test void refusalsUnknownErrorsAndManualDepartureDoNotChangeRegions() {
        for (String reason : new String[]{"Disconnected", "Invalid session", "Banned: timeout", "Authentication timed out",
                "Staff Watch: timed out", "GoNuts evasion timeout", "Quitting", "GoNuts restart", "Kicked: timeout",
                "Already connected", "Not whitelisted", "Unknown failure"}) {
            assertEquals("east.donutsmp.net", JoinRecovery.next("east.donutsmp.net", "", reason), reason);
        }
    }
    @Test void onePermittedRegionalDoorDoesNotInventAnother() {
        assertEquals("east.donutsmp.net", JoinRecovery.next("east.donutsmp.net", "east.donutsmp.net", "Timed out"));
    }
    @Test void onlyExplicitBansAreTreatedAsPermanentEnforcement() {
        assertTrue(JoinRecovery.permanentEnforcement("You are banned from this server"));
        assertTrue(JoinRecovery.permanentEnforcement("Visit the Ban Appeal page"));
        for (String transient_ : new String[] {"Invalid session", "Already connected", "Not whitelisted", "Connection timed out", "", null})
            assertFalse(JoinRecovery.permanentEnforcement(transient_), String.valueOf(transient_));
    }
}
