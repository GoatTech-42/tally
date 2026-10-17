package dev.doughbay.fabric.hosted;
import dev.doughbay.fabric.AutomatedExecutionDriver;
import dev.doughbay.fabric.automation.AutomationSessionController;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class DashboardRepriceProvenanceTest {
 @Test void tagBelongsToOnlyOnePositionAndIsConsumedOnce() throws Exception {
  var c=new AutomationSessionController(new AutomatedExecutionDriver());
  var f=AutomationSessionController.class.getDeclaredField("dashboardRepricePositionId");f.setAccessible(true);f.setLong(c,42);
  assertFalse(c.consumeDashboardReprice(41));assertTrue(c.consumeDashboardReprice(42));assertFalse(c.consumeDashboardReprice(42));
  assertEquals(AutomationSessionController.State.STOPPED,c.snapshot().state());
 }
}
