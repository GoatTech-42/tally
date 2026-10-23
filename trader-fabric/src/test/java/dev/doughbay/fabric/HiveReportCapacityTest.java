package dev.doughbay.fabric;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class HiveReportCapacityTest {
    OrderMetrics.Account bot(String name,int slots,long now) {
        return new OrderMetrics.Account(name,new OrderMetrics.Report(name,now,now,1000,List.of(),0L,0L,slots,45,0,List.of(),Map.of(),"Observed"));
    }
    @Test void threeNinetySlotAccountsShow270AndSelectedAccountShows90() {
        long now=100_000;
        var a=bot("A",90,now);var b=bot("B",90,now);var c=bot("C",90,now);
        assertEquals(270,StatusWebhook.reportSlots(List.of(a,b,c),now));
        assertEquals(90,StatusWebhook.reportSlots(List.of(b),now));
    }
    @Test void capacitiesComeFromEachAccountNotHostMultipliedByCount() {
        long now=100_000;
        assertEquals(153,StatusWebhook.reportSlots(List.of(bot("A",90,now),bot("B",18,now),bot("C",45,now)),now));
    }
    @Test void unknownCapacityDoesNotProduceMisleadingHostOnlyGrid() {
        long now=100_000;
        assertEquals(0,StatusWebhook.reportSlots(List.of(bot("A",90,now),new OrderMetrics.Account("B",null)),now));
        var partial=OrderMetrics.capacity(List.of(bot("A",90,now),bot("B",18,1)),now);
        assertEquals(1,partial.known());assertEquals(90,partial.total());
    }
}
