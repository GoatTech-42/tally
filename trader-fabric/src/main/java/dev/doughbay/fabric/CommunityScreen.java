package dev.doughbay.fabric;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/** Legacy entry points share the same linking and Swarm information panel. */
public final class CommunityScreen extends GoNutsScreen {
    private final Screen parent;
    private final SwarmPanel panel = new SwarmPanel();
    public CommunityScreen(Screen parent){super(Component.literal("Swarm"));this.parent=parent;}
    @Override protected void init(){
        int w=Math.min(600,width-24),x=(width-w)/2;
        panel.init(font,x,24,w,height-40,this::addRenderableWidget);
        addRenderableWidget(CockpitButton.builder(Component.literal("Back"),b->onClose()).bounds(x,height-26,w,18).build());
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){
        g.fill(0,0,width,height,0xF0050505);panel.render(g,font);super.extractRenderState(g,mx,my,delta);
    }
    @Override public boolean mouseScrolled(double mx,double my,double dx,double dy){
        return panel.scroll(mx,my,dy)||super.mouseScrolled(mx,my,dx,dy);
    }
    @Override public void onClose(){minecraft.gui.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
}
