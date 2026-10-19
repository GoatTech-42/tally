package dev.doughbay.fabric.staffwatch;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StaffPacketHealthTest {
    @Test void staffSpecificCountersDistinguishModeChangesFromInitialPresenceAndNeverPublishIdentities() {
        var health=new StaffPacketHealth(); health.report(1000,0,0);
        var first=java.util.UUID.randomUUID();var second=java.util.UUID.randomUUID();
        health.combinedStaff(first,"survival");health.combinedStaff(first,"survival");
        health.combinedStaff(first,"spectator");health.combinedStaff(second,"spectator");
        health.combinedStaff(first,"untrusted arbitrary value");
        String report=health.report(61000,2,0);
        assertTrue(report.contains("combinedStaffEntries=4 combinedStaffSpectators=2 combinedStaffModeChanges=1"));
        assertTrue(report.contains("recognizedModeChanges=0"),"passive combined counts must not claim accepted local evidence");
        assertFalse(report.contains(first.toString()));assertFalse(report.contains(second.toString()));
        assertFalse(report.contains("untrusted"));
    }
    @Test void reconnectOrRegionBoundaryDoesNotInventAStaffModeChange() {
        var health=new StaffPacketHealth();health.report(1000,0,0);
        var id=java.util.UUID.randomUUID();health.combinedStaff(id,"survival");
        health.resetStaffBaselines();health.combinedStaff(id,"spectator");
        assertTrue(health.report(61000,1,0).contains("combinedStaffEntries=2 combinedStaffSpectators=1 combinedStaffModeChanges=0"));
    }
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
