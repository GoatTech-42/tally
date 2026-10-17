package dev.doughbay.fabric;

import dev.doughbay.fabric.hosted.CommunityClient;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/** Explicit per-installation Swarm opt-in; existing live trading gates remain authoritative. */
public final class CommunityScreen extends Screen {
    private final Screen parent;
    private net.minecraft.client.gui.components.Button enable,disable;
    public CommunityScreen(Screen parent){super(Component.literal("Swarm API"));this.parent=parent;}
    @Override protected void init(){
        int w=Math.min(500,width-24),x=(width-w)/2;
        enable=CockpitButton.builder(Component.literal("Enable Swarm API"),b->CommunityClient.INSTANCE.enable()).bounds(x,height-74,w,18).build();addRenderableWidget(enable);
        disable=CockpitButton.builder(Component.literal("Disable Swarm API"),b->CommunityClient.INSTANCE.disable()).bounds(x,height-50,w,18).build();addRenderableWidget(disable);
        addRenderableWidget(CockpitButton.builder(Component.literal("Back"),b->onClose()).bounds(x,height-26,w,18).build());
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){
        int w=Math.min(500,width-24),x=(width-w)/2;g.fill(0,0,width,height,0xF0050910);
        var view=CommunityClient.INSTANCE.view();g.text(font,"SWARM API",x,14,UiTheme.GOLD);
        int y=36;
        for(String text:new String[]{"Contribute market observations and receive shared discovery guidance.","Uploads public listings, orders, rival activity and payment identities to gonuts.at.","Shared history guides discovery; live checks still authorize trades.",view.path("status").asText(),"Accepted: "+view.path("accepted").asLong()+"  Rejected: "+view.path("rejected").asLong(),"Discovery guidance: "+(view.path("guidanceEnabled").asBoolean()?"opted in":"off")}){
            for(var line:font.split(Component.literal(text),w)){if(y>=height-84)break;g.text(font,line,x,y,UiTheme.TEXT);y+=12;}y+=8;
        }
        enable.active=(!view.path("enabled").asBoolean()||!view.path("guidanceEnabled").asBoolean())&&dev.doughbay.fabric.hosted.HostedDashboardClient.INSTANCE.view().linked();
        disable.active=view.path("enabled").asBoolean()||view.path("enrolled").asBoolean();super.extractRenderState(g,mx,my,delta);
    }
    @Override public void onClose(){minecraft.gui.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
}
