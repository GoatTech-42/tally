package dev.doughbay.fabric;
import java.nio.file.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import static org.lwjgl.opengl.GL33.*;
import org.lwjgl.system.MemoryUtil;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
public final class TutorialGuiSmoke implements ClientModInitializer {
 private int ticks,phase;private DoughBayScreen main;private TutorialScreen guide;private QuickStartScreen setup;
 public void onInitializeClient(){Tuning.set("join.auto",0);Tuning.set("update.auto",0);Tuning.set("session.auto_resume",0);ClientTickEvents.END_CLIENT_TICK.register(this::tick);}
 private void tick(Minecraft mc){try{
  if(mc.level!=null)throw new IllegalStateException("Tutorial preview must remain offline");
  if(mc.gui.overlay()!=null||++ticks<45)return;ticks=0;
  switch(phase++){
   case 0->{mc.options.guiScale().set(3);mc.resizeGui();main=new DoughBayScreen(DoughBayClient.watcher());guide=new TutorialScreen(main,false);setup=new QuickStartScreen(main);mc.gui.setScreen(setup);}
   case 1->{capture(mc,"quick-start-limits.png");var field=QuickStartScreen.class.getDeclaredField("review");field.setAccessible(true);field.set(setup,true);mc.gui.setScreen(setup);}
   case 2->{capture(mc,"quick-start-review.png");main.openTutorialTarget("auction",guide);}
   case 3->{capture(mc,"settings-auction.png");main.openTutorialTarget("orders",guide);}
   case 4->{capture(mc,"settings-orders.png");mc.gui.setScreen(new TutorialScreen(main,false));}
   case 5->{capture(mc,"tutorial-library.png");
    var field=DoughBayScreen.class.getDeclaredField("tab");field.setAccessible(true);
    @SuppressWarnings({"rawtypes","unchecked"}) Object dashboard=Enum.valueOf((Class)field.getType(),"OVERVIEW");
    field.set(main,dashboard);mc.gui.setScreen(main);}
   case 6->{capture(mc,"install-dashboard.png");main.openTutorialTarget("autopilot",guide);}
   case 7->{capture(mc,"install-trade.png");main.openTutorialTarget("evasion",guide);}
   case 8->{capture(mc,"install-protections.png");
    if(!DoughBayClient.watcher().snapshot().status().startsWith("KEYLESS:"))throw new IllegalStateException("Local keyless analysis did not publish while offline");
    Files.writeString(Path.of("tutorial-preview-success.txt"),"Fresh offline capture: limits, review, tutorial library, Dashboard, Trade and Evasion; no server joined or Start pressed");mc.stop();}
  }
 }catch(Throwable e){try{Files.writeString(Path.of("tutorial-preview-error.txt"),e.toString());}catch(Exception ignored){}mc.stop();}}
 private void capture(Minecraft mc,String name)throws Exception{
  int w=mc.getWindow().getWidth(),h=mc.getWindow().getHeight();
  int fb=glGetInteger(GL_READ_FRAMEBUFFER_BINDING),pack=glGetInteger(GL_PIXEL_PACK_BUFFER_BINDING),align=glGetInteger(GL_PACK_ALIGNMENT);
  int row=glGetInteger(GL_PACK_ROW_LENGTH),sx=glGetInteger(GL_PACK_SKIP_PIXELS),sy=glGetInteger(GL_PACK_SKIP_ROWS);
  var bytes=MemoryUtil.memAlloc(w*h*4);
  try{
   glBindFramebuffer(GL_READ_FRAMEBUFFER,0);glReadBuffer(GL_BACK);glBindBuffer(GL_PIXEL_PACK_BUFFER,0);
   glPixelStorei(GL_PACK_ALIGNMENT,1);glPixelStorei(GL_PACK_ROW_LENGTH,0);glPixelStorei(GL_PACK_SKIP_PIXELS,0);glPixelStorei(GL_PACK_SKIP_ROWS,0);
   glReadPixels(0,0,w,h,GL_RGBA,GL_UNSIGNED_BYTE,bytes);var image=new BufferedImage(w,h,BufferedImage.TYPE_INT_RGB);
   for(int y=0;y<h;y++)for(int x=0;x<w;x++){int p=(y*w+x)*4;image.setRGB(x,h-1-y,((bytes.get(p)&255)<<16)|((bytes.get(p+1)&255)<<8)|(bytes.get(p+2)&255));}
   ImageIO.write(image,"png",Path.of(name).toFile());
  }finally{MemoryUtil.memFree(bytes);glBindFramebuffer(GL_READ_FRAMEBUFFER,fb);glBindBuffer(GL_PIXEL_PACK_BUFFER,pack);glPixelStorei(GL_PACK_ALIGNMENT,align);glPixelStorei(GL_PACK_ROW_LENGTH,row);glPixelStorei(GL_PACK_SKIP_PIXELS,sx);glPixelStorei(GL_PACK_SKIP_ROWS,sy);}
 }
}
