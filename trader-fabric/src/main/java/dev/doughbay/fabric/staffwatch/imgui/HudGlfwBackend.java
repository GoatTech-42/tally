package dev.doughbay.fabric.staffwatch.imgui;

import imgui.glfw.ImGuiImplGlfw;

/** Draw-only overlay: never change Minecraft's cursor mode or the desktop pointer. */
final class HudGlfwBackend extends ImGuiImplGlfw {
    @Override public void updateMouseData() { }
    @Override public void updateMouseCursor() { }
}
