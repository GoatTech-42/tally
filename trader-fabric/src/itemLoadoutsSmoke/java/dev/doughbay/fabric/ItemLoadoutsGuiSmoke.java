package dev.doughbay.fabric;
import java.nio.file.*;import java.util.*;import java.awt.image.BufferedImage;import javax.imageio.ImageIO;import static org.lwjgl.opengl.GL33.*;import org.lwjgl.system.MemoryUtil;
import net.fabricmc.api.ClientModInitializer;import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;import net.minecraft.client.gui.components.*;
/** Native offline regression with disposable invented item selections; no network actions. */
public final class ItemLoadoutsGuiSmoke implements ClientModInitializer {
 private int phase,ticks;private final long started=System.currentTimeMillis();private String firstId,firstCode;private Path fixtures;
 public void onInitializeClient(){Tuning.set("join.auto",0);Tuning.set("update.auto",0);Tuning.set("session.auto_resume",0);ClientTickEvents.END_CLIENT_TICK.register(this::tick);}
 private void require(boolean ok,String why){if(!ok)throw new IllegalStateException(why);}
 private void tick(Minecraft mc){try{
  if(mc.level!=null)throw new IllegalStateException("Fixture must remain offline");
  if(System.currentTimeMillis()-started>150000)throw new IllegalStateException("Fixture timed out");
  if(mc.gui.overlay()!=null||++ticks<35)return;ticks=0;
  switch(phase++){
   case 0->{mc.options.pauseOnLostFocus=false;
    net.minecraft.core.registries.BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(net.minecraft.data.registries.VanillaRegistries.createLookup()).forEach(net.minecraft.core.component.DataComponentInitializers.PendingComponents::apply);
    fixtures=Files.createTempDirectory(Path.of("."),"item-loadout-fixture-");SavedPresets.load(fixtures);
    Tuning.setText("items.allow","=diamond");Tuning.setText("items.deny","=mace");Tuning.set("watch.tracked_managed",1);Tuning.setText("watch.tracked","diamond");
    var screen=new DoughBayScreen(DoughBayClient.watcher());var tab=DoughBayScreen.class.getDeclaredField("tab");tab.setAccessible(true);
    @SuppressWarnings({"rawtypes","unchecked"}) Object value=Enum.valueOf((Class)tab.getType(),"ITEMS");tab.set(screen,value);mc.gui.setScreen(screen);}
   case 1->{var b=button(mc,"Item loadouts");require(b.visible&&b.active,"Item loadouts entry missing");capture(mc,"items-loadouts-entry.png");press(b);}
   case 2->{name(mc,"Diamonds");press(button(mc,"Save current as new"));require(SavedPresets.items().entries().size()==1,"First copy was not saved");
    var entry=SavedPresets.items().entries().getFirst();firstId=entry.id();firstCode=entry.code();
    Tuning.setText("items.allow","boots");Tuning.setText("items.deny","");Tuning.setText("watch.tracked","iron_boots");name(mc,"Armor");press(button(mc,"Save current as new"));
    require(new SavedPresets(fixtures,true).entries().size()==2,"Independent copies did not survive file reload");press(button(mc,"Diamonds"));
    Tuning.set("buy.purchase_cap_pct",11);Tuning.set("slots.max",45);press(button(mc,"Apply selected"));
    require(Tuning.itemSet("items.allow").equals(Set.of("=diamond")),"Wrong include selection");require(ItemLists.tracked().equals(Set.of("diamond")),"Wrong tracked selection");
    require(Tuning.get("buy.purchase_cap_pct")==11&&Tuning.get("slots.max")==45,"Item apply altered trading limits");
    require(SavedPresets.current().entries().isEmpty(),"Item loadouts mixed with full presets");}
   case 3->{capture(mc,"item-loadouts-applied.png");name(mc,"Diamond copy");press(button(mc,"Rename"));require(SavedPresets.items().get(firstId).code().equals(firstCode),"Rename changed captured selection");
    press(button(mc,"Delete selected"));require(SavedPresets.items().entries().size()==2,"Delete skipped its confirmation");press(button(mc,"Confirm delete"));require(SavedPresets.items().entries().size()==1,"Confirmed test copy was not deleted");
    press(button(mc,"Done"));}
   case 4->{require(button(mc,"Item loadouts").visible,"Return to Items lost entry");mc.options.guiScale().set(3);mc.resizeGui();}
   case 5->{require(button(mc,"Item loadouts").visible,"Resize lost entry");press(button(mc,"Item loadouts"));}
   case 6->{require(button(mc,"Apply selected").visible,"Resize lost apply control");capture(mc,"item-loadouts-reopened.png");Files.writeString(Path.of("item-loadouts-success.txt"),"Actual offline GUI: Items entry; separate named item copies; disk reload; item-only apply preserves limits; rename keeps code; confirmed test-copy deletion; return/reopen/resize. No server connection or live client changes.");mc.stop();}
  }
 }catch(Throwable e){try{Files.writeString(Path.of("item-loadouts-error.txt"),e.toString());}catch(Exception ignored){}mc.stop();}}
 private Button button(Minecraft mc,String text){return mc.gui.screen().children().stream().filter(c->c instanceof Button).map(c->(Button)c).filter(b->b.getMessage().getString().equals(text)).findFirst().orElseThrow(()->new IllegalStateException("Missing action: "+text));}
 private void name(Minecraft mc,String value){var fields=mc.gui.screen().children().stream().filter(c->c instanceof EditBox).map(c->(EditBox)c).toList();require(fields.size()==1,"Ambiguous name field");fields.getFirst().setValue(value);}
 private void press(Button b)throws Exception{for(var f:Button.class.getDeclaredFields())if(f.getType()==Button.OnPress.class){f.setAccessible(true);((Button.OnPress)f.get(b)).onPress(b);return;}throw new IllegalStateException("Missing native callback");}
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
