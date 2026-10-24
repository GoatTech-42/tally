package dev.doughbay.fabric;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class AuctionOccupancyTest {
    @TempDir Path root;
    @Test void countsNativeOccupancyRatherThanLedgerRecordsAndOrderBookFreshness() throws Exception {
        AuctionOccupancy.publish(root,"A",1_000_000,90,40,990_000);
        AuctionOccupancy.publish(root,"B",1_000_000,90,30,990_000);
        AuctionOccupancy.publish(root,"C",1_000_000,90,20,990_000);
        var view=AuctionOccupancy.view(root,List.of("A","B","C"),1_000_001);
        assertEquals(270,view.capacity());assertEquals(90,view.used());assertEquals(3,view.fresh());
        assertEquals(90,AuctionOccupancy.view(root,List.of("B"),1_000_001).capacity());
        assertEquals(30,AuctionOccupancy.view(root,List.of("B"),1_000_001).used());
    }
    @Test void missingStaleAndFutureCountsStayUnknownInsteadOfAppearingFree() throws Exception {
        AuctionOccupancy.publish(root,"A",1_000_000,90,0,990_000);
        assertEquals(-1,AuctionOccupancy.view(root,List.of("A","B"),1_000_001).used());
        assertEquals(-1,AuctionOccupancy.view(root,List.of("A"),1_090_001).used());
        assertEquals(-1,AuctionOccupancy.view(root,List.of("A"),999_999).used());
        AuctionOccupancy.publish(root,"A",1_000_000,90,-1,0);
        var unknown=AuctionOccupancy.view(root,List.of("A"),1_000_001);
        assertEquals(90,unknown.capacity());assertEquals(-1,unknown.used());assertEquals(0,unknown.fresh());
    }
}
