package dev.doughbay.fabric.automation;

import dev.doughbay.core.model.*;
import dev.doughbay.fabric.AutomatedExecutionDriver;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class QueuedInventoryOwnershipTest {
    private static Position purchase(long id, String item, int count, PositionStatus status) {
        return new Position(id, "REAL", item, StackBucket.of(count), count,
                count * 10_000L, count * 20_000L, 10_000, 0, 0, 0, Double.NaN, status);
    }

    @SuppressWarnings("unchecked")
    private static List<Position> queue(AutomationSessionController controller) throws Exception {
        var field = AutomationSessionController.class.getDeclaredField("recoveredQueue");
        field.setAccessible(true);
        return (List<Position>) field.get(controller);
    }

    @Test void sweepMustNotBookAnAlreadyQueuedBatchAgain() throws Exception {
        var controller = new AutomationSessionController(new AutomatedExecutionDriver());
        var first = purchase(1, "minecraft:obsidian", 64, PositionStatus.PURCHASED);
        var second = purchase(2, "minecraft:obsidian", 64, PositionStatus.PURCHASED);
        queue(controller).addAll(List.of(first, second));
        assertTrue(controller.inventoryStockAlreadyOwned("minecraft:obsidian"),
                "the inventory sweep must defer to the existing purchase records");
        assertFalse(controller.inventoryStockAlreadyOwned("minecraft:emerald"));
        assertEquals(List.of(first, second), queue(controller),
                "checking ownership must preserve the purchase IDs, quantities and costs");
    }

    @Test void partialQueuedStockStillOwnsItsInventory() throws Exception {
        var controller = new AutomationSessionController(new AutomatedExecutionDriver());
        queue(controller).add(purchase(3, "minecraft:diamond_block", 7, PositionStatus.PURCHASED));
        assertTrue(controller.inventoryStockAlreadyOwned("minecraft:diamond_block"));
    }

    @Test void descriptorStockCannotBeAdoptedAgainUnderItsPlainItemKey() throws Exception {
        var controller = new AutomationSessionController(new AutomatedExecutionDriver());
        queue(controller).add(purchase(5, "minecraft:diamond_sword#descriptor", 1, PositionStatus.PURCHASED));
        assertTrue(controller.inventoryStockAlreadyOwned("minecraft:diamond_sword"));
    }

    @Test void listedAndClosedRecordsDoNotClaimUnrelatedInventory() throws Exception {
        var controller = new AutomationSessionController(new AutomatedExecutionDriver());
        for (var status : List.of(PositionStatus.LISTED, PositionStatus.SOLD, PositionStatus.CANCELLED)) {
            queue(controller).clear();
            queue(controller).add(purchase(4, "minecraft:obsidian", 64, status));
            assertFalse(controller.inventoryStockAlreadyOwned("minecraft:obsidian"));
        }
    }
}
