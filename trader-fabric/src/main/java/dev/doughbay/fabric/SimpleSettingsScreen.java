package dev.doughbay.fabric;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import java.util.*;

/** Small set of explicit, previewed profiles; switching to/from Advanced is read-only. */
public final class SimpleSettingsScreen extends GoNutsScreen {
    private final DoughBayScreen parent;
    private final net.minecraft.client.gui.screens.Screen home;
    private String selected="",choice="",message="";
    private SimpleSettings.Preview draft;
    public SimpleSettingsScreen(DoughBayScreen parent){this(parent,parent);}
    SimpleSettingsScreen(DoughBayScreen parent,net.minecraft.client.gui.screens.Screen home){super(Component.literal("GoNuts simple settings"));this.parent=parent;this.home=home;}
    private int left(){return Math.max(12,(width-Math.min(660,width-24))/2);}
    private int wide(){return Math.min(660,width-24);}
    @Override protected void init(){
        int x=left(),w=wide();
        addRenderableWidget(CockpitButton.builder(Component.literal("Advanced view"),b->parent.openAdvancedSettings(SettingsSection.ALL)).bounds(x+w-110,12,110,20).build());
        if(selected.isEmpty()){
            var groups=SimpleSettings.current();int row=0;
            for(var group:groups){int y=70+row++*54;
                addRenderableWidget(CockpitButton.builder(Component.literal(group.current()+"  >"),b->{selected=group.id();choice="";draft=null;message="";rebuildWidgets();}).bounds(x+w-Math.min(190,w/2),y+7,Math.min(190,w/2),24).build());
            }
            int y=Math.min(height-55,70+groups.size()*54+8),bw=(w-8)/3;
            addRenderableWidget(CockpitButton.builder(Component.literal("Items & focus"),b->parent.openAdvancedSettings(SettingsSection.ITEMS)).bounds(x,y,bw,22).build());
            addRenderableWidget(CockpitButton.builder(Component.literal("Spending limits"),b->parent.openAdvancedSettings(SettingsSection.CAPITAL)).bounds(x+bw+4,y,bw,22).build());
            addRenderableWidget(CockpitButton.builder(Component.literal("Safety & recovery"),b->parent.openAdvancedSettings(SettingsSection.SAFETY)).bounds(x+2*(bw+4),y,bw,22).build());
        }else{
            var group=SimpleSettings.group(selected);int gap=4,bw=(w-gap*(group.options().size()-1))/group.options().size(),i=0;
            for(var option:group.options())addRenderableWidget(CockpitButton.builder(Component.literal(option.label()),b->{choice=option.id();draft=SimpleSettings.preview(selected,choice);message="";rebuildWidgets();}).bounds(x+i++*(bw+gap),76,bw,24).build());
            if(draft!=null){var apply=CockpitButton.builder(Component.literal("Apply "+draft.changes().size()+" change(s)"),b->{try{int n=SimpleSettings.apply(selected,choice,draft.expected());selected="";choice="";draft=null;message="Applied "+n+" setting(s). Everything else was kept.";}catch(IllegalArgumentException e){message=e.getMessage();draft=SimpleSettings.preview(selected,choice);}rebuildWidgets();}).bounds(x,height-56,Math.min(180,w/2),22).build();apply.active=!draft.changes().isEmpty();addRenderableWidget(apply);}
            addRenderableWidget(CockpitButton.builder(Component.literal("Cancel / back"),b->{selected="";choice="";draft=null;message="No settings changed.";rebuildWidgets();}).bounds(x+w-110,height-56,110,22).build());
        }
        addRenderableWidget(CockpitButton.builder(Component.literal("Help & setup"),b->minecraft.gui.setScreen(new StartHereScreen(parent))).bounds(x,height-27,Math.min(140,(w-8)/2),20).build());
        addRenderableWidget(CockpitButton.builder(Component.literal("Done"),b->onClose()).bounds(x+w-90,height-27,90,20).build());
    }
    private int wrap(GuiGraphicsExtractor g,String text,int x,int y,int w,int color,int maxLines){int count=0;for(var line:font.split(Component.literal(text),w)){if(count++>=maxLines)break;g.text(font,line,x,y,color);y+=12;}return y;}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){
        int x=left(),w=wide();g.fill(0,0,width,height,UiTheme.BACKGROUND);g.text(font,"GoNuts / SETTINGS / SIMPLE",x,17,UiTheme.GOLD);
        wrap(g,"Same saved settings. Switching views changes nothing. Custom values stay as they are.",x,40,w,UiTheme.SECONDARY,2);
        if(selected.isEmpty()){int row=0;for(var group:SimpleSettings.current()){int y=70+row++*54;g.fill(x,y,x+w,y+48,UiTheme.SURFACE);g.text(font,group.label(),x+8,y+8,UiTheme.TEXT);wrap(g,group.description(),x+8,y+23,w-Math.min(190,w/2)-22,UiTheme.SECONDARY,2);}}
        else{var group=SimpleSettings.group(selected);g.text(font,group.label(),x,64,UiTheme.TEXT);int y=111;if(draft==null)wrap(g,"Choose an option to preview exactly what it changes. Nothing applies until you press Apply.",x,y,w,UiTheme.SECONDARY,3);else{y=wrap(g,draft.effect(),x,y,w,UiTheme.SECONDARY,4)+7;for(var change:draft.changes()){if(y>height-105)break;g.text(font,UiTheme.fit(font,change.label()+": "+change.beforeShown()+" -> "+change.afterShown(),w),x,y,UiTheme.TEXT);y+=14;}for(var notice:draft.notices()){if(!notice.level().equals("warning")||y>height-96)continue;y=wrap(g,notice.title()+": "+notice.effect(),x,y+5,w,UiTheme.WARN,2)+4;}}}
        if(!message.isBlank())wrap(g,message,x,height-81,w,UiTheme.WARN,2);super.extractRenderState(g,mx,my,delta);
    }
    @Override public void onClose(){minecraft.gui.setScreen(home);}
    @Override public boolean isPauseScreen(){return false;}
}
