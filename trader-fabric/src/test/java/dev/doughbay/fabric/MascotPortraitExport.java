package dev.doughbay.fabric;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import javax.imageio.ImageIO;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Renders still portraits of every native character from the production draw
 * code, for README/web use and visual review. Off in normal builds:
 * <pre>GONUTS_RENDER_PORTRAIT=build/portraits ./gradlew :trader-fabric:test --tests "*MascotPortraitExport*" --rerun</pre>
 */
@EnabledIfEnvironmentVariable(named = "GONUTS_RENDER_PORTRAIT", matches = ".+")
class MascotPortraitExport {
    @Test
    void renderEveryCharacter() throws Exception {
        Path out = Path.of(System.getenv("GONUTS_RENDER_PORTRAIT"));
        Files.createDirectories(out);
        for (Appearance.Character character : Appearance.Character.values()) {
            for (String state : new String[]{"idle", "sale", "sleep"}) {
                var image = MascotPortrait.render(character, state);
                Path file = out.resolve("mascot_" + character.name().toLowerCase() + "_" + state + ".png");
                ImageIO.write(image, "png", file.toFile());
                assertTrue(Files.size(file) > 500, file + " should not be empty");
            }
        }
    }
}
