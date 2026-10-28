package dev.doughbay.fabric;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
public final class ProxyScreen extends GoNutsScreen {
    private final Screen parent;
    private final ProxyPanel panel=new ProxyPanel();
    public ProxyScreen(Screen parent){super(Component.literal("GoNuts proxies"));this.parent=parent;}
    @Override protected void init(){
        int w=Math.min(620,width-30);
        panel.init(font,(width-w)/2,40,w,this::addRenderableWidget,this::rebuildWidgets);
        addRenderableWidget(CockpitButton.builder(Component.literal("Done"),b->onClose()).bounds(width/2-50,height-26,100,20).build());
    }
    @Override public void render(GuiGraphics g,int mx,int my,float delta){
        g.fill(0,0,width,height,UiTheme.BACKGROUND);g.drawString(font,"TALLY · PROXIES",15,15,UiTheme.GOLD);
        panel.render(g,font);super.render(g,mx,my,delta);
    }
    @Override public void onClose(){minecraft.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
}
