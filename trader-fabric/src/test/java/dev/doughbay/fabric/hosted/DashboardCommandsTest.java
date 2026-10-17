package dev.doughbay.fabric.hosted;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
class DashboardCommandsTest {
 final DashboardCommands inbox=new DashboardCommands();final AtomicInteger calls=new AtomicInteger();
 final DashboardCommands.Handler handler=a->{calls.incrementAndGet();return new DashboardCommands.Result("completed","ok");};
 DashboardCommands.Command command(String id,String action){return new DashboardCommands.Command(id,action,1000,31_000);}
 @Test void repeatedQueuedAndCompletedIdsExecuteExactlyOnce(){inbox.connected(1);var c=command("cmd_1","pause");assertNull(inbox.receive(1,c,2000).result());assertNull(inbox.receive(1,c,2100).result());assertEquals("completed",inbox.dispatch(1,c.id(),2200,handler).status());assertEquals("completed",inbox.receive(1,c,2300).result().status());inbox.dispatch(1,c.id(),2400,handler);assertEquals(1,calls.get());}
 @Test void changedActionCannotReuseAnIdToExecuteAgain(){inbox.connected(1);inbox.receive(1,command("cmd_1","pause"),2000);inbox.dispatch(1,"cmd_1",2100,handler);assertEquals("completed",inbox.receive(1,command("cmd_1","resume"),2200).result().status());inbox.dispatch(1,"cmd_1",2300,handler);assertEquals(1,calls.get());}
 @Test void commandExpiresWhileWaitingForTheGameThread(){inbox.connected(1);inbox.receive(1,command("cmd_1","resume"),2000);assertEquals("expired",inbox.dispatch(1,"cmd_1",31_000,handler).reason());assertEquals(0,calls.get());}
 @Test void disconnectPreventsPreviouslyQueuedWork(){inbox.connected(1);inbox.receive(1,command("cmd_1","resume"),2000);inbox.disconnected(1);assertEquals("connection changed",inbox.dispatch(1,"cmd_1",2100,handler).reason());assertEquals(0,calls.get());}
 @Test void reconnectDoesNotReplayOldSocketCommands(){inbox.connected(1);inbox.receive(1,command("cmd_1","resume"),2000);inbox.disconnected(1);inbox.connected(2);assertNotNull(inbox.receive(2,command("cmd_1","resume"),2300).result());assertEquals("connection changed",inbox.receive(1,command("cmd_2","pause"),2300).result().reason());assertEquals(0,calls.get());}
 @Test void unsupportedCommandsAndOverlongLifetimesNeverExecute(){inbox.connected(1);assertEquals("unsupported",inbox.receive(1,command("cmd_1","send_chat"),2000).result().reason());assertEquals("expired",inbox.receive(1,new DashboardCommands.Command("cmd_2","pause",1000,32_000),2000).result().reason());assertEquals("expired",inbox.receive(1,command("cmd_3","pause"),31_000).result().reason());assertEquals(0,calls.get());}
 @Test void fullInboxRejectsInsteadOfEvictingLiveIdentities(){inbox.connected(1);for(int n=0;n<256;n++)assertNull(inbox.receive(1,command("cmd_"+n,"pause"),2000).result());assertEquals("busy",inbox.receive(1,command("cmd_over","pause"),2000).result().reason());inbox.dispatch(1,"cmd_0",2100,handler);inbox.dispatch(1,"cmd_0",2200,handler);assertEquals(1,calls.get());}
 @Test void swarmConsentCommandsKeepTheSameExpiryAndReconnectBarriers(){
  inbox.connected(1);assertNull(inbox.receive(1,command("cmd_on","swarm_enable"),2000).result());
  assertEquals("expired",inbox.dispatch(1,"cmd_on",31000,handler).reason());assertEquals(0,calls.get());
  assertNull(inbox.receive(1,command("cmd_off","swarm_disable"),2000).result());inbox.disconnected(1);
  assertEquals("connection changed",inbox.dispatch(1,"cmd_off",2100,handler).reason());assertEquals(0,calls.get());
 }
 @Test void controllerExceptionIsNotForwardedOrRetried(){inbox.connected(1);inbox.receive(1,command("cmd_1","resume"),2000);var result=inbox.dispatch(1,"cmd_1",2100,a->{calls.incrementAndGet();throw new IllegalStateException("C:/Users/private Bearer secret");});assertFalse(result.reason().contains("private"));inbox.dispatch(1,"cmd_1",2200,handler);assertEquals(1,calls.get());}
}
