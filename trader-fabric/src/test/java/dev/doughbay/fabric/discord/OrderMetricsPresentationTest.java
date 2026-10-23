package dev.doughbay.fabric.discord;

import dev.doughbay.fabric.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class OrderMetricsPresentationTest {
    OrderMetrics.Report report(String name,long at,long funds) {
        return new OrderMetrics.Report(name,at,at,1000,
                List.of(new AutomatedExecutionDriver.OwnOrderRow("minecraft:stone","minecraft:stone",64,10,1,0)),
                funds,10L,90,45,1,List.of(at-1000),Map.of(),"Observed");
    }
    String value(List<String[]> rows,String key){return rows.stream().filter(r->r[0].equals(key)).findFirst().orElseThrow()[1];}
    @Test void moneySummaryUsesOnlySelectedMembersAndMarksPartialCoverage() {
        long now=100_000;
        var a=new OrderMetrics.Account("A",report("A",now,100));
        var b=new OrderMetrics.Account("B",report("B",now,900));
        var selected=OrderMetricsPresentation.summary(List.of(b),now);
        assertEquals("1",value(selected,"Unfilled orders"));
        assertEquals(DiscordBridge.money(900),value(selected,"Order funds"));
        var all=OrderMetricsPresentation.summary(List.of(a,b,new OrderMetrics.Account("C",null)),now);
        assertEquals("2 (partial)",value(all,"Unfilled orders"));
        assertEquals(DiscordBridge.money(1000)+" (partial)",value(all,"Order funds"));
        assertEquals("2 observed (partial)",value(all,"Orders filled 1h"));
        assertEquals("2/3 accounts fresh",value(all,"Order coverage"));
    }
    @Test void staleAndUnknownAreNotReportedAsZero() {
        var rows=OrderMetricsPresentation.summary(List.of(new OrderMetrics.Account("A",report("A",1000,100))),500_000);
        assertEquals("unknown",value(rows,"Order funds"));
        assertEquals("unknown",value(rows,"Orders filled 1h"));
    }
    @Test void actualOrdersRendererSeparatesAccountsAndProducesThePanelImage() throws Exception {
        long now=100_000;
        var a=new OrderMetrics.Account("Fixture A",report("Fixture A",now,100));
        var b=new OrderMetrics.Account("Fixture B",report("Fixture B",now,900));
        var chosen=DiscordBridge.ordersPageFor(List.of(b),10,now,now,"green","Fixture B only");
        assertEquals(1,chosen.rows().size());assertEquals("Fixture B",chosen.rows().getFirst()[0]);
        assertFalse(chosen.rows().stream().anyMatch(row->row[0].equals("Fixture A")));
        var all=DiscordBridge.ordersPageFor(List.of(a,b),10,now,now,"green","Synthetic fixture: all accounts");
        assertEquals(List.of("Fixture A","Fixture B"),all.rows().stream().map(row->row[0]).toList());
        var directory=java.nio.file.Path.of("build/reports/order-metrics");java.nio.file.Files.createDirectories(directory);
        java.nio.file.Files.write(directory.resolve("selected-account.png"),StatsImage.render(chosen));
        java.nio.file.Files.write(directory.resolve("all-accounts-orders.png"),StatsImage.render(all));
        var money=new StatsImage.Page("Money — synthetic fixture", "Three accounts with 90 slots each", "green",
                OrderMetricsPresentation.summary(List.of(a,b,new OrderMetrics.Account("Fixture C",report("Fixture C",now,500))),now),
                43,270,new String[]{"Account","Scope"},new boolean[]{false,false},
                List.<String[]>of(new String[]{"All accounts","Synthetic QA data"}),"Rendering fixture only; no live bot data.");
        java.nio.file.Files.write(directory.resolve("all-accounts-money.png"),StatsImage.render(money));
    }
}
