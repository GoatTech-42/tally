package dev.doughbay.fabric;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class StaffWatchHistoryLocationTest {
    @TempDir Path root;

    @Test void joinedProfileSettingsDoNotSplitTheHiveObservationHistory() throws Exception {
        var active = HiveSetup.class.getDeclaredField("active"); active.setAccessible(true);
        Object before = active.get(null);
        try {
            Path owner = instance("owner"), third = instance("third");
            Path shared = owner.resolve("config/doughbay");
            var membership = HiveSetup.create(owner, shared, "FixtureOwner");
            HiveSetup.joinFresh(third, third.resolve("config/doughbay"), HiveSetup.code(membership), "FixtureThird");
            var resolve = HiveSetup.class.getDeclaredMethod("resolve", Path.class, Path.class, String.class, boolean.class);
            resolve.setAccessible(true);
            Path configuration = (Path) resolve.invoke(null, third, third.resolve("config/doughbay"), "FixtureThird", false);
            assertNotEquals(shared, configuration, "private account settings remain separate");
            assertEquals(shared, StaffWatchReport.historyDirectory(configuration),
                    "all hive observers must write the same activity database");
        } finally { active.set(null, before); }
    }

    @Test void soloHistoryKeepsItsExistingDirectory() throws Exception {
        var active = HiveSetup.class.getDeclaredField("active"); active.setAccessible(true);
        Object before = active.get(null);
        try {
            active.set(null, null);
            assertEquals(root, StaffWatchReport.historyDirectory(root));
        } finally { active.set(null, before); }
    }

    private Path instance(String name) throws Exception {
        Path game = Files.createDirectories(root.resolve(name));
        Path config = Files.createDirectories(game.resolve("config/doughbay"));
        Files.writeString(config.resolve("config.json"), "{}");
        try (var c = DriverManager.getConnection("jdbc:sqlite:" + config.resolve("doughbay.db"));
             var statement = c.createStatement()) {
            statement.execute("CREATE TABLE positions(id INTEGER PRIMARY KEY, client TEXT)");
        }
        return game;
    }
}
