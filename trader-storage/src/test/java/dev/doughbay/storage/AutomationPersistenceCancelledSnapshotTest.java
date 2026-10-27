package dev.doughbay.storage;

import dev.doughbay.core.model.Position;
import dev.doughbay.core.model.PositionStatus;
import dev.doughbay.core.model.StackBucket;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A failed cancelled-position read is not an empty answer. The slot audit
 * reopens still-listed stacks from this snapshot, so a transient query
 * failure that erases the last good snapshot fabricates "nothing was
 * cancelled" and leaves lost stock unmanaged. The last snapshot must survive
 * a failed refresh.
 */
class AutomationPersistenceCancelledSnapshotTest {

    @TempDir
    Path temp;

    @Test
    void aFailedCancelledQueryKeepsTheLastSnapshotInsteadOfFabricatingEmpty() throws Exception {
        Path file = temp.resolve("cancelled.db");
        long now = System.currentTimeMillis();
        try (Database database = new Database(file)) {
            new PositionRepository(database).upsert(new Position(7, "REAL", "minecraft:cobweb",
                    StackBucket.of(16), 16, 4_000, 9_000, now - 10_000, 0, now - 1_000, 0,
                    Double.NaN, PositionStatus.CANCELLED));
        }

        AutomationPersistenceWorker worker = new AutomationPersistenceWorker(file);
        worker.start();
        try {
            await(() -> worker.status().ready());
            worker.requestCancelledSnapshot(now - 3 * 24 * 3600_000L);
            await(() -> worker.cancelledSnapshot().stream().anyMatch(p -> p.positionId() == 7));

            // Break the read path underneath the worker: the table is gone.
            try (Database saboteur = new Database(file);
                 var statement = saboteur.connection().createStatement()) {
                statement.execute("DROP TABLE positions");
            }

            worker.requestCancelledSnapshot(now - 3 * 24 * 3600_000L);
            // The sabotaged read sits ahead of this write in the queue; the
            // write failing on the missing table proves the read ran first.
            long heartbeat = worker.submit(checkpoint());
            await(() -> worker.status().refused(heartbeat)
                    || worker.status().loadState() == AutomationPersistencePort.LoadState.FAILED);

            assertEquals(1, worker.cancelledSnapshot().size(),
                    "a failed refresh must not erase the last good cancelled snapshot");
            assertEquals(7, worker.cancelledSnapshot().getFirst().positionId());
        } finally {
            worker.close();
        }
    }

    private static AutomationSessionCheckpoint checkpoint() {
        Position position = new Position(42, "REAL", "minecraft:obsidian",
                StackBucket.X64, 64, 6500, 9000, 10_000, 0, 0, 0,
                Double.NaN, PositionStatus.PURCHASED);
        return new AutomationSessionCheckpoint(true, "SINGLE", "PREPARING_LIST",
                1, 6500, 9_000, 10_100, "verified purchase", "",
                position, AutomationUncertainExposure.none());
    }

    private static void await(java.util.function.BooleanSupplier check) throws Exception {
        long deadline = System.nanoTime() + 5_000_000_000L;
        while (!check.getAsBoolean() && System.nanoTime() < deadline) Thread.sleep(10);
        org.junit.jupiter.api.Assertions.assertTrue(check.getAsBoolean(),
                "condition did not become true before the deadline");
    }
}
