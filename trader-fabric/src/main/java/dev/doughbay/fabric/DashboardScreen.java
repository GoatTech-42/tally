package dev.doughbay.fabric;

import dev.doughbay.fabric.hosted.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

/** Explicit pairing and a non-secret status view; relay connection never pauses trading. */
public final class DashboardScreen extends GoNutsScreen {
    private final Screen parent;private EditBox code,name;
    private String codeDraft="",nameDraft="GoNuts client";
    private net.minecraft.client.gui.components.Button link,unlink,retry;
    public DashboardScreen(Screen parent){super(Component.literal("Hosted Dashboard"));this.parent=parent;}
    private int left(){return (width-Math.min(500,width-24))/2;}private int panelWidth(){return Math.min(500,width-24);}
    private boolean compact(){return height<340;}
    private int websiteY(){return compact()?34:48;}
    private int codeY(){return compact()?70:94;}
    private int nameY(){return compact()?106:135;}
    private int linkY(){return compact()?136:167;}
    private int controlsY(){return compact()?164:197;}
    @Override protected void init(){
        DoughBayClient.automationSessionController().setPanelOpen(true);
        int x=left()+12,w=panelWidth()-24;
        if (!ProjectStatus.publicServicesEnabled()) {
            // The hosted dashboard and pairing went away with the public
            // services; show why instead of controls that can only fail.
            addRenderableWidget(CockpitButton.builder(Component.literal("Swarm info (retired service)"),b->minecraft.gui.setScreen(new CommunityScreen(this))).bounds(x,height-48,w,18).build());
            addRenderableWidget(CockpitButton.builder(Component.literal("Back"),b->onClose()).bounds(x,height-26,w,18).build());
            return;
        }
        addRenderableWidget(CockpitButton.builder(Component.literal("Open dashboard website"),b->Util.getPlatform().openUri(DashboardProtocol.PAGE)).bounds(x,websiteY(),w,18).build());
        code=new EditBox(font,x,codeY(),w,18,Component.literal("Pairing code"));code.setMaxLength(16);code.setValue(codeDraft);code.setHint(Component.literal("K7PQ-M3ZD"));code.setResponder(v->codeDraft=v);addRenderableWidget(code);
        name=new EditBox(font,x,nameY(),w,18,Component.literal("Client label"));name.setMaxLength(48);name.setValue(nameDraft);name.setResponder(v->nameDraft=v);addRenderableWidget(name);
        link=CockpitButton.builder(Component.literal("Link with code"),b->{HostedDashboardClient.INSTANCE.pair(codeDraft,nameDraft);codeDraft="";code.setValue("");}).bounds(x,linkY(),w,18).build();addRenderableWidget(link);
        retry=CockpitButton.builder(Component.literal("Reconnect"),b->HostedDashboardClient.INSTANCE.reconnect()).bounds(x,controlsY(),w/2-3,18).build();addRenderableWidget(retry);
        unlink=CockpitButton.builder(Component.literal("Unlink locally"),b->HostedDashboardClient.INSTANCE.unlink()).bounds(x+w/2+3,controlsY(),w/2-3,18).build();addRenderableWidget(unlink);
        addRenderableWidget(CockpitButton.builder(Component.literal("Swarm API"),b->minecraft.gui.setScreen(new CommunityScreen(this))).bounds(x,height-48,w,18).build());
        addRenderableWidget(CockpitButton.builder(Component.literal("Back"),b->onClose()).bounds(x,height-26,w,18).build());
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){
        int x=left()+12,w=panelWidth()-24;g.fill(0,0,width,height,0xF0050505);
        var view=HostedDashboardClient.INSTANCE.view();boolean idle=!view.busy();
        link.active=idle&&!view.linked()&&System.currentTimeMillis()>=view.retryAt();code.active=name.active=idle&&!view.linked();retry.active=unlink.active=idle&&view.linked();
        g.text(font,"HOSTED DASHBOARD",x,compact()?6:14,UiTheme.GOLD);
        if (!ProjectStatus.publicServicesEnabled()) {
            int ry=compact()?19:30;
            for(var line:font.split(Component.literal(ProjectStatus.RETIRED),w)){g.text(font,line,x,ry,UiTheme.SECONDARY);ry+=12;}
            ry+=6;
            for(var line:font.split(Component.literal("The hosted dashboard, pairing codes and Swarm linking were part of that retired service. Local trading, your Hive and your own settings still work."),w)){g.text(font,line,x,ry,UiTheme.MUTED);ry+=12;}
            super.extractRenderState(g,mx,my,delta);
            return;
        }
        g.text(font,UiTheme.fit(font,"Sign in on gonuts.at, choose Link client, then enter its code.",w),x,compact()?19:30,UiTheme.SECONDARY);
        g.text(font,"Pairing code (expires after five minutes)",x,codeY()-12,UiTheme.TEXT);
        g.text(font,"Client label (shown on your dashboard)",x,nameY()-12,UiTheme.TEXT);
        int y=compact()?194:230;
        if(view.linked()){g.text(font,UiTheme.fit(font,"Linked as: "+view.name(),w),x,y,UiTheme.TEXT);y+=17;}
        for(var line:font.split(Component.literal(view.status()),w)){if(y>=height-(compact()?56:76))break;g.text(font,line,x,y,view.online()?UiTheme.GOOD:UiTheme.SECONDARY);y+=12;}
        g.text(font,UiTheme.fit(font,"Linking joins Swarm: share public market data and receive discovery hints.",w),x,height-68,UiTheme.MUTED);
        if(!compact())g.text(font,UiTheme.fit(font,"Local unlink disconnects it; remove the entry on the website too.",w),x,height-54,UiTheme.MUTED);
        super.extractRenderState(g,mx,my,delta);
    }
    @Override public void onClose(){minecraft.gui.setScreen(parent);}
    @Override public void removed(){DoughBayClient.automationSessionController().setPanelOpen(false);super.removed();}
    @Override public boolean isPauseScreen(){return false;}
}
