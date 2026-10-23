package dev.doughbay.fabric.discord;

import dev.doughbay.fabric.OrderMetrics;
import dev.doughbay.storage.Database;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PanelReadModelTest {
    @TempDir Path root;
    @Test void historicalMemberCannotPoisonCurrentCapacityAndMissingCurrentReportStaysUnknown() throws Exception {
        var names=PanelReadModel.currentMembers(List.of("A","B","C","a"),"A");
        assertEquals(List.of("A","B","C"),names);
        var reports=root.resolve("order-reports");Files.createDirectories(reports);
        var mapper=new com.fasterxml.jackson.databind.ObjectMapper();
        for(String name:names){
            var report=new OrderMetrics.Report(name,100_000,100_000,1,List.of(),0L,0L,90,90,0,List.of(),Map.of(),"");
            var hash=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(name.toLowerCase(Locale.ROOT).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            Files.write(reports.resolve(hash+".json"),mapper.writeValueAsBytes(report));
        }
        var capacity=OrderMetrics.capacity(PanelReadModel.capacityAccounts(root,"",names),100_001);
        assertEquals(270,capacity.total());assertEquals(3,capacity.known());assertEquals(3,capacity.accounts());
        var selected=PanelReadModel.capacityAccounts(root,"Historical",names);
        assertEquals("Historical",selected.getFirst().name());assertNull(selected.getFirst().report());
        var missing=new ArrayList<>(names);missing.add("D");
        capacity=OrderMetrics.capacity(PanelReadModel.capacityAccounts(root,"",missing),100_001);
        assertEquals(3,capacity.known());assertEquals(4,capacity.accounts());
    }
    @Test void currentActivityIsScopedAndOldSelectedRecordIsExplicitlyStale() throws Exception {
        var ledger=root.resolve("test.db");
        try(var db=new Database(ledger);var query=db.connection().prepareStatement("INSERT INTO automation_session_state(client,controller_state,detail,updated_at,session_open,run_mode,trades_started,committed_spend,session_started_at,uncertain_active) VALUES(?,?,?,?,1,'CONTINUOUS',0,0,1,0)")){
            for(String name:List.of("A","B","C","Historical")){
                query.setString(1,name);query.setString(2,"READING_ORDERS");query.setString(3,"Collecting this account’s order");query.setLong(4,name.equals("Historical")?1:100_000);query.executeUpdate();
            }
        }
        var rows=PanelReadModel.activity(ledger,List.of("A","B","C"),"",100_100);
        assertEquals(3,rows.size());assertEquals("A",rows.get(0).path("account").asText());assertTrue(rows.get(0).path("current").asBoolean());
        rows=PanelReadModel.activity(ledger,List.of("A","B","C"),"B",100_100);
        assertEquals(1,rows.size());assertEquals("B",rows.get(0).path("account").asText());
        rows=PanelReadModel.activity(ledger,List.of("A","B","C"),"Historical",100_100);
        assertFalse(rows.get(0).path("current").asBoolean());
    }
    @Test void readableSummaryContainsTheSameMetricsAndSalesWithoutImageIdentityFields(){
        var page=new StatsImage.Page("Money","Hive of3","green",List.<String[]>of(new String[]{"Orders filled","2 /h"}),14,270,
            new String[]{"Time","Item","Profit"},new boolean[]{false,false,true},List.<String[]>of(new String[]{"12:00","stone x64","+$500"}),"Observed fills only");
        var result=PanelReadModel.report(page);
        assertEquals("2 /h",result.path("headline").get(0).get(1).asText());
        assertEquals("stone x64",result.path("rows").get(0).get(1).asText());
        assertEquals("Observed fills only",result.path("footer").asText());assertFalse(result.has("faces"));
    }
    @Test void changingAmountsAndTemporarilyUnavailableSourcesDoNotMoveMoneyFields() throws Exception {
        var first=PanelReadModel.moneyHeadline(List.<String[]>of(new String[]{"Profit 1h","$900K"},
            new String[]{"Swarm rank","Waiting for current Swarm data"}),270,false);
        var second=PanelReadModel.moneyHeadline(List.<String[]>of(new String[]{"Profit 1h","$123456789.12B"},
            new String[]{"Estimated Swarm rank","#3 of 10 active Hives"},new String[]{"Swarm basis","Reported profit"},
            new String[]{"Your Hive reported booked profit · 24h","123,456,789"}),270,false);
        assertEquals(first.stream().map(r->r[0]).toList(),second.stream().map(r->r[0]).toList());
        assertArrayEquals(StatsImage.moneyHeadlineSpans(first),StatsImage.moneyHeadlineSpans(second));
        var a=new StatsImage.Page("Money","Example","green",first,14,270,new String[]{"Item"},new boolean[]{false},List.of(),"Example");
        var b=new StatsImage.Page("Money","Example","green",second,14,270,new String[]{"Item"},new boolean[]{false},List.of(),"Example");
        var before=javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(StatsImage.render(a)));
        var after=javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(StatsImage.render(b)));
        assertEquals(before.getHeight(),after.getHeight());
        var out=Path.of("build/reports/mobile-money");Files.createDirectories(out);Files.write(out.resolve("summary.png"),StatsImage.render(b));
    }
}
