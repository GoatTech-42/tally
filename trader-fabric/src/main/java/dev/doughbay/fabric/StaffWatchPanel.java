package dev.doughbay.fabric;

import java.util.*;
import java.util.function.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.network.chat.Component;
import dev.doughbay.fabric.staffwatch.WatchConfig;
import dev.doughbay.fabric.staffwatch.ReconnectController;
import dev.doughbay.fabric.staffwatch.module.impl.misc.StaffListModule;

/** Embedded in the cockpit, with the same controls and surface colors as the other tabs. */
final class StaffWatchPanel {
 private final List<AbstractWidget> widgets=new ArrayList<>();
 private final Map<AbstractWidget,Integer> offsets=new IdentityHashMap<>();
 private int x,top,w,bottom,scroll,maxScroll;
 private String message="";
 private boolean error;
 void init(Font font,int x,int top,int w,int bottom,Consumer<AbstractWidget> add){
  this.x=x;this.top=top;this.w=w;this.bottom=bottom;widgets.clear();offsets.clear();
  var c=WatchConfig.get();
  int col=(w-24)/2;
  toggle(add,"Module",()->c.enabled,v->c.enabled=v,x+8,40,col);
  toggle(add,"Escape / reconnect",()->c.autoDisconnect,v->c.autoDisconnect=v,x+16+col,40,col);
  toggle(add,"Staff List",()->c.staffList,v->c.staffList=v,x+8,66,col);
  toggle(add,"Anti Vanish",()->c.antiVanish,v->c.antiVanish=v,x+16+col,66,col);
  toggle(add,"Region alerts",()->c.regionAlert,v->c.regionAlert=v,x+8,92,col);
  toggle(add,"Show HUD",()->c.showHud,v->c.showHud=v,x+16+col,92,col);
  toggle(add,"Alert sounds",()->c.alertSounds,v->c.alertSounds=v,x+8,118,col);
  toggle(add,"Switch regions first",()->c.regionSwitch,v->c.regionSwitch=v,x+16+col,118,col);
  add(add,CockpitButton.builder(Component.literal("Server: "+c.reconnectAddress()),b->{int next=(WatchConfig.SERVER_ADDRESSES.indexOf(c.reconnectAddress())+1)%WatchConfig.SERVER_ADDRESSES.size();c.serverAddress=WatchConfig.SERVER_ADDRESSES.get(next);persist();b.setMessage(Component.literal("Server: "+c.reconnectAddress()));}).bounds(x+8,top+162,w-16,20).build(),162);
  position();
 }
 private void add(Consumer<AbstractWidget> add,AbstractWidget widget,int y){widgets.add(widget);offsets.put(widget,y);add.accept(widget);}
 private void toggle(Consumer<AbstractWidget> add,String name,BooleanSupplier get,Consumer<Boolean> set,int x,int y,int width){
  add(add,CockpitButton.builder(label(name,get.getAsBoolean()),b->{set.accept(!get.getAsBoolean());persist();b.setMessage(label(name,get.getAsBoolean()));}).bounds(x,top+y,width,20).build(),y);
 }
 private Component label(String name,boolean on){return Component.literal(name+": "+(on?"ON":"OFF"));}
 private void persist(){try{WatchConfig.get().save();message="Saved";error=false;}catch(Exception e){message="Could not save settings";error=true;}}
 private void position(){for(var widget:widgets){int y=top+offsets.get(widget)-scroll;widget.setY(y);widget.visible=y>=top&&y+widget.getHeight()<=bottom;}}
 boolean scroll(double mx,double my,double delta){if(mx<x||mx>x+w||my<top||my>bottom)return false;scroll=Math.max(0,Math.min(maxScroll,scroll-(int)(delta*24)));position();return true;}
 void render(GuiGraphics g,Font font){
  var c=WatchConfig.get();long now=System.currentTimeMillis();var report=StaffWatchReport.snapshot();var rows=report.current(now);
  int historyRows=Math.min(20,report.summaries().size()),eventRows=Math.min(20,report.history().size());
  maxScroll=Math.max(0,480+Math.max(1,rows.size())*22+historyRows*64+eventRows*22-(bottom-top));scroll=Math.min(scroll,maxScroll);position();int y=top-scroll;
  g.enableScissor(x,top,x+w,bottom);
  UiTheme.panel(g,x,y,w,218,UiTheme.SURFACE);
  text(g,font,"STAFF WATCH",x+10,y+10,UiTheme.GOLD);
  text(g,font,"A staff spectator detection does not prove they are watching you.",x+10,y+25,UiTheme.SECONDARY);
  text(g,font,"DONUT SMP — click to choose Main / East / West",x+10,y+148,UiTheme.TEXT);
  text(g,font,c.regionSwitch?"First alert: switch region. Same staff within 2 min: disconnect.":"Staff alert: disconnect immediately; wait 5–10 min before rejoining.",x+10,y+190,UiTheme.MUTED);
  text(g,font,message,x+10,y+204,error?UiTheme.BAD:UiTheme.GOOD);
  UiTheme.panel(g,x,y+226,w,76,UiTheme.INSET);
  text(g,font,c.enabled?"MONITORING ENABLED":"MODULE DISABLED",x+10,y+237,c.enabled?UiTheme.GOOD:UiTheme.MUTED);
  String last=ReconnectController.INSTANCE.lastWatcher();
  text(g,font,last.isEmpty()?"No watcher alerts this session":"Last watcher alert: "+last,x+10,y+255,UiTheme.TEXT);
  var timer=ReconnectController.INSTANCE.schedule;
  text(g,font,timer.active()?"Reconnect in "+((timer.remainingMillis()+999)/1000)+" seconds":Minecraft.getInstance().level==null?"Join Donut SMP to monitor staff":"Donut SMP Staff Watch",x+10,y+273,UiTheme.SECONDARY);
  text(g,font,"Current hive observations ("+rows.size()+")",x+10,y+315,UiTheme.GOLD);
  int rowY=y+334;
  if(rows.isEmpty())text(g,font,"No matching staff currently visible to the detector.",x+10,rowY,UiTheme.MUTED);
  for(var row:rows){UiTheme.panel(g,x,rowY-4,w,21,UiTheme.SURFACE);text(g,font,dev.doughbay.fabric.staffwatch.util.NameFilter.apply(row.name())+"  •  "+StaffWatchReport.presenceLabel(row.presence())+"  •  "+row.observer(),x+10,rowY,row.presence().equals("SPECTATOR")?UiTheme.BAD:UiTheme.TEXT);rowY+=22;}
  if(rows.isEmpty())rowY+=22;
  rowY+=12;text(g,font,"ACTIVITY HISTORY — 30 DAYS",x+10,rowY,UiTheme.GOLD);rowY+=16;
  text(g,font,report.status(now)+" • watched "+StaffActivityMetrics.durationLabel(report.watchedMillis()),x+10,rowY,UiTheme.SECONDARY);rowY+=15;
  text(g,font,"Passive observations; gaps excluded and hive overlaps merged.",x+10,rowY,UiTheme.MUTED);rowY+=15;
  text(g,font,"Hours shown in "+java.time.ZoneId.systemDefault()+"; staff timezones unknown.",x+10,rowY,UiTheme.MUTED);rowY+=22;
  if(report.summaries().isEmpty()){text(g,font,"History starts when an updated client begins observing.",x+10,rowY,UiTheme.MUTED);rowY+=22;}
  for(var summary:report.summaries().stream().limit(20).toList()) {
   String seen=java.time.Instant.ofEpochMilli(summary.lastSeen()).atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("MMM d HH:mm"));
   text(g,font,dev.doughbay.fabric.staffwatch.util.NameFilter.apply(summary.name())+" • "+summary.intervals()+" intervals • "+StaffActivityMetrics.durationLabel(summary.observedMillis())+" • last "+seen,x+10,rowY,UiTheme.TEXT);rowY+=16;
   String first=java.time.Instant.ofEpochMilli(summary.firstSeen()).atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("MMM d HH:mm"));
   text(g,font,"First in retained history: "+first,x+10,rowY,UiTheme.SECONDARY);rowY+=16;
   text(g,font,"Observed hours: "+summary.usualHours(),x+10,rowY,UiTheme.SECONDARY);rowY+=32;
  }
  text(g,font,"RECENT SIGHTINGS — absence does not prove offline",x+10,rowY,UiTheme.GOLD);rowY+=22;
  for(var event:report.history().stream().limit(20).toList()) {
   String at=java.time.Instant.ofEpochMilli(event.at()).atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"));
   text(g,font,at+" • "+dev.doughbay.fabric.staffwatch.util.NameFilter.apply(event.name())+" • "+StaffWatchReport.label(event.event())+" • "+event.observer(),x+10,rowY,UiTheme.TEXT);rowY+=22;
  }
  g.disableScissor();
  if(maxScroll>0)g.drawString(font,"Scroll for more",x+w-82,bottom-9,UiTheme.MUTED);
 }
 private void text(GuiGraphics g,Font font,String text,int x,int y,int color){g.drawString(font,UiTheme.fit(font,text,w-20),x,y,color);}
}
