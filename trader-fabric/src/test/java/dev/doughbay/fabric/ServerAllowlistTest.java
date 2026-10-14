package dev.doughbay.fabric;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerAllowlistTest {
    private static final Set<String> LISTED = Set.of("donutsmp.net", "singleplayer");

    @Test
    void aRegionOfAListedServerIsThatServer() {
        assertTrue(ServerAllowlist.allows(LISTED, "donutsmp.net"));
        assertTrue(ServerAllowlist.allows(LISTED, "west.donutsmp.net"));
        assertTrue(ServerAllowlist.allows(LISTED, "east.donutsmp.net"));
        assertTrue(ServerAllowlist.allows(LISTED, "asia.donutsmp.net"));
        assertTrue(ServerAllowlist.allows(LISTED, "singleplayer"));
    }

    @Test
    void anythingElseIsStillRefused() {
        assertFalse(ServerAllowlist.allows(LISTED, "notdonutsmp.net"));
        assertFalse(ServerAllowlist.allows(LISTED, "donutsmp.net.example.com"));
        assertFalse(ServerAllowlist.allows(LISTED, "example.com"));
        assertFalse(ServerAllowlist.allows(LISTED, "west.singleplayer"));
        assertFalse(ServerAllowlist.allows(List.of("net"), "donutsmp.net"));
        assertFalse(ServerAllowlist.allows(LISTED, null));
        assertFalse(ServerAllowlist.allows(null, "donutsmp.net"));
    }
}
