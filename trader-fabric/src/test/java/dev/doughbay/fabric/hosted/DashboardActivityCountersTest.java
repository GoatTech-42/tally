package dev.doughbay.fabric.hosted;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class DashboardActivityCountersTest {
 @Test void incompleteWindowsStayUnavailable(){var c=new DashboardActivityCounters(1000);c.observe(1,"LIST","SUCCEEDED",false,2000,2000);assertFalse(c.snapshot(3_600_999).has("listings"));assertEquals(1,c.snapshot(3_601_000).path("listings").path("h1").asInt());assertFalse(c.snapshot(3_601_000).path("listings").has("h24"));}
 @Test void successesAreDeduplicatedAndRepricesSeparateFromNewListings(){var c=new DashboardActivityCounters(1000);c.observe(1,"LIST","SUCCEEDED",false,2000,2000);c.observe(1,"LIST","SUCCEEDED",false,2000,2000);c.observe(2,"LIST","FAILED",false,3000,3000);c.observe(3,"BUY","SUCCEEDED",false,3000,3000);c.observe(4,"LIST","SUCCEEDED",true,4000,4000);var s=c.snapshot(3_601_000);assertEquals(1,s.path("listings").path("h1").asInt());assertEquals(1,s.path("reprices").path("h1").asInt());}
 @Test void priorProcessAndFutureReceiptsCannotInflateObservedCounts(){var c=new DashboardActivityCounters(1000);c.observe(1,"LIST","SUCCEEDED",false,900,2000);c.observe(2,"LIST","SUCCEEDED",false,3000,2000);assertEquals(0,c.snapshot(3_601_000).path("listings").path("h1").asInt());}
 @Test void hourAndDayWindowsUseTheirActualBoundaries(){var c=new DashboardActivityCounters(1000);c.observe(1,"LIST","SUCCEEDED",false,2000,2000);c.observe(2,"LIST","SUCCEEDED",false,86_400_999,86_400_999);var s=c.snapshot(86_401_000);assertEquals(1,s.path("listings").path("h1").asInt());assertEquals(2,s.path("listings").path("h24").asInt());assertEquals(1,c.snapshot(86_402_000).path("listings").path("h24").asInt());}
}
