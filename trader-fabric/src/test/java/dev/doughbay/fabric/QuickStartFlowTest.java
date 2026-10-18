package dev.doughbay.fabric;
import org.junit.jupiter.api.Test;
import dev.doughbay.core.execution.ExecutionResult;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class QuickStartFlowTest {
 @Test void recoveryOrActiveSessionCannotSaveLimitsOrStart(){
  var calls=new ArrayList<String>();
  var r=QuickStartFlow.start(100,500,()->"Recovered session must keep caps",(a,b)->{calls.add("save");return "";},()->{calls.add("start");return ExecutionResult.awaitingPlayer("started");});
  assertFalse(r.ok());assertTrue(calls.isEmpty());
 }
 @Test void invalidLimitsAndFailedSaveNeverStart(){
  var calls=new ArrayList<String>();
  assertFalse(QuickStartFlow.start(501,500,()->null,(a,b)->{calls.add("save");return "";},()->{calls.add("start");return ExecutionResult.awaitingPlayer("started");}).ok());
  assertTrue(calls.isEmpty());
  assertFalse(QuickStartFlow.start(100,500,()->null,(a,b)->"Disk refused",()->{calls.add("start");return ExecutionResult.awaitingPlayer("started");}).ok());
  assertTrue(calls.isEmpty());
 }
 @Test void explicitStartSavesThenRunsNativeStartExactlyOnceAndReportsRefusal(){
  var calls=new ArrayList<String>();
  var r=QuickStartFlow.start(100,500,()->null,(a,b)->{calls.add("save");return "";},()->{calls.add("start");return ExecutionResult.refused("Live snapshot stale");});
  assertEquals(List.of("save","start"),calls);assertFalse(r.ok());assertTrue(r.detail().contains("has not started"));
 }
}
