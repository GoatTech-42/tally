package dev.doughbay.fabric;
import java.nio.file.*;import java.util.*;import java.awt.image.BufferedImage;import javax.imageio.ImageIO;
import static org.lwjgl.opengl.GL33.*;import org.lwjgl.system.MemoryUtil;
import net.fabricmc.api.ClientModInitializer;import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.*;import net.minecraft.world.level.levelgen.WorldOptions;import net.minecraft.world.level.levelgen.presets.WorldPresets;
/** Disposable single-player review. Uses no signed-in account or live configuration. */
public final class SettingsGuiSmoke implements ClientModInitializer {
 private String beforeViewSwitch="";private int phase,ticks,scaleIndex;private final List<Map<String,Object>> scaleProof=new ArrayList<>();private long started=System.currentTimeMillis();private dev.doughbay.fabric.web.LocalPanel panel;
 public void onInitializeClient(){Tuning.set("join.auto",0);Tuning.set("update.auto",0);Tuning.set("update.check",0);Tuning.set("session.auto_resume",0);Tuning.set("multi.enabled",0);ClientTickEvents.END_CLIENT_TICK.register(this::tick);}
 private void open(Minecraft mc,SettingsSection section)throws Exception{
  var screen=new DoughBayScreen(DoughBayClient.watcher());var tab=DoughBayScreen.class.getDeclaredField("tab");tab.setAccessible(true);
  @SuppressWarnings({"unchecked","rawtypes"}) Object value=Enum.valueOf((Class)tab.getType(),"SETTINGS");tab.set(screen,value);
  var field=DoughBayScreen.class.getDeclaredField("settingsSection");field.setAccessible(true);field.set(screen,section);mc.gui.setScreen(screen);
 }
 private void next(){phase++;ticks=0;}
 private void tick(Minecraft mc){if(phase==99)return;try{
  if(phase<15&&System.currentTimeMillis()-started>900000)throw new IllegalStateException("Local visual review timed out");
  if(mc.gui.overlay()!=null)return;ticks++;
  if(phase==0){if(ticks<70)return;mc.options.pauseOnLostFocus=false;org.lwjgl.glfw.GLFW.glfwRestoreWindow(mc.getWindow().handle());org.lwjgl.glfw.GLFW.glfwSetWindowSize(mc.getWindow().handle(),1440,1000);mc.options.guiScale().set(2);mc.options.renderDistance().set(4);mc.resizeGui();
   mc.createWorldOpenFlows().createFreshLevel("settings-review-"+System.currentTimeMillis(),new LevelSettings("Settings UI local review",GameType.CREATIVE,LevelSettings.DifficultySettings.DEFAULT,true,WorldDataConfiguration.DEFAULT),new WorldOptions(2611211L,false,false),r->r.lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),mc.gui.screen());next();return;}
  if(mc.level==null||mc.player==null)return;
  switch(phase){
   case 1->{if(ticks<40)return;
    Tuning.set("evasion.escape",0);Tuning.set("orders.enabled",1);Tuning.set("orders.bid",1);Tuning.set("orders.bid_min_samples",15);Tuning.set("orders.bid_on_real_trades",0);Tuning.set("orders.bid_stacks",16);Tuning.set("buy.focus",1);Tuning.setText("buy.focus_items","");
    panel=dev.doughbay.fabric.web.LocalPanel.start(0,false,"2468"::equals,new dev.doughbay.fabric.web.LocalPanel.Backend(){
     public byte[] png(){return new byte[0];}public String state(){return "{\"page\":\"Status\",\"notice\":\"ISOLATED SINGLE-PLAYER PREVIEW\",\"components\":[]}";}
     public void press(String id,String value){}public void text(String id,String text){}
     public String data(String name,Map<String,String> q){return WebPages.data(name,q);}
     public String change(String name,Map<String,String> f){return WebPages.change(name,f);}
    },org.slf4j.LoggerFactory.getLogger("SettingsPreview"));
    Files.writeString(Path.of("settings-panel-port.txt"),Integer.toString(panel.port()));open(mc,SettingsSection.ORDERS);next();}
   case 2->{if(ticks<35)return;capture(mc,"settings-orders.png");open(mc,SettingsSection.ITEMS);next();}
   case 3->{if(ticks<35)return;capture(mc,"settings-focus.png");open(mc,SettingsSection.CAPITAL);next();}
   case 4->{if(ticks<35)return;capture(mc,"settings-money.png");open(mc,SettingsSection.SAFETY);next();}
   case 5->{if(ticks<35)return;capture(mc,"settings-safety.png");org.lwjgl.glfw.GLFW.glfwSetWindowSize(mc.getWindow().handle(),960,720);mc.options.guiScale().set(2);mc.resizeGui();open(mc,SettingsSection.ITEMS);next();}
   case 6->{if(ticks<35)return;capture(mc,"settings-narrow.png");org.lwjgl.glfw.GLFW.glfwSetWindowSize(mc.getWindow().handle(),1440,1000);mc.options.guiScale().set(2);mc.resizeGui();Tuning.set("buy.focus",0);Tuning.set("orders.bid_min_samples",6);open(mc,SettingsSection.ORDERS);next();}
   case 7->{if(ticks<35)return;capture(mc,"settings-ready.png");next();}
   case 8->{if(ticks<10)return;int[] scales={1,2,3,4,0};mc.options.guiScale().set(scales[scaleIndex]);mc.resizeGui();open(mc,SettingsSection.ITEMS);next();}
   case 9->{if(ticks<30)return;
    int requested=new int[]{1,2,3,4,0}[scaleIndex];var window=mc.getWindow();var screen=(DoughBayScreen)mc.gui.screen();
    int expected=GoNutsUiScale.preferred(window.getWidth(),window.getHeight());
    if(window.getGuiScale()!=expected||mc.options.guiScale().get()!=requested||screen.width!=window.getGuiScaledWidth()||screen.height!=window.getGuiScaledHeight())throw new AssertionError("Independent menu scale or saved option mismatch");
    for(var widget:net.fabricmc.fabric.api.client.screen.v1.Screens.getWidgets(screen))if(widget.getX()<0||widget.getY()<0||widget.getX()+widget.getWidth()>screen.width||widget.getY()+widget.getHeight()>screen.height)throw new AssertionError("Widget outside screen at Minecraft scale "+requested);
    var chooser=net.fabricmc.fabric.api.client.screen.v1.Screens.getWidgets(screen).stream().filter(w->w.getMessage().getString().contains("choose category")).findFirst().orElseThrow();
    double chooserRawX=(chooser.getX()+chooser.getWidth()/2d)*window.getScreenWidth()/window.getGuiScaledWidth(),chooserRawY=(chooser.getY()+chooser.getHeight()/2d)*window.getScreenHeight()/window.getGuiScaledHeight();
    var chooserEvent=new net.minecraft.client.input.MouseButtonEvent(net.minecraft.client.MouseHandler.getScaledXPos(window,chooserRawX),net.minecraft.client.MouseHandler.getScaledYPos(window,chooserRawY),new net.minecraft.client.input.MouseButtonInfo(0,0));
    if(!screen.mouseClicked(chooserEvent,false))throw new AssertionError("Category chooser click not accepted");screen.mouseReleased(chooserEvent);
    if(scaleIndex==0)capture(mc,"settings-categories.png");
    var button=net.fabricmc.fabric.api.client.screen.v1.Screens.getWidgets(screen).stream().filter(w->w.getMessage().getString().equals("Orders")).findFirst().orElseThrow();
    double x=button.getX()+button.getWidth()/2d,y=button.getY()+button.getHeight()/2d;
    double rawX=x*window.getScreenWidth()/window.getGuiScaledWidth(),rawY=y*window.getScreenHeight()/window.getGuiScaledHeight();
    var event=new net.minecraft.client.input.MouseButtonEvent(net.minecraft.client.MouseHandler.getScaledXPos(window,rawX),net.minecraft.client.MouseHandler.getScaledYPos(window,rawY),new net.minecraft.client.input.MouseButtonInfo(0,0));
    if(!screen.mouseClicked(event,false))throw new AssertionError("Menu click not accepted");screen.mouseReleased(event);
    var field=DoughBayScreen.class.getDeclaredField("settingsSection");field.setAccessible(true);if(field.get(screen)!=SettingsSection.ORDERS)throw new AssertionError("Click landed on wrong category");
    scaleProof.add(Map.of("minecraftOption",requested,"menuScale",expected,"width",screen.width,"height",screen.height,"widgetsInBounds",true,"nativePointerConversionClickedCorrectCategory",true));
    capture(mc,"settings-game-scale-"+requested+".png");scaleIndex++;
    if(scaleIndex<5){phase=8;ticks=0;}else{mc.options.guiScale().set(4);mc.resizeGui();mc.gui.setScreen(new net.minecraft.client.gui.screens.PauseScreen(true));next();}
   }
   case 10->{if(ticks<20)return;
    var window=mc.getWindow();if(window.getGuiScale()!=window.calculateScale(4,mc.isEnforceUnicode())||mc.options.guiScale().get()!=4)throw new AssertionError("Native scale was not restored on close");
    mc.gui.setScreen(new PresetsScreen(mc.gui.screen()));next();}
   case 11->{if(ticks<20)return;
    var window=mc.getWindow();if(window.getGuiScale()!=GoNutsUiScale.preferred(window.getWidth(),window.getHeight()))throw new AssertionError("Supporting mod screen did not use independent scale");
    capture(mc,"settings-presets-independent-scale.png");mc.options.guiScale().set(2);open(mc,SettingsSection.ORDERS);
    Files.writeString(Path.of("settings-scale-verification.json"),new com.fasterxml.jackson.databind.ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(Map.of("scales",scaleProof,"nativeScaleRestored",true,"savedOptionUnchangedByMenu",true,"supportingScreenVerified",true)));
    beforeViewSwitch=SettingsCode.export();mc.gui.setScreen(new SimpleSettingsScreen((DoughBayScreen)mc.gui.screen()));next();}
   case 12->{if(ticks<25)return;capture(mc,"settings-simple.png");
    var screen=(SimpleSettingsScreen)mc.gui.screen();var selected=SimpleSettingsScreen.class.getDeclaredField("selected");selected.setAccessible(true);selected.set(screen,"pace");var choice=SimpleSettingsScreen.class.getDeclaredField("choice");choice.setAccessible(true);choice.set(screen,"fast");var draft=SimpleSettingsScreen.class.getDeclaredField("draft");draft.setAccessible(true);draft.set(screen,SimpleSettings.preview("pace","fast"));screen.resize(screen.width,screen.height);next();}
   case 13->{if(ticks<25)return;capture(mc,"settings-simple-preview.png");if(!beforeViewSwitch.equals(SettingsCode.export()))throw new AssertionError("Simple view or preview changed saved settings");open(mc,SettingsSection.ORDERS);next();}
   case 14->{if(ticks<25)return;capture(mc,"settings-ready.png");if(!beforeViewSwitch.equals(SettingsCode.export()))throw new AssertionError("Switching back to Advanced changed settings");Files.writeString(Path.of("settings-preview-ready.txt"),"Simple/Advanced view switches and preview preserve all settings; independent scaling and native hitboxes verified.");next();}
   case 15->{/* Remain open for local browser and GUI review. */}
  }
 }catch(Throwable e){e.printStackTrace();try{Files.writeString(Path.of("settings-preview-error.txt"),e.toString());}catch(Exception ignored){}phase=99;if(panel!=null)panel.close();mc.stop();}}
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
