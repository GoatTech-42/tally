package dev.doughbay.fabric;

import dev.doughbay.fabric.proxy.*;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.network.chat.*;
import net.minecraft.util.FormattedCharSequence;
import java.util.*;
import java.util.function.Consumer;

/** Same local manager embedded in the cockpit and the Multiplayer screen. */
final class ProxyPanel {
    private String selected="", type="SOCKS5", message="";
    private boolean clearCredentials;
    private final Map<String,EditBox> fields=new HashMap<>();
    private int x,y,w;
    void init(Font font,int x,int y,int width,Consumer<AbstractWidget> add,Runnable rebuild) {
        this.x=x;this.y=y;this.w=width;fields.clear();
        var settings=ProxyAccess.settings();var profiles=settings.state().profiles();
        if(selected.isEmpty()&&!profiles.isEmpty())selected=settings.state().selected().isEmpty()?profiles.getFirst().id():settings.state().selected();
        var profile=profiles.stream().filter(p->p.id().equals(selected)).findFirst().orElse(null);
        type=profile==null?type:profile.type();
        add.accept(CockpitButton.builder(Component.literal("Previous"),b->{choose(-1);rebuild.run();}).bounds(x,y,75,20).build());
        add.accept(CockpitButton.builder(Component.literal("Next"),b->{choose(1);rebuild.run();}).bounds(x+79,y,60,20).build());
        add.accept(CockpitButton.builder(Component.literal("New profile"),b->{selected="new";type="SOCKS5";clearCredentials=false;rebuild.run();}).bounds(x+w-100,y,100,20).build());
        int half=(w-10)/2;
        field(font,add,"name","Profile name",x,y+45,w,profile==null?"":profile.name(),48,false);
        field(font,add,"host","Host",x,y+83,w-90,profile==null?"":profile.host(),253,false);
        field(font,add,"port","Port",x+w-80,y+83,80,profile==null?"1080":String.valueOf(profile.port()),5,false);
        field(font,add,"username","Username (blank keeps saved)",x,y+121,half,"",255,false);
        field(font,add,"password","Password (blank keeps saved)",x+half+10,y+121,half,"",255,true);
        var kind=CockpitButton.builder(Component.literal(type),b->{type=type.equals("SOCKS5")?"SOCKS4":"SOCKS5";b.setMessage(Component.literal(type));}).bounds(x,y+150,90,20).build();add.accept(kind);
        add.accept(CockpitButton.builder(Component.literal(clearCredentials?"Clear saved credentials: Yes":"Clear saved credentials: No"),b->{clearCredentials=!clearCredentials;b.setMessage(Component.literal(clearCredentials?"Clear saved credentials: Yes":"Clear saved credentials: No"));}).bounds(x+100,y+150,Math.min(w-100,230),20).build());
        int bw=(w-12)/4;
        add.accept(CockpitButton.builder(Component.literal("Save"),b->{
            var form=new HashMap<String,String>();fields.forEach((key,field)->form.put(key,field.getValue()));form.put("action","save");form.put("id",profile==null?"":profile.id());form.put("type",type);form.put("clearCredentials",String.valueOf(clearCredentials));
            message=settings.change(form);if(profile==null&&settings.state().profiles().size()>profiles.size())selected=settings.state().profiles().getLast().id();clearCredentials=false;rebuild.run();
        }).bounds(x,y+180,bw,20).build());
        add.accept(CockpitButton.builder(Component.literal("Use proxy"),b->{message=settings.change(Map.of("action","enable","id",selected));rebuild.run();}).bounds(x+bw+4,y+180,bw,20).build());
        add.accept(CockpitButton.builder(Component.literal("Disable"),b->{message=settings.change(Map.of("action","disable"));rebuild.run();}).bounds(x+2*(bw+4),y+180,bw,20).build());
        add.accept(CockpitButton.builder(Component.literal("Delete"),b->{message=settings.change(Map.of("action","delete","id",selected));if(settings.state().profiles().stream().noneMatch(p->p.id().equals(selected)))selected="";rebuild.run();}).bounds(x+3*(bw+4),y+180,bw,20).build());
    }
    private void choose(int delta) {
        var list=ProxyAccess.settings().state().profiles();if(list.isEmpty())return;
        int index=0;for(int i=0;i<list.size();i++)if(list.get(i).id().equals(selected))index=i;
        selected=list.get(Math.floorMod(index+delta,list.size())).id();clearCredentials=false;
    }
    private void field(Font font,Consumer<AbstractWidget> add,String key,String label,int px,int py,int width,String value,int max,boolean secret) {
        var box=secret ? new EditBox(font,px,py,width,20,Component.literal(label)) {
            @Override protected MutableComponent createNarrationMessage() { return Component.literal("Proxy password"); }
        } : new EditBox(font,px,py,width,20,Component.literal(label));
        box.setMaxLength(max);box.setValue(value);
        if(secret)box.addFormatter((text,cursor)->FormattedCharSequence.forward("*".repeat(text.length()),Style.EMPTY));
        fields.put(key,box);add.accept(box);
    }
    void render(GuiGraphics g,Font font) {
        var settings=ProxyAccess.settings();var active=settings.selected();
        g.text(font,UiTheme.fit(font,settings.blocked()?"Routing blocked: fix settings or explicitly disable":settings.state().enabled()?"Active: "+active.name():"Direct connection (proxy disabled)",w-150),x+145,y+6,UiTheme.GOLD);
        g.text(font,"Profile name",x,y+33,UiTheme.TEXT);g.text(font,"Host",x,y+71,UiTheme.TEXT);g.text(font,"Port",x+w-80,y+71,UiTheme.TEXT);
        g.text(font,"Username (blank keeps saved)",x,y+109,UiTheme.TEXT);g.text(font,"Password (blank keeps saved)",x+(w-10)/2+10,y+109,UiTheme.TEXT);
        String note="Changes apply on the next connection. Each hive client has its own profiles.";
        g.text(font,UiTheme.fit(font,note,w),x,y+214,UiTheme.SECONDARY);
        g.text(font,UiTheme.fit(font,"SOCKS5 supports username/password. SOCKS4 supports username only.",w),x,y+229,UiTheme.MUTED);
        g.text(font,UiTheme.fit(font,"Credentials are stored locally with file access restricted to this user.",w),x,y+244,UiTheme.MUTED);
        if(!message.isEmpty())try{g.text(font,UiTheme.fit(font,new com.fasterxml.jackson.databind.ObjectMapper().readTree(message).path("note").asText(),w),x,y+264,UiTheme.SECONDARY);}catch(Exception ignored){}
    }
}
