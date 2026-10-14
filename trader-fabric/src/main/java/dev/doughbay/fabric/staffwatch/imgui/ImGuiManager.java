package dev.doughbay.fabric.staffwatch.imgui;
import imgui.*;import imgui.gl3.ImGuiImplGl3;import imgui.glfw.ImGuiImplGlfw;
import net.minecraft.client.Minecraft;import dev.doughbay.fabric.staffwatch.gui.*;import dev.doughbay.fabric.staffwatch.module.impl.misc.*;
import org.lwjgl.opengl.*;import com.mojang.blaze3d.opengl.GlStateManager;
public final class ImGuiManager {
 private static final ImGuiManager INSTANCE=new ImGuiManager(); public static ImGuiManager getInstance(){return INSTANCE;}
 private final ImGuiImplGlfw glfw=new ImGuiImplGlfw();private final ImGuiImplGl3 gl3=new ImGuiImplGl3();private boolean initialized;
 private ImFont text,label;
 public ImFont hudValueFont(){return text;}public ImFont hudTextFont(){return text;}public ImFont hudLabelFont(){return label;}
 private void init(){if(initialized)return;ImGui.createContext();ImGui.getIO().setIniFilename(null);
  try{var atlas=ImGui.getIO().getFonts();try(var in=getClass().getResourceAsStream("/assets/gonuts_staffwatch/fonts/geist-semibold.ttf")){text=atlas.addFontFromMemoryTTF(in.readAllBytes(),34);}
  try(var in=getClass().getResourceAsStream("/assets/gonuts_staffwatch/fonts/geist-medium.ttf")){label=atlas.addFontFromMemoryTTF(in.readAllBytes(),28);}}
  catch(Exception e){throw new IllegalStateException("Cannot load staff HUD fonts",e);}
  glfw.init(Minecraft.getInstance().getWindow().handle(),false);gl3.init(null);initialized=true;
 }
 public void renderIfActive(){var mc=Minecraft.getInstance();if(mc.level==null||(!StaffListModule.shouldShowHud()&&!AntiVanishModule.shouldShowHud()))return;
  init();int w=mc.getWindow().getWidth(),h=mc.getWindow().getHeight();int old=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
  GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,0);GlStateManager._viewport(0,0,w,h);
  glfw.newFrame();ImGui.getIO().setDisplaySize(w,h);ImGui.getIO().setDisplayFramebufferScale(1,1);gl3.newFrame();ImGui.newFrame();
  HudRenderer.getInstance().render();ImGui.render();gl3.renderDrawData(ImGui.getDrawData());GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,old);
 }
}
