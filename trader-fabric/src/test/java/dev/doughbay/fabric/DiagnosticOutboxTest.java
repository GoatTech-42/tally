package dev.doughbay.fabric;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import dev.doughbay.fabric.hosted.DashboardProtocol;
import static org.junit.jupiter.api.Assertions.*;
class DiagnosticOutboxTest {
 @TempDir Path root;
 private final long now=1_800_000_000_000L;
 private String settings(){return SettingsCode.exportForReport();}
 private List<String> start(){return List.of("life event=START build=1.0 reason=GAME_START");}
 @Test void aGapOnlyOldSessionCannotHideAFreshSession()throws Exception{
  try(var old=new DiagnosticOutbox(root,"1.0",now);var current=new DiagnosticOutbox(root,"2.0",now+1)){
   old.append("LIFECYCLE",start(),settings(),now);var original=old.batch(now+2,32);old.reject(original);
   current.append("LIFECYCLE",List.of("life event=START build=2.0 reason=GAME_START"),settings(),now+3);
   var gap=old.batch(now+4,32);assertEquals(old.session(),gap.path("sessionId").asText());assertTrue(gap.path("events").isEmpty());assertEquals(1,gap.path("gaps").size());old.sent(gap);
   var fresh=current.batch(now+5,32);assertEquals(current.session(),fresh.path("sessionId").asText());current.sent(fresh);
   assertTrue(current.acknowledge(DashboardProtocol.JSON.createObjectNode().put("version",1).put("sessionId",current.session()).put("throughSequence",1)));
   assertEquals(gap.path("gaps"),old.batch(now+6,32).path("gaps"),"The old declared loss remains pending until its own ACK");
  }
 }
 @Test void sessionsCreatedInTheSameMillisecondAllReceiveATurn()throws Exception{
  try(var a=new DiagnosticOutbox(root,"1.0",now);var b=new DiagnosticOutbox(root,"1.0",now);var c=new DiagnosticOutbox(root,"1.0",now)){
   for(var out:List.of(a,b,c))out.append("LIFECYCLE",start(),settings(),now);
   Set<String> served=new HashSet<>();String first="";
   for(int i=0;i<3;i++){var frame=a.batch(now+1,32);String id=frame.path("sessionId").asText();if(i==0)first=id;assertTrue(served.add(id));a.sent(frame);}
   assertEquals(Set.of(a.session(),b.session(),c.session()),served);
   assertEquals(first,a.batch(now+2,32).path("sessionId").asText());
  }
 }
 @Test void newerSessionFaultMustNotWaitForAnOlderSessionsEntireBacklog()throws Exception{
  try(var old=new DiagnosticOutbox(root,"1.0",now);var current=new DiagnosticOutbox(root,"2.0",now+1);var peer=new DiagnosticOutbox(root,"2.0",now+2)){
   old.append("LIFECYCLE",start(),settings(),now);
   for(int i=1;i<=100;i++)old.append("DECISION",List.of("dec v=1 at="+(now+i)+" scope=DRIVER event=SUCCEEDED code=LIST reason=NONE market=minecraft:stone requested=-1 selected=64 price=100 limit=0"),"",now+i);
   current.append("LIFECYCLE",List.of("life event=START build=2.0 reason=GAME_START"),settings(),now+102);
   current.append("FAULT",List.of("ctx fault_stage=DRIVER_CLICK fault_type=IllegalStateException fault_age_ms=0 fault_count=1"),"",now+103);
   peer.append("LIFECYCLE",List.of("life event=START build=2.0 reason=GAME_START"),settings(),now+104);
   var first=old.batch(now+200,32);assertEquals(old.session(),first.path("sessionId").asText());old.sent(first);
   var fresh=current.batch(now+201,32);
   assertEquals(current.session(),fresh.path("sessionId").asText(),"Fresh-session diagnostics must not sit behind every old event");
   assertEquals("2.0",fresh.path("build").asText());assertEquals("FAULT",fresh.path("events").get(1).path("kind").asText());
   current.sent(fresh);
   var third=peer.batch(now+202,32);assertEquals(peer.session(),third.path("sessionId").asText(),"A third shared-spool session also needs a turn");peer.sent(third);
   var retry=old.batch(now+203,32);assertEquals(first.path("events"),retry.path("events"),"Unacknowledged old evidence is retained byte-for-byte");
   assertFalse(current.acknowledge(DashboardProtocol.JSON.createObjectNode().put("version",1).put("sessionId",current.session()).put("throughSequence",3)));
   assertTrue(current.acknowledge(DashboardProtocol.JSON.createObjectNode().put("version",1).put("sessionId",current.session()).put("throughSequence",2)));
   assertEquals(first.path("events"),old.batch(now+204,32).path("events"),"Another session's ACK cannot erase old events");
  }
 }
 @Test void unackedEventsAndCredentialSurviveRestartWithoutChangingOldSession()throws Exception{
  String secret,old;
  try(var out=new DiagnosticOutbox(root,"1.0",now)){secret=out.credential();old=out.session();out.append("LIFECYCLE",start(),settings(),now);}
  try(var out=new DiagnosticOutbox(root,"1.0",now+1000)){assertEquals(secret,out.credential());assertNotEquals(old,out.session());var frame=out.batch(now+1000,32);assertEquals(old,frame.path("sessionId").asText());assertEquals(1,frame.path("events").size());assertFalse(frame.path("events").get(0).path("settingsCode").asText().isEmpty());}
 }
 @Test void futureAndWrongSessionAcknowledgementsCannotEraseEvents()throws Exception{
  try(var out=new DiagnosticOutbox(root,"1.0",now)){out.append("LIFECYCLE",start(),settings(),now);var frame=out.batch(now,32);var ack=DashboardProtocol.JSON.createObjectNode().put("version",1).put("sessionId",out.session()).put("throughSequence",1);assertFalse(out.acknowledge(ack));out.sent(frame);assertFalse(out.acknowledge(ack.deepCopy().put("throughSequence",2)));assertFalse(out.acknowledge(ack.deepCopy().put("sessionId",UUID.randomUUID().toString())));assertNotNull(out.batch(now,32));assertTrue(out.acknowledge(ack));assertNull(out.batch(now,32));}
 }
 @Test void boundedQueueDeclaresGapsWithoutOverlappingSurvivingEvents()throws Exception{
  try(var out=new DiagnosticOutbox(root,"1.0",now,2)){out.append("LIFECYCLE",start(),settings(),now);out.append("SNAPSHOT",List.of("ctx captured_at_ms="+(now+1)),"",now+1);out.append("SNAPSHOT",List.of("ctx captured_at_ms="+(now+2)),"",now+2);var frame=out.batch(now+3,32);assertEquals(2,frame.path("events").size());assertEquals(1,frame.path("gaps").size());long missing=frame.path("gaps").get(0).path("from").asLong();for(var event:frame.path("events"))assertNotEquals(missing,event.path("sequence").asLong());assertEquals("OUTBOX_FULL",frame.path("gaps").get(0).path("reason").asText());}
 }
 @Test void expirationAndPermanentRejectionAreExplicitAndStopResendingThoseEvents()throws Exception{
  try(var out=new DiagnosticOutbox(root,"1.0",now)){out.append("LIFECYCLE",start(),settings(),now);var frame=out.batch(now+DiagnosticOutbox.TTL+1,32);assertTrue(frame.path("events").isEmpty());assertEquals("OUTBOX_EXPIRED",frame.path("gaps").get(0).path("reason").asText());}
  try(var out=new DiagnosticOutbox(root.resolve("other"),"1.0",now)){out.append("LIFECYCLE",start(),settings(),now);var frame=out.batch(now,32);out.reject(frame);var next=out.batch(now,32);assertTrue(next.path("events").isEmpty());assertEquals("OUTBOX_CORRUPT",next.path("gaps").get(0).path("reason").asText());}
 }
 @Test void arbitraryTextAndMismatchedKindsNeverReachDurableStorage()throws Exception{
  try(var out=new DiagnosticOutbox(root,"1.0",now)){assertThrows(IllegalArgumentException.class,()->out.append("SNAPSHOT",List.of("ctx player=private_player"),settings(),now));assertThrows(IllegalArgumentException.class,()->out.append("ACTION",start(),settings(),now));assertThrows(IllegalArgumentException.class,()->out.append("LIFECYCLE",start(),"",now));assertNull(out.batch(now,32));}
 }
 @Test void observationFloodCannotStrandARealFailureBehindTheRoutineBacklog()throws Exception{
  try(var out=new DiagnosticOutbox(root,"1.0",now)){
   out.append("LIFECYCLE",start(),settings(),now);var initial=out.batch(now,32);out.sent(initial);out.acknowledge(DashboardProtocol.JSON.createObjectNode().put("version",1).put("sessionId",out.session()).put("throughSequence",1));
   for(int i=1;i<=200;i++)out.append("SNAPSHOT",List.of("ctx captured_at_ms="+(now+i)),"",now+i);
   out.append("FAULT",List.of("ctx fault_stage=DRIVER_CLICK fault_type=IllegalStateException fault_age_ms=0 fault_count=1"),"",now+201);
   var frame=out.batch(now+202,32);assertEquals(17,frame.path("events").size());assertEquals("FAULT",frame.path("events").get(16).path("kind").asText());assertEquals(2,frame.path("gaps").get(0).path("from").asLong());assertEquals(185,frame.path("gaps").get(0).path("to").asLong());
   assertThrows(IllegalArgumentException.class,()->out.reconcileStatus(DashboardProtocol.JSON.createObjectNode()));
  }
 }
 @Test void conflictingSessionIsReplacedWithoutLosingPendingEventsOrSettings()throws Exception{
  try(var out=new DiagnosticOutbox(root,"1.0",now)){
   out.append("LIFECYCLE",start(),settings(),now);var original=out.batch(now,32);String old=out.session();out.sent(original);out.recoverConflict(original);
   var repaired=out.batch(now,32);assertNotEquals(old,repaired.path("sessionId").asText());assertEquals(out.session(),repaired.path("sessionId").asText());assertEquals(original.path("events"),repaired.path("events"));assertEquals("1.0",repaired.path("build").asText());
   out.append("SNAPSHOT",List.of("ctx captured_at_ms="+(now+1)),"",now+1);assertEquals(2,out.batch(now+1,32).path("events").size());
  }
 }
 @Test void acknowledgedSettingsAreNotResentAndServerExpiryRestoresAnExplicitMissingPrefix()throws Exception{
  try(var out=new DiagnosticOutbox(root,"1.0",now)){
   out.append("LIFECYCLE",start(),settings(),now);var first=out.batch(now,32);out.sent(first);assertTrue(out.acknowledge(DashboardProtocol.JSON.createObjectNode().put("version",1).put("sessionId",out.session()).put("throughSequence",1)));
   out.append("SNAPSHOT",List.of("ctx captured_at_ms="+(now+1)),"",now+1);var second=out.batch(now+2,32);assertFalse(second.path("events").get(0).has("settingsCode"));
   out.reconcileStatus(DashboardProtocol.JSON.createObjectNode().put("known",false).set("sessions",DashboardProtocol.JSON.createArrayNode()));var recovered=out.batch(now+2,32);assertEquals(1,recovered.path("gaps").get(0).path("from").asLong());assertEquals(1,recovered.path("gaps").get(0).path("to").asLong());assertTrue(recovered.path("events").get(0).has("settingsCode"));
  }
 }
}
