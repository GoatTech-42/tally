package dev.doughbay.fabric.discord;

import java.nio.file.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeAuctionOccupancyImageTest {
    @Test void nativeAndTrackedCountsAreDistinctAndUnknownGridStaysUnknown() throws Exception {
        var head=PanelReadModel.moneyHeadline(List.of(new String[]{"Listed","90"},
                new String[]{"Tracked listings","3"},new String[]{"Auction data","3/3 accounts have current native auction counts"},
                new String[]{"Auction capacity","270 slots"}),270,false);
        var page=new StatsImage.Page("Money","Synthetic native occupancy fixture","green",head,90,270,
                new String[]{"Item"},new boolean[]{false},List.of(),"Native counts and tracked records are different sources.");
        var report=PanelReadModel.report(page);
        assertEquals(90,report.path("slotsUsed").asInt());
        assertEquals("3",head.stream().filter(r->r[0].equals("Tracked listings")).findFirst().orElseThrow()[1]);
        var unknown=new StatsImage.Page("Money","Synthetic partial coverage fixture","gold",head,-1,270,
                new String[]{"Item"},new boolean[]{false},List.of(),"Auction occupancy unavailable; do not interpret as free slots.");
        assertEquals(-1,PanelReadModel.report(unknown).path("slotsUsed").asInt());
        Path out=Path.of("build/reports/native-occupancy");Files.createDirectories(out);
        Files.write(out.resolve("known.png"),StatsImage.render(page));
        Files.write(out.resolve("unknown.png"),StatsImage.render(unknown));
    }
}
