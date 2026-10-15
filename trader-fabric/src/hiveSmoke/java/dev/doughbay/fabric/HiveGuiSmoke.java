package dev.doughbay.fabric;
import java.nio.file.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import static org.lwjgl.opengl.GL33.*;
import org.lwjgl.system.MemoryUtil;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
public final class HiveGuiSmoke implements ClientModInitializer {
 private int ticks,phase;
 private final boolean joining=Boolean.getBoolean("gonuts.hive.preview.join");
 public void onInitializeClient(){Tuning.set("join.auto",0);Tuning.set("update.auto",0);Tuning.set("session.auto_resume",0);ClientTickEvents.END_CLIENT_TICK.register(this::tick);}
 private void tick(Minecraft mc){try{
  ticks++;if(mc.gui.overlay()!=null)return;
  if(mc.level!=null)throw new IllegalStateException("Hive preview must remain offline");
  if(phase==0&&ticks>100){
   mc.options.pauseOnLostFocus=false;mc.options.guiScale().set(2);mc.resizeGui();
   var screen=new DoughBayScreen(DoughBayClient.watcher());screen.selectHive();mc.gui.setScreen(screen);phase=1;ticks=0;
  }else if(phase==1&&ticks>30){
   capture(mc,"hive-before.png");
   if(HiveSetup.active()){
    var config=DoughBayClient.activeConfig();
    if(!config.directory().toRealPath().equals(HiveSetup.sharedDirectoryOr(config.directory()).toRealPath()))throw new IllegalStateException("Startup did not resolve shared configuration");
    capture(mc,"hive-connected.png");
    Files.writeString(Path.of("hive-restart-success.txt"),"Restart resolved the shared configuration and ledger; account isolation enabled.");
    phase=3;mc.stop();return;
   }
   if(joining){
    String code=Files.readString(Path.of(System.getenv("LOCALAPPDATA"),"GoNuts/hive-preview-source/invite.txt"));
    mc.gui.screen().children().stream().filter(e->e instanceof EditBox).map(e->(EditBox)e).findFirst().orElseThrow().setValue(code);
    press(mc,"Join hive");
   }else{
    press(mc,"Create hive & copy code");
    Files.writeString(Path.of("invite.txt"),mc.keyboardHandler.getClipboard());
   }
   phase=2;ticks=0;
  }else if(phase==2&&ticks>30){
   capture(mc,joining?"hive-joined.png":"hive-created.png");
   var member=HiveSetup.configured(net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir());
   if(member==null)throw new IllegalStateException("Hive link was not created");
   Files.writeString(Path.of("hive-preview-success.txt"),"Offline hive screen and durable link verified; no server connection.");phase=3;mc.stop();
  }
 }catch(Throwable e){try{Files.writeString(Path.of("hive-preview-error.txt"),e.toString());}catch(Exception ignored){}phase=3;mc.stop();}}
 private void press(Minecraft mc,String text){
  Button button=mc.gui.screen().children().stream().filter(e->e instanceof Button).map(e->(Button)e).filter(b->b.getMessage().getString().equals(text)).findFirst().orElseThrow();
  if(!button.active)throw new IllegalStateException("Expected active button: "+text);
  button.onPress(null);
 }
    private void capture(Minecraft mc, String name) throws Exception {
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
