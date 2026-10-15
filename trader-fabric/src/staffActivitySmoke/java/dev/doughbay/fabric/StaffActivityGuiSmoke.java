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
/** Offline screenshot fixture, never shipped. All observations are sample data. */
public final class StaffActivityGuiSmoke implements ClientModInitializer {
 private int ticks,phase; private DoughBayScreen screen;
 public void onInitializeClient(){Tuning.set("join.auto",0);Tuning.set("update.auto",0);Tuning.set("session.auto_resume",0);ClientTickEvents.END_CLIENT_TICK.register(this::tick);}
 private void tick(Minecraft mc){try{
  ticks++; if(mc.gui.overlay()!=null)return;
  long now=System.currentTimeMillis();
  var rows=new ArrayList<StaffWatchReport.Row>();var summaries=new ArrayList<StaffActivityMetrics.Summary>();
  for(int i=1;i<=5;i++){String name="SampleStaff"+i;rows.add(new StaffWatchReport.Row(name,i==2?"SPECTATOR":"ONLINE",true,false,"DemoObserver","NA West 1"));summaries.add(new StaffActivityMetrics.Summary(name,8+i,(2+i)*3600000L,now-6*86400000L,now-(i-1)*60000L,"18:00–20:00: detected during 62% of watched time"));}
  var history=List.of(new StaffWatchReport.Sighting(now-60000,"SampleStaff2",StaffWatchReport.Event.SPECTATOR,true,"NA West 1","DemoObserver"),new StaffWatchReport.Sighting(now-240000,"SampleStaff3",StaffWatchReport.Event.OBSERVED,true,"NA West 1","DemoObserver"));
  var f=StaffWatchReport.class.getDeclaredField("current");f.setAccessible(true);f.set(null,new StaffWatchReport.Snapshot(now,true,true,rows,history,"NA West 1",summaries,26*3600000L,2,"PREVIEW — sample data"));
  if(phase==2||phase==3){var pf=DoughBayScreen.class.getDeclaredField("staffWatchPanel");pf.setAccessible(true);var sf=StaffWatchPanel.class.getDeclaredField("scroll");sf.setAccessible(true);sf.setInt(pf.get(screen),phase==2?310:550);}
  if(phase==0&&ticks>100){if(mc.level!=null)throw new IllegalStateException("Preview must be offline");mc.options.pauseOnLostFocus=false;mc.options.guiScale().set(2);mc.resizeGui();screen=new DoughBayScreen(DoughBayClient.watcher());screen.selectStaffWatch();mc.gui.setScreen(screen);phase=1;ticks=0;}
  else if(phase==1&&ticks>30){capture(mc,"staff-watch-gui.png");var p=DoughBayScreen.class.getDeclaredField("staffWatchPanel");p.setAccessible(true);var panel=p.get(screen);var s=StaffWatchPanel.class.getDeclaredField("scroll");s.setAccessible(true);s.setInt(panel,550);phase=2;ticks=0;}
  else if(phase==2&&ticks>30){capture(mc,"staff-watch-gui-list.png");phase=3;ticks=0;} else if(phase==3&&ticks>30){capture(mc,"staff-watch-gui-history.png");phase=4;mc.stop();}
 }catch(Throwable e){try{Files.writeString(Path.of("staff-preview-error.txt"),e.toString());}catch(Exception ignored){}phase=3;mc.stop();}}    private void capture(Minecraft mc, String name) throws Exception {
        int w = mc.getWindow().getWidth(), h = mc.getWindow().getHeight();
        int fb = glGetInteger(GL_READ_FRAMEBUFFER_BINDING), pack = glGetInteger(GL_PIXEL_PACK_BUFFER_BINDING), align = glGetInteger(GL_PACK_ALIGNMENT);
        int row = glGetInteger(GL_PACK_ROW_LENGTH), sx = glGetInteger(GL_PACK_SKIP_PIXELS), sy = glGetInteger(GL_PACK_SKIP_ROWS);
        var bytes = MemoryUtil.memAlloc(w*h*4);
        try {
            glBindFramebuffer(GL_READ_FRAMEBUFFER,0); glReadBuffer(GL_BACK); glBindBuffer(GL_PIXEL_PACK_BUFFER,0);
            glPixelStorei(GL_PACK_ALIGNMENT,1); glPixelStorei(GL_PACK_ROW_LENGTH,0); glPixelStorei(GL_PACK_SKIP_PIXELS,0); glPixelStorei(GL_PACK_SKIP_ROWS,0);
            glReadPixels(0,0,w,h,GL_RGBA,GL_UNSIGNED_BYTE,bytes);
            var image = new BufferedImage(w,h,BufferedImage.TYPE_INT_RGB);
            for (int y=0;y<h;y++) for (int x=0;x<w;x++) {int p=(y*w+x)*4; image.setRGB(x,h-1-y,((bytes.get(p)&255)<<16)|((bytes.get(p+1)&255)<<8)|(bytes.get(p+2)&255));}
            ImageIO.write(image,"png",Path.of(name).toFile());
        } finally {MemoryUtil.memFree(bytes); glBindFramebuffer(GL_READ_FRAMEBUFFER,fb); glBindBuffer(GL_PIXEL_PACK_BUFFER,pack); glPixelStorei(GL_PACK_ALIGNMENT,align); glPixelStorei(GL_PACK_ROW_LENGTH,row); glPixelStorei(GL_PACK_SKIP_PIXELS,sx); glPixelStorei(GL_PACK_SKIP_ROWS,sy);}
    }
}




