package dev.doughbay.fabric;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DashboardWebsiteAccessTest {
    // The hosted dashboard was retired with the public services. The access
    // helper must never launch or copy the dead address, and must say why.
    @Test void retiredServiceNeverLaunchesTheDeadDashboardAddress() {
        List<String> launched = new ArrayList<>();
        DashboardWebsiteAccess access = new DashboardWebsiteAccess(uri -> launched.add(uri.toASCIIString()), value -> {});
        access.open();
        assertEquals(List.of(), launched, "retired dashboard must not be opened");
        assertEquals(ProjectStatus.RETIRED, access.message());
    }

    @Test void retiredServiceNeverCopiesTheDeadDashboardAddress() {
        List<String> copied = new ArrayList<>();
        DashboardWebsiteAccess access = new DashboardWebsiteAccess(uri -> {}, copied::add);
        access.copy();
        assertEquals(List.of(), copied, "retired dashboard address must not be copied");
        assertEquals(ProjectStatus.RETIRED, access.message());
    }

    @Test void retiredServiceOffersNoAddressToType() {
        DashboardWebsiteAccess access = new DashboardWebsiteAccess(uri -> {}, value -> {});
        assertTrue(access.address().isEmpty(),
                "there is no dashboard address to show once the service is retired");
    }
}
