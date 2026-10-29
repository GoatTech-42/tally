package dev.doughbay.fabric.staffwatch.imgui;

import com.mojang.blaze3d.opengl.GlStateManager;
import dev.doughbay.fabric.staffwatch.gui.HudRenderer;
import dev.doughbay.fabric.staffwatch.module.impl.misc.AntiVanishModule;
import dev.doughbay.fabric.staffwatch.module.impl.misc.StaffListModule;
import imgui.ImFont;
import imgui.ImGui;
import imgui.gl3.ImGuiImplGl3;
import imgui.glfw.ImGuiImplGlfw;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.slf4j.LoggerFactory;

public final class ImGuiManager {
    private static final ImGuiManager INSTANCE = new ImGuiManager();
    private final OptionalHudRenderer renderer = new OptionalHudRenderer();
    private ImGuiImplGlfw glfw;
    private ImGuiImplGl3 gl3;
    private ImFont text, label;

    public static ImGuiManager getInstance() { return INSTANCE; }
    public ImFont hudValueFont() { return text; }
    public ImFont hudTextFont() { return text; }
    public ImFont hudLabelFont() { return label; }

    private void init() {
        // HeadlessMC must never enter native ImGui setup: its GL calls are stubs.
        // This is a draw-only HUD. Minecraft owns the desktop cursor even
        // while menus open/close in an unfocused background window.
        glfw = new HudGlfwBackend();
        gl3 = new ImGuiImplGl3();
        ImGui.createContext();
        ImGui.getIO().setIniFilename(null);
        try {
            var atlas = ImGui.getIO().getFonts();
            try (var in = getClass().getResourceAsStream("/assets/gonuts_staffwatch/fonts/geist-semibold.ttf")) {
                text = atlas.addFontFromMemoryTTF(in.readAllBytes(), 34);
            }
            try (var in = getClass().getResourceAsStream("/assets/gonuts_staffwatch/fonts/geist-medium.ttf")) {
                label = atlas.addFontFromMemoryTTF(in.readAllBytes(), 28);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Cannot load staff HUD fonts", e);
        }
        glfw.init(Minecraft.getInstance().getWindow().handle(), false);
        gl3.init(null);
    }

    public void renderIfActive() {
        var mc = Minecraft.getInstance();
        if (mc.level == null || (!StaffListModule.shouldShowHud() && !AntiVanishModule.shouldShowHud())) {
            HudRenderer.getInstance().noteStackHidden();
            return;
        }
        renderer.render(() -> GL11.glGetString(GL11.GL_VERSION),
                () -> GL11.glGetString(GL20.GL_SHADING_LANGUAGE_VERSION), this::init, this::draw,
                reason -> LoggerFactory.getLogger(ImGuiManager.class).warn(
                        "Staff Watch visual HUD unavailable: {}. Detection, reconnects and panel alerts remain active.", reason));
    }

    private void draw() {
        var window = Minecraft.getInstance().getWindow();
        int w = window.getWidth(), h = window.getHeight();
        int old = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        try {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
            GlStateManager._viewport(0, 0, w, h);
            glfw.newFrame();
            ImGui.getIO().setDisplaySize(w, h);
            ImGui.getIO().setDisplayFramebufferScale(1, 1);
            gl3.newFrame();
            ImGui.newFrame();
            HudRenderer.getInstance().render();
            ImGui.render();
            gl3.renderDrawData(ImGui.getDrawData());
        } finally {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, old);
        }
    }
}
