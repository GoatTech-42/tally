package dev.doughbay.fabric;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class OrderMetricsTest {
    @TempDir Path root;
    static AutomatedExecutionDriver.OwnOrderRow row(int delivered) {
        return new AutomatedExecutionDriver.OwnOrderRow("minecraft:stone", "minecraft:stone", 64, 10, delivered, 1000);
    }
    void save(OrderMetrics.Report report) throws Exception {
        Path file = OrderMetrics.path(root, report.account()); Files.createDirectories(file.getParent());
        Files.write(file, new ObjectMapper().writeValueAsBytes(report));
    }
    OrderMetrics.Report capture(OrderMetrics metrics, long at, List<AutomatedExecutionDriver.OwnOrderRow> rows) {
        return metrics.snapshot(at, at, rows, CapitalBreakdown.orders(at, rows, Map.of()));
    }
    @Test void countsOnlyObservedCompletionNotInitialFullOrRepeatedReads() {
        var metrics = new OrderMetrics(root, "A", 1000);
        metrics.accept(1100, List.of(row(64))); assertEquals(0, capture(metrics,1100,List.of(row(64))).completed());
        metrics.accept(1200, List.of(row(10)));
        metrics.accept(1300, List.of(row(64)));
        metrics.accept(1400, List.of(row(64)));
        metrics.accept(1300, List.of(row(64)));
        assertEquals(1, capture(metrics,1400,List.of(row(64))).completed());
        assertEquals(1, OrderMetrics.completedSince(capture(metrics,1400,List.of(row(64))),1200,1400));
    }
    @Test void cancelledAndDisappearedOrdersAreNotCompletions() {
        var metrics = new OrderMetrics(root,"A",1000);
        metrics.accept(1100,List.of(row(10)));
        metrics.accept(1200,List.of());
        metrics.accept(1300,List.of(row(64)));
        metrics.accept(1400,List.of(new AutomatedExecutionDriver.OwnOrderRow("minecraft:stone","minecraft:stone",64,10,64,0,true)));
        assertEquals(0,capture(metrics,1400,List.of()).completed());
    }
    @Test void impossibleDeliveryDoesNotCreateACompletion() {
        var metrics=new OrderMetrics(root,"A",1000);
        metrics.accept(1100,List.of(row(1)));metrics.accept(1200,List.of(row(65)));
        assertEquals(0,capture(metrics,1200,List.of()).completed());
    }
    @Test void identicalOrdersUseMultiplicityAndNewPlacementsHaveSeparateCompletion() {
        var metrics = new OrderMetrics(root,"A",1000);
        metrics.accept(1100,List.of(row(10),row(20)));
        metrics.accept(1200,List.of(row(64),row(20)));
        metrics.accept(1300,List.of(row(64),row(64)));
        metrics.placement("minecraft:stone",10,64);
        metrics.accept(1400,List.of(row(64),row(64),row(64)));
        assertEquals(3,capture(metrics,1400,List.of()).completed());
    }
    @Test void restartPreservesDeduplicationAndHistory() throws Exception {
        var metrics = new OrderMetrics(root,"A",1000);
        metrics.accept(1100,List.of(row(1))); metrics.accept(1200,List.of(row(64)));
        save(capture(metrics,1200,List.of(row(64))));
        var restarted = new OrderMetrics(root,"A",1300);
        restarted.accept(1400,List.of(row(64)));
        assertEquals(1,capture(restarted,1400,List.of(row(64))).completed());
        assertEquals(1000,capture(restarted,1400,List.of(row(64))).trackingSince());
    }
    @Test void tenAccountsStaySeparatedAndAllAccountsCountEachOnce() throws Exception {
        List<String> members = new ArrayList<>();
        for (int i=0;i<10;i++) {
            String name="Bot"+i; members.add(name);
            var metrics = new OrderMetrics(root,name,1000);
            var rows=List.of(new AutomatedExecutionDriver.OwnOrderRow("minecraft:stone","minecraft:stone",64,10+i,i,1000));
            metrics.accept(2000,rows); save(capture(metrics,2000,rows));
        }
        members.add("BOT1");
        var selected=OrderMetrics.select(root,"bot7",members);
        assertEquals(1,selected.size()); assertEquals(17,selected.getFirst().report().rows().getFirst().unitPrice());
        assertEquals(10,OrderMetrics.select(root,"",members).size());
        assertNull(OrderMetrics.select(root,"Missing",members).getFirst().report());
    }
    @Test void mismatchedCorruptAndStaleReportsNeverBecomeAnotherAccountsData() throws Exception {
        var metrics=new OrderMetrics(root,"A",1000); var report=capture(metrics,2000,List.of(row(1)));save(report);
        Files.copy(OrderMetrics.path(root,"A"),OrderMetrics.path(root,"B"));
        assertNull(OrderMetrics.read(root,"B"));
        assertFalse(OrderMetrics.fresh(report,400_000)); assertTrue(OrderMetrics.fresh(report,3000));
        Files.writeString(OrderMetrics.path(root,"A"),"broken");
        assertNull(OrderMetrics.read(root,"A"));
        var corrupt = new OrderMetrics(root,"A",4000);corrupt.accept(5000,List.of(row(64)));
        assertEquals("broken",Files.readString(OrderMetrics.path(root,"A")));
    }
}
