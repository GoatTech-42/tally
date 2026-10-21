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
        assertEquals(new RankCapacity.PageCapacity(0,false),RankCapacity.auctionPage(page(LOCKED),1));
        assertEquals(0,RankCapacity.auctionPage(page(LOCKED),0).slots());
    }
    @Test void aLockedSecondPageCannotDistinguishEighteenFromFortyFiveSlots() {
        assertEquals(0, RankCapacity.auctionPage(page(LOCKED), 1).slots(),
                "Both an 18-slot and a 45-slot account can have a locked second page");
    }
    @Test void navigationTotalCannotOverrideContradictoryPhysicalCapacity() {
        var small = page(LOCKED); for (int i = 0; i < 18; i++) small.set(i, VACANT);
        assertEquals(0, RankCapacity.auctionEvidence(small, 0, List.of("Slots: 18/90"), false).slots());
        assertEquals(new RankCapacity.PageCapacity(18, true),
                RankCapacity.auctionEvidence(small, 0, List.of("Slots: 18/18"), false));
        assertEquals(0, RankCapacity.auctionEvidence(page(VACANT), 0, List.of("Slots: 0/18"), false).slots());
        assertEquals(0, RankCapacity.auctionEvidence(page(VACANT), 1, List.of("Slots: 0/45"), false).slots());
        assertEquals(0, RankCapacity.auctionEvidence(page(LOCKED), 1, List.of("Slots: 18/90"), false).slots());
    }
    @Test void pendingOrUnloadedPagesCannotTeachCapacityEvenWithAnExplicitLabel() {
        assertEquals(0, RankCapacity.auctionEvidence(page(VACANT), 1, List.of("Slots: 0/90"), true).slots());
        assertEquals(0, RankCapacity.auctionEvidence(page(new OwnListingCell("minecraft:air",0,List.of())),
                0, List.of("Slots: 0/90"), false).slots());
        assertEquals(0, RankCapacity.auctionEvidence(List.of(VACANT), 0, List.of("Slots: 0/90"), false).slots());
    }
    @Test void compatibleExplicitEvidenceStillDistinguishesRealRankCapacities() {
        assertEquals(new RankCapacity.PageCapacity(45,true),
                RankCapacity.auctionEvidence(page(VACANT),0,List.of("Slots: 0/45"),false));
        assertEquals(new RankCapacity.PageCapacity(90,true),
                RankCapacity.auctionEvidence(page(VACANT),0,List.of("Slots: 0/90"),false));
        for (int total : List.of(18,45)) assertEquals(new RankCapacity.PageCapacity(total,true),
                RankCapacity.auctionEvidence(page(LOCKED),1,List.of("Slots: 0/"+total),false));
        assertEquals(new RankCapacity.PageCapacity(90,true),
                RankCapacity.auctionEvidence(page(VACANT),1,List.of(),false));
    }
    @Test void lockedSecondPageUsesOnlyTheFirstPageOfTheSameScan() {
        var locked = page(LOCKED);
        for (int total : List.of(18,45)) {
            var first = new RankCapacity.PageCapacity(total, total == 18);
            assertEquals(new RankCapacity.PageCapacity(total,true),
                    RankCapacity.auctionEvidence(locked,1,List.of(),false,first));
            assertEquals(0,RankCapacity.auctionEvidence(locked,1,List.of("Slots: 0/90"),false,first).slots());
            assertEquals(0,RankCapacity.auctionEvidence(locked,1,List.of(),true,first).slots());
        }
        assertEquals(0,RankCapacity.auctionEvidence(locked,1,List.of(),false).slots());
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
