package dev.doughbay.fabric.staffwatch;
import net.minecraft.client.gui.screens.*;import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.components.Button;import net.minecraft.client.gui.GuiGraphicsExtractor;import net.minecraft.network.chat.Component;
public final class ReconnectScreen extends Screen {
 public ReconnectScreen(){super(Component.literal("Staff Watch — disconnected"));}
 protected void init(){addRenderableWidget(Button.builder(Component.literal("Cancel reconnect"),b->onClose()).bounds(width/2-100,height/2+42,200,20).build());}
 public void onClose(){ReconnectController.INSTANCE.cancel();minecraft.gui.setScreen(new JoinMultiplayerScreen(new TitleScreen()));}
 public void extractRenderState(GuiGraphicsExtractor g,int x,int y,float dt){super.extractRenderState(g,x,y,dt);var schedule=ReconnectController.INSTANCE.schedule;
  g.centeredText(font,title,width/2,height/2-70,0xFFFFCC66);
  g.centeredText(font,Component.literal(schedule.watcher()==null?"Connection lost — retrying every 2 minutes":"Watcher alert: "+schedule.watcher()),width/2,height/2-42,0xFFFFFFFF);
  g.centeredText(font,Component.literal("Server: "+schedule.address()),width/2,height/2-18,0xFFBBC8D8);
  long seconds=(schedule.remainingMillis()+999)/1000;
  g.centeredText(font,Component.literal(String.format("Reconnecting in %d:%02d",seconds/60,seconds%60)),width/2,height/2+8,0xFFFFFFFF);
 }
}
