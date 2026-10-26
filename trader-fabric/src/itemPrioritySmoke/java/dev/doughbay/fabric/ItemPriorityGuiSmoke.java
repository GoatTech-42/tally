package dev.doughbay.fabric;
import java.nio.file.*;import java.util.*;import java.awt.image.BufferedImage;import javax.imageio.ImageIO;import static org.lwjgl.opengl.GL33.*;import org.lwjgl.system.MemoryUtil;import org.lwjgl.glfw.GLFW;
import net.fabricmc.api.ClientModInitializer;import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;import net.minecraft.client.gui.components.*;
/** Isolated offline application test. Uses invented item preferences, no desktop input or server actions. */
public final class ItemPriorityGuiSmoke implements ClientModInitializer {
 private int phase,ticks;private final long started=System.currentTimeMillis();private DoughBayScreen parent;private Path settings;
 public void onInitializeClient(){try{
  Path cwd=Path.of(".").toAbsolutePath().normalize();var f=Tuning.class.getDeclaredField("file");f.setAccessible(true);settings=(Path)f.get(null);
  require(cwd.getFileName().toString().startsWith("item-priority70-preview"),"Fixture requires its dedicated game directory");require(settings!=null&&settings.toAbsolutePath().normalize().startsWith(cwd),"Fixture settings must be isolated");
  Tuning.set("join.auto",0);Tuning.set("update.auto",0);Tuning.set("session.auto_resume",0);ClientTickEvents.END_CLIENT_TICK.register(this::tick);
 }catch(Exception e){throw new IllegalStateException(e);}}
 private void require(boolean ok,String why){if(!ok)throw new IllegalStateException(why);}
 private void tick(Minecraft mc){try{
  GLFW.glfwHideWindow(mc.getWindow().handle());
  if(mc.level!=null)throw new IllegalStateException("Fixture must stay offline");if(System.currentTimeMillis()-started>180000)throw new IllegalStateException("Fixture timed out");
  if(mc.gui.overlay()!=null||++ticks<35)return;ticks=0;
  switch(phase++){
   case 0->{mc.options.pauseOnLostFocus=false;
    net.minecraft.core.registries.BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(net.minecraft.data.registries.VanillaRegistries.createLookup()).forEach(net.minecraft.core.component.DataComponentInitializers.PendingComponents::apply);
    Tuning.setText("items.allow","=diamond, =emerald");Tuning.setText("items.deny","=mace");Tuning.setText(ItemPriorities.KEY,"");Tuning.set("buy.purchase_cap_pct",11);Tuning.set("slots.max",45);
    parent=new DoughBayScreen(DoughBayClient.watcher());var tab=DoughBayScreen.class.getDeclaredField("tab");tab.setAccessible(true);@SuppressWarnings({"rawtypes","unchecked"}) Object value=Enum.valueOf((Class)tab.getType(),"ITEMS");tab.set(parent,value);select("minecraft:diamond","minecraft:emerald");mc.gui.setScreen(parent);}
   case 1->{require(button(mc,"Priority").active,"Selected items have no priority action");require(button(mc,"Item loadouts").visible,"Existing loadouts entry lost");capture(mc,"items-priority-entry.png");press(button(mc,"Priority"));}
   case 2->{require(button(mc,"High").active&&button(mc,"Standard").active&&button(mc,"Low").active,"Priority choices missing");capture(mc,"priority-choices.png");press(button(mc,"High"));require(ItemPriorities.level("diamond")==ItemPriorities.Level.HIGH&&ItemPriorities.level("emerald")==ItemPriorities.Level.HIGH,"High did not apply selected items");unchanged();}
   case 3->{capture(mc,"items-priority-high.png");select("minecraft:diamond");press(button(mc,"Priority"));press(button(mc,"Low"));require(ItemPriorities.level("diamond")==ItemPriorities.Level.LOW&&ItemPriorities.level("emerald")==ItemPriorities.Level.HIGH,"Single-item change altered the other item");select("minecraft:diamond","minecraft:emerald");press(button(mc,"Priority"));}
   case 4->{capture(mc,"priority-mixed.png");press(button(mc,"Back to Items"));require(ItemPriorities.level("diamond")==ItemPriorities.Level.LOW,"Back changed preference");Tuning.load(settings.getParent());require(ItemPriorities.level("diamond")==ItemPriorities.Level.LOW&&ItemPriorities.level("emerald")==ItemPriorities.Level.HIGH,"File reload lost preferences");unchanged();press(button(mc,"Priority"));press(button(mc,"Standard"));}
   case 5->{require(ItemPriorities.level("diamond")==ItemPriorities.Level.STANDARD&&ItemPriorities.level("emerald")==ItemPriorities.Level.STANDARD,"Standard did not reset both items");unchanged();select();GLFW.glfwSetWindowSize(mc.getWindow().handle(),390,768);}
   case 6->{require(button(mc,"Priority").visible&&!button(mc,"Priority").active,"Empty selection priority action must be disabled");require(button(mc,"Item loadouts").visible,"Compact layout lost loadouts");capture(mc,"items-priority-compact.png");select("minecraft:diamond");mc.resizeGui();}
   case 7->{press(button(mc,"Priority"));}
   case 8->{capture(mc,"priority-compact.png");press(button(mc,"High"));require(ItemPriorities.level("diamond")==ItemPriorities.Level.HIGH&&ItemPriorities.level("emerald")==ItemPriorities.Level.STANDARD,"Compact apply altered another item");unchanged();Files.writeString(Path.of("item-priority70-success.txt"),"Actual offline GUI: selected-items Priority entry; High/Standard/Low; mixed and single selections; Back; settings reload; unchanged item filters/caps; empty-selection disable; compact resize/reopen/apply. No desktop input, server connection or live client changes.");mc.stop();}
  }
 }catch(Throwable e){try{Files.writeString(Path.of("item-priority70-error.txt"),e.toString());}catch(Exception ignored){}mc.stop();}}
 private void unchanged(){require(Tuning.get("buy.purchase_cap_pct")==11&&Tuning.get("slots.max")==45,"Priority altered limits");require(Tuning.itemSet("items.allow").equals(Set.of("=diamond","=emerald"))&&Tuning.itemSet("items.deny").equals(Set.of("=mace")),"Priority changed item filters");require(Tuning.itemAllowed("diamond"),"Low disabled the selected item");}
 @SuppressWarnings("unchecked") private void select(String...ids)throws Exception{var f=DoughBayScreen.class.getDeclaredField("itemsSelected");f.setAccessible(true);var selected=(Set<String>)f.get(parent);selected.clear();selected.addAll(List.of(ids));}
 private Button button(Minecraft mc,String text){return mc.gui.screen().children().stream().filter(c->c instanceof Button).map(c->(Button)c).filter(b->b.getMessage().getString().equals(text)).findFirst().orElseThrow(()->new IllegalStateException("Missing action: "+text));}
 private void press(Button b)throws Exception{for(var f:Button.class.getDeclaredFields())if(f.getType()==Button.OnPress.class){f.setAccessible(true);((Button.OnPress)f.get(b)).onPress(b);return;}throw new IllegalStateException("Missing callback");}
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
