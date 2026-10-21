package dev.doughbay.fabric;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
class WebPanelActionDeadlineTest {
 @Test void timedOutQueuedPaymentSubmissionCannotExecuteLater(){
  var game=new ArrayList<Runnable>();var calls=new AtomicInteger();
  assertEquals("unknown",WebPages.boundedPanelAction(game::add,()->{calls.incrementAndGet();return "queued";},"unknown",1));
  game.getFirst().run();assertEquals(0,calls.get());
 }
 @Test void anOnTimeSubmissionRunsExactlyOnce(){
  var calls=new AtomicInteger();assertEquals("queued",WebPages.boundedPanelAction(Runnable::run,()->{calls.incrementAndGet();return "queued";},"unknown",100));assertEquals(1,calls.get());
 }
}
