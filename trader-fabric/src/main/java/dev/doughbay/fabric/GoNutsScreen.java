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
    private static void fit(){
        var mc=Minecraft.getInstance();if(mc==null||mc.getWindow()==null)return;
        var window=mc.getWindow();window.setGuiScale(GoNutsUiScale.preferred(window.getWidth(),window.getHeight()));
    }
    @Override public void added(){super.added();fit();}
    @Override public void resize(int width,int height){
        var mc=Minecraft.getInstance();
        if(mc==null||mc.getWindow()==null){super.resize(width,height);return;}
        fit();super.resize(mc.getWindow().getGuiScaledWidth(),mc.getWindow().getGuiScaledHeight());
    }
    @Override public void removed(){
        var mc=Minecraft.getInstance();
        if(mc!=null&&mc.getWindow()!=null&&mc.options!=null){
            var window=mc.getWindow();window.setGuiScale(window.calculateScale(mc.options.guiScale().get(),mc.isEnforceUnicode()));
        }
        super.removed();
    }
}
