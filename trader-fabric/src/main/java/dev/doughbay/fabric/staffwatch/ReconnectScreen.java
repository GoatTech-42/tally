package dev.doughbay.fabric.staffwatch;
import net.minecraft.client.gui.screens.*;import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.components.Button;import net.minecraft.client.gui.GuiGraphicsExtractor;import net.minecraft.network.chat.Component;
public final class ReconnectScreen extends dev.doughbay.fabric.GoNutsScreen {
 private Button reconnect;
 public ReconnectScreen(){super(Component.literal("Disconnected"));}
 protected void init(){
  int w=Math.min(200,width-40);
  reconnect=addRenderableWidget(Button.builder(Component.literal("Reconnect now"),b->ReconnectController.INSTANCE.reconnectNow()).bounds((width-w)/2,height/2+42,w,20).build());
  addRenderableWidget(Button.builder(Component.literal("Cancel / server list"),b->onClose()).bounds((width-w)/2,height/2+66,w,20).build());
  updateReconnectButton();
 }
 private void updateReconnectButton(){
  reconnect.active=ReconnectController.INSTANCE.canReconnectNow();
  reconnect.setMessage(Component.literal(ReconnectController.INSTANCE.schedule.watcher()!=null&&!reconnect.active?"Waiting for staff cooldown":"Reconnect now"));
 }
 public void tick(){super.tick();updateReconnectButton();}
 public void onClose(){ReconnectController.INSTANCE.cancel();minecraft.gui.setScreen(new JoinMultiplayerScreen(new TitleScreen()));}
 public void extractRenderState(GuiGraphicsExtractor g,int x,int y,float dt){super.extractRenderState(g,x,y,dt);var schedule=ReconnectController.INSTANCE.schedule;
  g.centeredText(font,Component.literal(schedule.watcher()==null?"Connection lost":"Staff Watch — disconnected"),width/2,height/2-95,0xFFFFFFFF);
  g.centeredText(font,Component.literal(schedule.watcher()==null?"Automatic retry every 2 minutes":"Watcher alert: "+schedule.watcher()),width/2,height/2-72,0xFFFFFFFF);
  g.centeredText(font,Component.literal("Server: "+schedule.address()),width/2,height/2-50,0xFFBDBDBD);
  if(schedule.watcher()==null&&!ReconnectController.INSTANCE.disconnectReason().isBlank()){
   int lineY=height/2-27,n=0;
   for(var line:font.split(Component.literal("Reason: "+ReconnectController.INSTANCE.disconnectReason()),Math.max(120,width-40))){
    if(n++==3)break;g.text(font,line,(width-font.width(line))/2,lineY,0xFFBDBDBD);lineY+=font.lineHeight+2;
   }
  }
  long seconds=(schedule.remainingMillis()+999)/1000;
  g.centeredText(font,Component.literal(String.format("Reconnecting in %d:%02d",seconds/60,seconds%60)),width/2,height/2+18,0xFFFFFFFF);
 }
}
