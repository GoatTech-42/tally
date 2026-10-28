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
        // Public reporting was removed from the standalone release.
    }
    /** Called only at native screen initialization/resize; never changes the saved GUI option. */
    public final int reserveReportSpace(int fullHeight){
        reportFullHeight=Math.max(1,fullHeight);return reportFullHeight;
    }
    @Override protected void rebuildWidgets(){super.rebuildWidgets();addReportButton();}
    private void addReportButton(){
        // No reporting footer or reserved content space.
    }
    @Override public void extractRenderState(net.minecraft.client.gui.GuiGraphics g,int mx,int my,float delta){
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
