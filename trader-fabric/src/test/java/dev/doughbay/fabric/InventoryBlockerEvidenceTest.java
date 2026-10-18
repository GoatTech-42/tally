package dev.doughbay.fabric;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static dev.doughbay.fabric.InventoryBlockerEvidence.Code.*;

class InventoryBlockerEvidenceTest {
    @Test void reportingCannotEraseTheLastObservedNonPanelBlocker(){
        var evidence=new InventoryBlockerEvidence();
        evidence.observe(CURSOR,1000);evidence.observe(GONUTS_PANEL,1100);evidence.observe(REPORT_SCREEN,1200);
        String s=evidence.report(1500);
        assertTrue(s.contains("inventory_blocker=REPORT_SCREEN prior_inventory_blocker=CURSOR prior_inventory_blocker_age_ms=500"));
        evidence.observe(CLEAR,1600);
        assertTrue(evidence.report(1700).contains("prior_inventory_blocker=CLEAR prior_inventory_blocker_age_ms=100"));
    }
    @Test void unknownContextNeverBecomesAnInventedClearState(){
        var evidence=new InventoryBlockerEvidence();evidence.observe(REPORT_SCREEN,1000);
        assertTrue(evidence.report(1200).contains("prior_inventory_blocker=NOT_CAPTURED prior_inventory_blocker_age_ms=-1"));
    }
}
