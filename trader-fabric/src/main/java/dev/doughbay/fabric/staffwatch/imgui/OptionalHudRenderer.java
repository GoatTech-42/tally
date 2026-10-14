package dev.doughbay.fabric.staffwatch.imgui;

import java.util.function.Consumer;
import java.util.function.Supplier;

/** Isolates optional native HUD failures from the client and packet detectors. */
final class OptionalHudRenderer {
    private boolean initialized;
    private boolean unavailable;

    void render(Supplier<String> glVersion, Supplier<String> shaderVersion,
                Runnable initialize, Runnable draw, Consumer<String> warning) {
        if (unavailable) return;
        try {
            if (!initialized) {
                if (blank(glVersion.get()) || blank(shaderVersion.get())) {
                    unavailable = true;
                    warning.accept("no OpenGL/GLSL version (headless graphics)");
                    return;
                }
                initialize.run();
                initialized = true;
            }
            draw.run();
        } catch (RuntimeException | LinkageError e) {
            // No retries every frame and no shutdown of the independent detectors.
            unavailable = true;
            warning.accept(e.toString());
        }
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }
}
