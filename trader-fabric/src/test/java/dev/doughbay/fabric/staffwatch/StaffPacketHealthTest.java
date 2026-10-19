package dev.doughbay.fabric.staffwatch;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StaffPacketHealthTest {
    @Test void separatesCombinedAddsFromStandaloneModeEvidenceWithoutReclassifyingEither() {
        var health=new StaffPacketHealth();
        assertNull(health.report(1000,0,0));
        health.received(false,true,200,2000);
        health.received(true,true,100,3000);
        health.received(true,false,2,4000);
        health.recognizedMode();
        assertNull(health.report(60999,1,0));
        String report=health.report(61000,1,0);
        assertTrue(report.contains("playerInfo=3 modeUpdates=2 combinedAddMode=1 modeEntries=102 recognizedModeChanges=1"));
        assertTrue(report.contains("lastModeAgeMs=57000 listedStaff=1 spectators=0"));
        assertNull(health.report(61001,1,0));
    }
    @Test void missingPacketsRemainExplicitlyUnknownRatherThanStaffAbsent() {
        var health=new StaffPacketHealth();health.report(1000,0,0);
        String report=health.report(61000,0,0);
        assertTrue(report.contains("modeUpdates=0"));assertTrue(report.contains("lastModeAgeMs=-1"));
        assertFalse(report.contains("no staff"));
    }
}
