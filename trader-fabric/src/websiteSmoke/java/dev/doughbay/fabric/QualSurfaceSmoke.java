package dev.doughbay.fabric;
import java.nio.file.*;import java.awt.image.BufferedImage;import javax.imageio.ImageIO;import static org.lwjgl.opengl.GL33.*;import org.lwjgl.system.MemoryUtil;
import net.fabricmc.api.ClientModInitializer;import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;import net.minecraft.client.Minecraft;import net.minecraft.client.gui.screens.Screen;import net.minecraft.client.gui.components.Button;
/** Offline qualification of the real first-open surfaces; never joins a server or ships in releases. */
public final class QualSurfaceSmoke implements ClientModInitializer {
 private int ticks,phase,checks,chain;private DoughBayScreen main;private final StringBuilder trail=new StringBuilder();
 public void onInitializeClient(){Tuning.set("join.auto",0);Tuning.set("update.auto",0);Tuning.set("session.auto_resume",0);ClientTickEvents.END_CLIENT_TICK.register(this::tick);}
 private void require(boolean ok,String message){checks++;if(!ok)throw new IllegalStateException(message);}
 private static String name(Screen s){return s==null?"null":s.getClass().getSimpleName();}
 private static Button find(Screen s,String label){for(var c:s.children())if(c instanceof Button b&&b.getMessage().getString().equals(label))return b;return null;}
 private void tick(Minecraft mc){try{
  require(mc.level==null,"qualification joined a server");if(mc.gui.overlay()!=null||++ticks<35)return;ticks=0;
  switch(phase++){
   case 0->{mc.options.pauseOnLostFocus=false;mc.options.guiScale().set(2);mc.resizeGui();
    // The same private entry the open key uses, so the real notice chain composes.
    var m=DoughBayClient.class.getDeclaredMethod("openPanel",Minecraft.class);m.setAccessible(true);m.invoke(null,mc);}
   case 1->{Screen cur=mc.gui.screen();trail.append(name(cur)).append(';');
    require(cur instanceof PublicSupportNoticeScreen,"first-open screen is not the retired-support notice: "+name(cur));
    require(find(cur,"Tally source code")!=null,"notice lost the source link");
    require(find(cur,"Continue to local Tally")!=null,"notice lost the continue path");
    capture(mc,"qual-1-first-open.png");cur.onClose();}
   case 2->{Screen cur=mc.gui.screen();trail.append(name(cur)).append(';');
    if(cur instanceof DoughBayScreen d){main=d;capture(mc,"qual-2-main-panel.png");}
    else{require(++chain<5,"notice chain never reached the main panel: "+trail);capture(mc,"qual-chain-"+chain+"-"+name(cur)+".png");cur.onClose();phase--;}}
   case 3->{require(main!=null,"main panel never captured");
    // No widget path reaches About on this screen (root Settings opens SimpleSettingsScreen);
    // select the tab the way navigate() leaves it, then rebuild like navigate() does.
    var tabField=DoughBayScreen.class.getDeclaredField("tab");tabField.setAccessible(true);
    Class<?> tabEnum=Class.forName("dev.doughbay.fabric.DoughBayScreen$Tab");
    tabField.set(main,Enum.valueOf((Class)tabEnum.asSubclass(Enum.class),"ABOUT"));
    main.rebuildWidgets();}
   case 4->{require(mc.gui.screen()==main,"About tab left the main panel: "+name(mc.gui.screen()));
    require(find(main,"Project status")!=null,"About tab lost the Project status card button");
    capture(mc,"qual-3-about.png");mc.gui.setScreen(new QuickStartScreen(main));}
   case 5->{Screen cur=mc.gui.screen();require(cur instanceof QuickStartScreen,"QuickStart screen did not open: "+name(cur));trail.append(name(cur)).append(';');
    require(find(cur,"Project status: retired services")!=null,"QuickStart lost the retired-services button");
    require(find(cur,"Optional: join Swarm")==null,"QuickStart resurrected the retired swarm copy");
    capture(mc,"qual-4-quickstart.png");}
   case 6->{Files.writeString(Path.of("qual-surface-success.txt"),"Actual offline GUI: first-open retired-support notice, main panel, About tab and QuickStart captured; screens="+trail+" checks="+checks);mc.stop();}
  }
 }catch(Throwable e){try{Files.writeString(Path.of("qual-surface-error.txt"),e+" screens="+trail);}catch(Exception ignored){}mc.stop();}}
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
