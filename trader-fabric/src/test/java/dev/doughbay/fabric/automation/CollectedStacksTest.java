package dev.doughbay.fabric.automation;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CollectedStacksTest {
    @Test void twoCollectedStacksAreBothBookedWhileFourteenStayOnTheOrder() {
        var parts = CollectedStacks.split(128, 64, 12800, 100);
        assertEquals(java.util.List.of(new CollectedStacks.Part(64,6400),
                new CollectedStacks.Part(64,6400)), parts);
    }
    @Test void completedOrderIncludesItsPartialStackAndEveryCoinOfCost() {
        var parts = CollectedStacks.split(142, 64, 14203, 100);
        assertEquals(java.util.List.of(64,64,14), parts.stream().map(CollectedStacks.Part::count).toList());
        assertEquals(14203, parts.stream().mapToLong(CollectedStacks.Part::cost).sum());
        assertTrue(parts.stream().allMatch(p -> p.cost() >= p.count()*100));
    }
    @Test void respectsSixteenAndUnstackableItems() {
        assertEquals(java.util.List.of(16,16,3), CollectedStacks.split(35,16,350,10)
                .stream().map(CollectedStacks.Part::count).toList());
        assertEquals(3, CollectedStacks.split(3,1,300,100).size());
    }
    @Test void preservesQuantityAndCostAcrossBatchSizes() {
        for (int maximum : new int[]{1,16,64}) for(int count=1;count<=256;count++) {
            long cost=count*101L+17;
            var parts=CollectedStacks.split(count,maximum,cost,100);
            assertEquals(count,parts.stream().mapToInt(CollectedStacks.Part::count).sum());
            assertEquals(cost,parts.stream().mapToLong(CollectedStacks.Part::cost).sum());
            assertTrue(parts.stream().allMatch(p->p.count()>0 && p.count()<=maximum && p.cost()>=p.count()*100));
        }
    }
}
