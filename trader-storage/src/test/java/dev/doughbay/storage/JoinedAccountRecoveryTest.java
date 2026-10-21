package dev.doughbay.storage;

import dev.doughbay.core.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class JoinedAccountRecoveryTest {
    @TempDir Path root;
    private static Position position(long id, PositionStatus status) {
        return new Position(id,"REAL","minecraft:bundle",StackBucket.of(1),1,100,150,
                1000,0,status.isClosed()?2000:0,0,Double.NaN,status);
    }
    private static AutomationSessionCheckpoint checkpoint(Position p, int trades, long spend) {
        return new AutomationSessionCheckpoint(true,"CONTINUOUS","PAUSED",trades,spend,
                500,100,200,2000,"Recovery test","",p,AutomationUncertainExposure.none());
    }
    private void seed(Path file)throws Exception {
        try(var db=new Database(file)) {
            var legacy=new PositionRepository(db);
            legacy.saveAutomationCheckpoint(checkpoint(position(1,PositionStatus.PURCHASED),94,100));
            legacy.upsert(position(2,PositionStatus.CANCELLED));
            var peer=new PositionRepository(db);peer.setClient("Peer");
            peer.saveAutomationCheckpoint(checkpoint(position(3,PositionStatus.PURCHASED),90,100));
        }
    }
    @Test void newMemberDoesNotInheritLegacyBundleCheckpointCountersOrCancelledStock()throws Exception {
        Path file=root.resolve("shared.db");seed(file);
        try(var db=new Database(file)) {
            var fresh=new PositionRepository(db);fresh.setClient("Third");
            assertTrue(fresh.loadAutomationRecovery().unresolvedExposure(),"Reproduce legacy inclusion before selecting joined scope");
            fresh.setLegacyRecovery(false);
            var recovery=fresh.loadAutomationRecovery();
            assertFalse(recovery.sessionOpen());assertFalse(recovery.unresolvedExposure());
            assertEquals(0,recovery.tradesStarted());assertEquals(0,recovery.committedSpend());
            assertTrue(fresh.findRecentlyCancelled("REAL",0).isEmpty());
            assertEquals(AutomationRuntimeSummary.empty(),fresh.automationRuntimeSummary());
            try(var s=db.connection().createStatement();var r=s.executeQuery("SELECT status,client FROM positions WHERE position_id=1")) {
                assertTrue(r.next());assertEquals("PURCHASED",r.getString(1));assertEquals("",r.getString(2));
            }
        }
    }
    @Test void ownRecoveryAndCapsRemainLockedWhileOtherAccountHistoryDoesNotInflateThem()throws Exception {
        Path file=root.resolve("owned.db");seed(file);
        try(var db=new Database(file)) {
            var fresh=new PositionRepository(db);fresh.setClient("Third");fresh.setLegacyRecovery(false);
            fresh.saveAutomationCheckpoint(checkpoint(position(4,PositionStatus.PURCHASED),1,100));
            var recovery=fresh.loadAutomationRecovery();
            assertTrue(recovery.sessionOpen());assertTrue(recovery.unresolvedExposure());
            assertEquals(1,recovery.tradesStarted());assertEquals(100,recovery.committedSpend());
            assertEquals(java.util.List.of(4L),recovery.openPositions().stream().map(Position::positionId).toList());
            var legacy=new PositionRepository(db);assertEquals(3,legacy.findOpen("REAL").size(),"All records remain available to legacy reconciliation");
        }
    }
    @Test void actualPersistenceWorkerAppliesJoinedScopeBeforeItsFirstRead()throws Exception {
        Path file=root.resolve("worker.db");seed(file);
        var worker=new AutomationPersistenceWorker(file,false);worker.setClient("Third");worker.start();
        try {
            long until=System.nanoTime()+5_000_000_000L;
            while(!worker.status().ready()&&System.nanoTime()<until)Thread.sleep(10);
            assertTrue(worker.status().ready());assertFalse(worker.status().recovery().unresolvedExposure());
            assertEquals(0,worker.status().recovery().tradesStarted());
        }finally{
            worker.close();
            long until=System.nanoTime()+5_000_000_000L;
            while(worker.status().loadState()!=AutomationPersistencePort.LoadState.CLOSED&&System.nanoTime()<until)Thread.sleep(10);
            assertEquals(AutomationPersistencePort.LoadState.CLOSED,worker.status().loadState());
        }
    }
    @Test void strictRecoveryWithoutAnOwnerFailsClosed()throws Exception {
        try(var db=new Database(root.resolve("owner.db"))) {
            var repo=new PositionRepository(db);repo.setLegacyRecovery(false);
            assertThrows(java.sql.SQLException.class,repo::loadAutomationRecovery);
        }
    }
}
