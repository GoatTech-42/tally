package dev.doughbay.fabric.staffwatch;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DonutServerTest {
    @Test void recognizesMainAndRegionalConnections() {
        assertTrue(DonutServer.matches("donutsmp.net"));
        assertTrue(DonutServer.matches("east.donutsmp.net:25565"));
        assertTrue(DonutServer.matches("WEST.DONUTSMP.NET."));
    }

    @Test void unrelatedServersDoNotAcquireReconnectOwnership() {
        assertFalse(DonutServer.matches("other.example"));
        assertFalse(DonutServer.matches("notdonutsmp.net"));
        assertFalse(DonutServer.matches("donutsmp.net.other.example"));
        assertFalse(DonutServer.matches(null));
        assertFalse(DonutServer.matches(""));
    }
}
