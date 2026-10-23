package dev.doughbay.fabric;
import java.nio.file.*;
import java.util.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import static org.lwjgl.opengl.GL33.*;
import org.lwjgl.system.MemoryUtil;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;

/** Real release UI, disposable offline account, no world, no Start or live configuration. */
public final class DocumentationGuiSmoke implements ClientModInitializer {
 private int phase,ticks;private long began=System.currentTimeMillis();private DoughBayScreen main;private StartHereScreen help;private String baseline;
 public void onInitializeClient(){Tuning.set("join.auto",0);Tuning.set("update.auto",0);Tuning.set("update.check",0);Tuning.set("session.auto_resume",0);ClientTickEvents.END_CLIENT_TICK.register(this::tick);}
 private void press(net.minecraft.client.gui.screens.Screen screen,String label)throws Exception{
  var button=net.fabricmc.fabric.api.client.screen.v1.Screens.getWidgets(screen).stream().filter(b->b.getMessage().getString().equals(label)).findFirst().orElseThrow(()->new IllegalStateException("Documented button missing: "+label));
  var w=Minecraft.getInstance().getWindow();double rawX=(button.getX()+button.getWidth()/2d)*w.getScreenWidth()/w.getGuiScaledWidth(),rawY=(button.getY()+button.getHeight()/2d)*w.getScreenHeight()/w.getGuiScaledHeight();
  var event=new net.minecraft.client.input.MouseButtonEvent(net.minecraft.client.MouseHandler.getScaledXPos(w,rawX),net.minecraft.client.MouseHandler.getScaledYPos(w,rawY),new net.minecraft.client.input.MouseButtonInfo(0,0));
  if(!screen.mouseClicked(event,false))throw new AssertionError("Documented control did not accept its native click");screen.mouseReleased(event);
 }
 private void field(Object target,String name,String value)throws Exception{var f=target.getClass().getDeclaredField(name);f.setAccessible(true);@SuppressWarnings({"rawtypes","unchecked"}) Object e=Enum.valueOf((Class)f.getType(),value);f.set(target,e);}
 private void page(Minecraft mc,String tab)throws Exception{field(main,"tab",tab);mc.gui.setScreen(main);}
 private void tick(Minecraft mc){try{
  if(mc.level!=null)throw new AssertionError("Documentation capture joined a world");
  if(System.currentTimeMillis()-began>900000)throw new AssertionError("Documentation capture deadline exceeded");
  if(mc.gui.overlay()!=null||++ticks<35)return;ticks=0;
  switch(phase++){
   case 0->{mc.options.pauseOnLostFocus=false;mc.options.guiScale().set(2);mc.resizeGui();main=new DoughBayScreen(DoughBayClient.watcher());baseline=SettingsCode.export();mc.gui.setScreen(new SimpleSettingsScreen(main));}
   case 1->{press(mc.gui.screen(),"Help & setup");if(!(mc.gui.screen() instanceof StartHereScreen))throw new AssertionError("Help entry unavailable");help=(StartHereScreen)mc.gui.screen();}
   case 2->{capture(mc,"start-here.png");press(help,"1  SET UP TRADING - START HERE");}
   case 3->{capture(mc,"quick-start-limits.png");press(mc.gui.screen(),"Review & continue");}
   case 4->{capture(mc,"quick-start-review.png");if(DoughBayClient.donutAutomationEnabled())throw new AssertionError("Tutorial armed trading");mc.gui.screen().onClose();press(help,"2  Open Trade - status, Start, Stop and recovery");}
   case 5->{capture(mc,"install-trade.png");page(mc,"OVERVIEW");}
   case 6->{capture(mc,"install-dashboard.png");mc.gui.setScreen(new SimpleSettingsScreen(main));}
   case 7->{capture(mc,"settings-simple.png");press(mc.gui.screen(),"Advanced view");}
   case 8->{capture(mc,"settings-advanced.png");main.openAdvancedSettings(SettingsSection.ORDERS);}
   case 9->{capture(mc,"settings-orders.png");main.openAdvancedSettings(SettingsSection.ITEMS);}
   case 10->{capture(mc,"settings-items.png");page(mc,"EVASION");}
   case 11->{capture(mc,"install-protections.png");page(mc,"SWARM");}
   case 12->{capture(mc,"swarm.png");page(mc,"HIVE");}
   case 13->{capture(mc,"hive-setup.png");field(main,"automationMode","DISCORD");page(mc,"AUTOMATION");}
   case 14->{capture(mc,"discord-setup.png");field(main,"automationMode","WEB");page(mc,"AUTOMATION");}
   case 15->{capture(mc,"web-panel.png");mc.gui.setScreen(new TutorialScreen(main,false));}
   case 16->{capture(mc,"tutorial-library.png");org.lwjgl.glfw.GLFW.glfwSetWindowSize(mc.getWindow().handle(),960,720);mc.options.guiScale().set(4);mc.resizeGui();mc.gui.setScreen(new SimpleSettingsScreen(main));}
   case 17->{capture(mc,"settings-compact.png");for(var b:net.fabricmc.fabric.api.client.screen.v1.Screens.getWidgets(mc.gui.screen()))if(b.getY()<0||b.getY()+b.getHeight()>mc.gui.screen().height)throw new AssertionError("Documentation control clipped");if(!baseline.equals(SettingsCode.export()))throw new AssertionError("Documentation navigation changed settings");Files.writeString(Path.of("documentation-preview-success.txt"),"Actual release UI routes clicked; no world joined, trading armed, or saved settings changed. Desktop and compact captures complete.");mc.stop();}
  }
 }catch(Throwable failure){try{Files.writeString(Path.of("documentation-preview-error.txt"),failure.toString());}catch(Exception ignored){}mc.stop();}}
 private void capture(Minecraft mc,String name)throws Exception{
  int w=mc.getWindow().getWidth(),h=mc.getWindow().getHeight(),fb=glGetInteger(GL_READ_FRAMEBUFFER_BINDING),pack=glGetInteger(GL_PIXEL_PACK_BUFFER_BINDING),align=glGetInteger(GL_PACK_ALIGNMENT),row=glGetInteger(GL_PACK_ROW_LENGTH),sx=glGetInteger(GL_PACK_SKIP_PIXELS),sy=glGetInteger(GL_PACK_SKIP_ROWS);var bytes=MemoryUtil.memAlloc(w*h*4);
  try{glBindFramebuffer(GL_READ_FRAMEBUFFER,0);glReadBuffer(GL_BACK);glBindBuffer(GL_PIXEL_PACK_BUFFER,0);glPixelStorei(GL_PACK_ALIGNMENT,1);glPixelStorei(GL_PACK_ROW_LENGTH,0);glPixelStorei(GL_PACK_SKIP_PIXELS,0);glPixelStorei(GL_PACK_SKIP_ROWS,0);glReadPixels(0,0,w,h,GL_RGBA,GL_UNSIGNED_BYTE,bytes);var image=new BufferedImage(w,h,BufferedImage.TYPE_INT_RGB);for(int y=0;y<h;y++)for(int x=0;x<w;x++){int p=(y*w+x)*4;image.setRGB(x,h-1-y,((bytes.get(p)&255)<<16)|((bytes.get(p+1)&255)<<8)|(bytes.get(p+2)&255));}ImageIO.write(image,"png",Path.of(name).toFile());}
  finally{MemoryUtil.memFree(bytes);glBindFramebuffer(GL_READ_FRAMEBUFFER,fb);glBindBuffer(GL_PIXEL_PACK_BUFFER,pack);glPixelStorei(GL_PACK_ALIGNMENT,align);glPixelStorei(GL_PACK_ROW_LENGTH,row);glPixelStorei(GL_PACK_SKIP_PIXELS,sx);glPixelStorei(GL_PACK_SKIP_ROWS,sy);}
 }
}
