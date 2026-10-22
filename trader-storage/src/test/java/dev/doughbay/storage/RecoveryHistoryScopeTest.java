package dev.doughbay.storage;

import dev.doughbay.core.model.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RecoveryHistoryScopeTest {
    private static Position row(long id,long at,PositionStatus status) {
        return new Position(id,"REAL","minecraft:stone",StackBucket.X64,64,100,120,
                at,at,status.isClosed()?at+10:0,status==PositionStatus.SOLD?120:0,
                status==PositionStatus.SOLD?20:Double.NaN,status);
    }

    @Test void inclusiveSessionBoundaryPreservesOwnerLegacyAndDurableCapFloors() throws Exception {
        for (boolean legacy : new boolean[]{false,true}) try (var db=Database.inMemory()) {
            var own=new PositionRepository(db);own.setClient("Alpha");own.setLegacyRecovery(legacy);
            own.upsert(row(1,999,PositionStatus.SOLD));
            own.upsert(row(2,1000,PositionStatus.PURCHASED));
            own.upsert(row(3,1001,PositionStatus.SOLD));
            own.upsert(row(6,1001,PositionStatus.SIGNAL));
            own.upsert(row(7,1001,PositionStatus.MISSED_BEFORE_PURCHASE));
            var other=new PositionRepository(db);other.setClient("Beta");other.upsert(row(4,1001,PositionStatus.PURCHASED));
            new PositionRepository(db).upsert(row(5,1001,PositionStatus.PURCHASED));
            own.saveAutomationCheckpoint(new AutomationSessionCheckpoint(true,"CONTINUOUS","MONITORING",
                    0,0,1000,2000,"test","",null,AutomationUncertainExposure.none()));
            var r=own.loadAutomationRecovery();
            assertEquals(legacy?3:2,r.tradesStarted());
            assertEquals(legacy?200:100,r.committedSpend());
            assertFalse(r.openPositions().stream().anyMatch(p->p.positionId()==4));
            own.saveAutomationCheckpoint(new AutomationSessionCheckpoint(true,"CONTINUOUS","MONITORING",
                    9,900,1000,2001,"durable floors","",null,AutomationUncertainExposure.none()));
            assertEquals(9,own.loadAutomationRecovery().tradesStarted());
            assertEquals(900,own.loadAutomationRecovery().committedSpend());
        }
    }

    @Test void unknownSessionStartCountsOnlyOpenStockAndAnonymousLegacyStillCoversAllOwners() throws Exception {
        try (var db=Database.inMemory()) {
            var repo=new PositionRepository(db);
            repo.upsert(row(1,1,PositionStatus.SOLD));
            repo.upsert(row(2,1,PositionStatus.PURCHASED));
            var other=new PositionRepository(db);other.setClient("Beta");other.upsert(row(3,2,PositionStatus.LISTED));
            repo.saveAutomationCheckpoint(new AutomationSessionCheckpoint(false,"CONTINUOUS","STOPPED",
                    0,0,0,2000,"unknown start","",null,AutomationUncertainExposure.none()));
            var r=repo.loadAutomationRecovery();
            assertEquals(2,r.tradesStarted());
            assertEquals(200,r.committedSpend());
            assertEquals(2,r.openPositions().size());
        }
    }
}
