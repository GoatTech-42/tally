package dev.doughbay.fabric;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Local presentation preferences; changing these cannot change automation settings. */
public final class Appearance {
    public enum Theme { CLASSIC, MIDNIGHT, MINT, ROSE }
    public enum Character { ORIGINAL, MOCHI, SPROUT }
    private static Appearance current = new Appearance();
    private static Path file;
    private volatile Theme theme = Theme.CLASSIC;
    private volatile Character mascot = Character.ORIGINAL;

    private Appearance() { }
    public static Theme theme() { return current.theme; }
    public static Character mascot() { return current.mascot; }

    static void load(Path directory) {
        file = directory.resolve("appearance.json");
        try {
            if (Files.isRegularFile(file)) {
                var loaded = new Gson().fromJson(Files.readString(file), Appearance.class);
                if (loaded != null) current = loaded;
            }
        } catch (Exception ignored) { current = new Appearance(); }
        if (current.theme == null) current.theme = Theme.CLASSIC;
        if (current.mascot == null) current.mascot = Character.ORIGINAL;
        UiTheme.apply(current.theme);
    }

    static void choose(Theme theme, Character mascot) {
        current.theme = theme;
        current.mascot = mascot;
        UiTheme.apply(theme);
        if (file == null) return;
        try {
            Files.createDirectories(file.getParent());
            Path temporary = file.resolveSibling("appearance.json.tmp");
            Files.writeString(temporary, new GsonBuilder().setPrettyPrinting().create().toJson(current));
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            DoughBayClient.LOGGER.warn("GoNuts could not save appearance preferences", e);
        }
    }
}
