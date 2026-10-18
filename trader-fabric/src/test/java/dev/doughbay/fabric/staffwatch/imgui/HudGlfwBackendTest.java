package dev.doughbay.fabric.staffwatch.imgui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HudGlfwBackendTest {
    @Test void hudMouseUpdatesNeedNeitherAGameWindowNorAnImGuiContext() {
        var backend = new HudGlfwBackend();
        // The upstream implementations read ImGui IO and write GLFW cursor
        // state. A passive HUD must not touch either, even when unfocused.
        assertDoesNotThrow(backend::updateMouseData);
        assertDoesNotThrow(backend::updateMouseCursor);
    }
}
