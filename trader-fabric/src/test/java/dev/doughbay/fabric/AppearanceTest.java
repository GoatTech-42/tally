package dev.doughbay.fabric;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppearanceTest {
    @Test
    void aChoiceIsKeptAndTheTallyLookIsTheDefault(@TempDir Path directory) throws Exception {
        try {
            Appearance.load(directory);
            assertEquals(Appearance.Theme.TALLY, Appearance.theme());
            assertEquals(Appearance.Character.TALLY, Appearance.mascot());
            int classicGold = UiTheme.GOLD;

            Appearance.choose(Appearance.Theme.MINT, Appearance.Character.SPROUT);
            assertTrue(Files.isRegularFile(directory.resolve("appearance.json")));
            assertTrue(UiTheme.GOLD != classicGold);

            // A new start reads the same choice back.
            Appearance.choose(Appearance.Theme.CLASSIC, Appearance.Character.ORIGINAL);
            Files.writeString(directory.resolve("appearance.json"), "{\"theme\":\"MINT\",\"mascot\":\"SPROUT\"}");
            Appearance.load(directory);
            assertEquals(Appearance.Theme.MINT, Appearance.theme());
            assertEquals(Appearance.Character.SPROUT, Appearance.mascot());

            // A file that is not ours leaves the old look, and nothing breaks.
            Files.writeString(directory.resolve("appearance.json"), "not json at all {");
            Appearance.load(directory);
            assertEquals(Appearance.Theme.TALLY, Appearance.theme());
            assertEquals(classicGold, UiTheme.GOLD);
        } finally {
            UiTheme.apply(Appearance.Theme.CLASSIC);
        }
    }
}
