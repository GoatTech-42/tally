package dev.doughbay.fabric;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.GuiGraphics;
public final class BugReportScreen extends GoNutsScreen {
    private final Screen parent;
    public BugReportScreen(Screen parent){super(Component.literal("Public support retired"));this.parent=parent;}
    @Override protected void init(){addRenderableWidget(CockpitButton.builder(Component.literal("Back"),b->onClose()).bounds((width-100)/2,height-35,100,20).build());}
    @Override public void onClose(){minecraft.setScreen(parent);}
    @Override public void extractRenderState(GuiGraphics g,int mx,int my,float delta){
        int y=30;for(var line:font.split(Component.literal(ProjectStatus.NOTICE),Math.max(40,width-40))){g.drawString(font,line,20,y,UiTheme.TEXT);y+=12;}
        super.extractRenderState(g,mx,my,delta);
    }
}
