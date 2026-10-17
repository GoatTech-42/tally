package dev.doughbay.fabric;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class EvasionRtpCommandTest {
    @Test void directRouteRequiresCurrentExplicitRegionAndMatchingServerCompletion() {
        var routes=List.of("west","east","eu west","eu central","asia","oceania");
        assertEquals("rtp west",EvasionRtpCommand.resolve("na-west-1",routes));
        assertEquals("rtp east",EvasionRtpCommand.resolve("na-east-2",routes));
        assertEquals("rtp eu west",EvasionRtpCommand.resolve("eu-west-3",routes));
        assertEquals("rtp eu central",EvasionRtpCommand.resolve("eu-central-4",routes));
        assertEquals("rtp asia",EvasionRtpCommand.resolve("asia-5",routes));
        assertEquals("rtp oceania",EvasionRtpCommand.resolve("oceania-6",routes));
        assertEquals("rtp",EvasionRtpCommand.resolve("na-west-1",List.of("east")));
        assertEquals("rtp",EvasionRtpCommand.resolve("europe-1",routes));
        assertEquals("rtp",EvasionRtpCommand.resolve("west",routes));
        assertEquals("rtp",EvasionRtpCommand.resolve("na-west-1\nother command",routes));
        assertEquals("rtp",EvasionRtpCommand.resolve("na-west-1",List.of("west;other")));
    }
}
