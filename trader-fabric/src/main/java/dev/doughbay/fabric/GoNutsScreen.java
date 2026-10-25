package dev.doughbay.fabric;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Native screen coordinates, text, widgets, scissors and input share one temporary scale.
 * Minecraft's saved GUI option is never edited. Leaving a mod menu restores its current value.
 */
public abstract class GoNutsScreen extends Screen {
    protected GoNutsScreen(Component title){super(title);}
    private static boolean reportButtonRegistered;
    private int reportFullHeight;
    private net.minecraft.client.gui.components.Button reportButton;
    static void registerReportButton(){
        if(reportButtonRegistered)return;reportButtonRegistered=true;
        net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.AFTER_INIT.register((client,screen,w,h)->{
            if(screen instanceof GoNutsScreen own)own.addReportButton();
        });
    }
    /** Called only at native screen initialization/resize; never changes the saved GUI option. */
    public final int reserveReportSpace(int fullHeight){
        reportFullHeight=Math.max(1,fullHeight);
        return Math.max(1,reportFullHeight-40);
    }
    @Override protected void rebuildWidgets(){super.rebuildWidgets();addReportButton();}
    private void addReportButton(){
        if(reportFullHeight<=height || reportButton!=null&&children().contains(reportButton))return;
        int w=Math.max(1,Math.min(280,width-24));
        reportButton=CockpitButton.builder(Component.literal(this instanceof BugReportScreen?"BUG REPORT OPEN":"REPORT BUG"),b->{
            if(!(this instanceof BugReportScreen))minecraft.gui.setScreen(new BugReportScreen(this));
        }).bounds((width-w)/2,height+5,w,30).build();
        ((CockpitButton)reportButton).primary();reportButton.active=!(this instanceof BugReportScreen);
        addRenderableWidget(reportButton);
    }
    @Override public void extractRenderState(net.minecraft.client.gui.GuiGraphicsExtractor g,int mx,int my,float delta){
        if(reportFullHeight>height){g.fill(0,height,width,reportFullHeight,UiTheme.BACKGROUND);g.fill(0,height,width,height+1,UiTheme.EDGE);}
        super.extractRenderState(g,mx,my,delta);
    }
    private static void fit(){
        var mc=Minecraft.getInstance();if(mc==null||mc.getWindow()==null)return;
        var window=mc.getWindow();window.setGuiScale(GoNutsUiScale.preferred(window.getWidth(),window.getHeight()));
    }
    @Override public void added(){super.added();fit();}
    @Override public void resize(int width,int height){
        var mc=Minecraft.getInstance();
        if(mc==null||mc.getWindow()==null){super.resize(width,reserveReportSpace(height));return;}
        fit();super.resize(mc.getWindow().getGuiScaledWidth(),reserveReportSpace(mc.getWindow().getGuiScaledHeight()));
    }
    @Override public void removed(){
        var mc=Minecraft.getInstance();
        if(mc!=null&&mc.getWindow()!=null&&mc.options!=null){
            var window=mc.getWindow();window.setGuiScale(window.calculateScale(mc.options.guiScale().get(),mc.isEnforceUnicode()));
        }
        super.removed();
    }
}
