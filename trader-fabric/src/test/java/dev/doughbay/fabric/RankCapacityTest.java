package dev.doughbay.fabric;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static dev.doughbay.fabric.AutomatedExecutionDriver.*;

class RankCapacityTest {
    private static final OwnListingCell VACANT = new OwnListingCell("minecraft:gray_stained_glass_pane",1,List.of("List","Click to sell an item"));
    private static final OwnListingCell LOCKED = new OwnListingCell("minecraft:red_stained_glass_pane",1,List.of("Locked"));
    private List<OwnListingCell> page(OwnListingCell cell) { return new ArrayList<>(Collections.nCopies(45,cell)); }
    @Test void firstPageIsOnlyALowerBoundAndTheSecondDistinguishesFortyFiveFromNinety() {
        assertEquals(new RankCapacity.PageCapacity(45,false),RankCapacity.auctionPage(page(VACANT),0));
        assertEquals(new RankCapacity.PageCapacity(90,true),RankCapacity.auctionPage(page(VACANT),1));
        assertEquals(new RankCapacity.PageCapacity(45,true),RankCapacity.auctionPage(page(LOCKED),1));
        assertEquals(0,RankCapacity.auctionPage(page(LOCKED),0).slots());
    }
    @Test void unloadedAndUnknownCellsCannotBecomeRankEvidence() {
        var first = page(LOCKED); for(int i=0;i<18;i++)first.set(i,VACANT);
        assertEquals(new RankCapacity.PageCapacity(18,true),RankCapacity.auctionPage(first,0));
        first.set(40,new OwnListingCell("minecraft:air",0,List.of()));
        assertEquals(0,RankCapacity.auctionPage(first,0).slots());
        first.set(40,new OwnListingCell("minecraft:barrier",1,List.of("Unknown menu cell")));
        assertEquals(0,RankCapacity.auctionPage(first,0).slots());
    }
    @Test void explicitOrderAndAuctionLabelsUseTheTotalNeverUsedCount() {
        assertEquals(90,RankCapacity.explicitTotal(List.of("Orders: 7 / 90")));
        assertEquals(45,RankCapacity.explicitTotal(List.of("Auction slots: 0/45")));
        assertEquals(18,RankCapacity.explicitTotal(List.of("Slots: 18/18")));
        assertEquals(0,RankCapacity.explicitTotal(List.of("Orders: 91/90")));
        assertEquals(0,RankCapacity.explicitTotal(List.of("Orders: 7/90","Slots: 1/45")));
        assertEquals(0,RankCapacity.explicitTotal(List.of("Remaining: 7/90","Your Orders (1/2)","Price: 18")));
    }
}
