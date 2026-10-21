package dev.doughbay.storage;
import dev.doughbay.core.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;
class LegacyRecoveryOwnerRepairTest {
 @TempDir Path root;
 Position bundle(){return new Position(1,"REAL","minecraft:bundle",StackBucket.of(1),1,100,150,1000,0,0,0,Double.NaN,PositionStatus.PURCHASED);}
 AutomationSessionCheckpoint checkpoint(){return new AutomationSessionCheckpoint(true,"CONTINUOUS","PAUSED",94,100,500,2000,"Legacy checkpoint","legacy-key",bundle(),AutomationUncertainExposure.none());}
 Path[] fixture()throws Exception{
  Path live=root.resolve("live.db"),backup=root.resolve("preserved.db");
  try(var db=new Database(live)){new PositionRepository(db).saveAutomationCheckpoint(checkpoint());}
  Files.copy(live,backup);
  try(var db=new Database(live);var s=db.connection().createStatement()){
   // Model the OLD bug without relying on the corrected upsert method.
   s.execute("UPDATE positions SET client='Third'");
   s.execute("INSERT INTO automation_session_state SELECT 'Third',session_open,run_mode,controller_state,106472,committed_spend,session_started_at,updated_at,detail,bound_listing_key,uncertain_active,uncertain_phase,uncertain_listing_key,uncertain_item_key,uncertain_item_id,uncertain_item_count,uncertain_buy_price,uncertain_target_price,uncertain_seller,uncertain_observed_at,session_active_millis,lifetime_active_millis FROM automation_session_state WHERE client=''");
  }return new Path[]{live,backup};
 }
 @Test void oldRecoveryCheckpointNeverClaimsAnUnownedPositionOrAnotherOwner()throws Exception{
  Path file=root.resolve("ownership.db");
  try(var db=new Database(file)){
   var legacy=new PositionRepository(db);legacy.upsert(bundle());var third=new PositionRepository(db);third.setClient("Third");third.upsert(bundle());
   try(var s=db.connection().createStatement();var r=s.executeQuery("SELECT client FROM positions WHERE position_id=1")){assertTrue(r.next());assertEquals("",r.getString(1));}
   try(var s=db.connection().createStatement()){s.execute("UPDATE positions SET client='Peer'");}
   assertThrows(java.sql.SQLException.class,()->third.upsert(bundle()));
  }
 }
 @Test void approvedExactRepairPreservesOriginalTradeAndArchivesFalseCheckpoint()throws Exception{
  var p=fixture();var plan=LegacyRecoveryOwnerRepair.inspect(p[0],p[1],"Third");
  assertThrows(IllegalArgumentException.class,()->LegacyRecoveryOwnerRepair.apply(p[0],p[1],"Third",null));
  LegacyRecoveryOwnerRepair.apply(p[0],p[1],"Third",plan.fingerprint());
  try(var db=new Database(p[0]);var s=db.connection().createStatement()){
   try(var r=s.executeQuery("SELECT client,status,quantity,purchase_price FROM positions WHERE position_id=1")){assertTrue(r.next());assertEquals("",r.getString(1));assertEquals("PURCHASED",r.getString(2));assertEquals(1,r.getInt(3));assertEquals(100,r.getInt(4));}
   try(var r=s.executeQuery("SELECT previous_checkpoint FROM legacy_recovery_owner_repairs")){assertTrue(r.next());assertTrue(r.getString(1).contains("106472"));}
   var own=new PositionRepository(db);own.setClient("Third");own.setLegacyRecovery(false);assertFalse(own.loadAutomationRecovery().sessionOpen());
   assertTrue(new PositionRepository(db).loadAutomationRecovery().unresolvedExposure());
  }
  assertThrows(IllegalStateException.class,()->LegacyRecoveryOwnerRepair.apply(p[0],p[1],"Third",plan.fingerprint()));
 }
 @Test void changedTradeRefusesEntireRepairWithoutArchivingOrClearingAnything()throws Exception{
  var p=fixture();var plan=LegacyRecoveryOwnerRepair.inspect(p[0],p[1],"Third");
  try(var db=new Database(p[0]);var s=db.connection().createStatement()){s.execute("UPDATE positions SET target_price=999 WHERE position_id=1");}
  assertThrows(IllegalStateException.class,()->LegacyRecoveryOwnerRepair.apply(p[0],p[1],"Third",plan.fingerprint()));
  try(var db=new Database(p[0]);var s=db.connection().createStatement();var r=s.executeQuery("SELECT client FROM automation_session_state WHERE client='Third'")){assertTrue(r.next());}
 }
 @Test void checkpointChangesRequireAnotherReviewAndCannotReplayAnApproval()throws Exception{
  var p=fixture();var plan=LegacyRecoveryOwnerRepair.inspect(p[0],p[1],"Third");
  try(var db=new Database(p[0]);var s=db.connection().createStatement()){s.execute("UPDATE automation_session_state SET updated_at=updated_at+1 WHERE client='Third'");}
  assertThrows(IllegalStateException.class,()->LegacyRecoveryOwnerRepair.apply(p[0],p[1],"Third",plan.fingerprint()));
 }
}
