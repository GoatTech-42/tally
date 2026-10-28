package dev.doughbay.fabric;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import java.net.URI;

/** Shown only when the player opens the mod; never interrupts a trade or opens the game. */
final class PublicSupportNoticeScreen extends GoNutsScreen {
    private static boolean shown;
    private final Screen next;
    private PublicSupportNoticeScreen(Screen next){super(Component.literal("Tally - upstream support ended"));this.next=next;}
    static Screen firstOpen(Screen next){if(shown)return next;shown=true;return new PublicSupportNoticeScreen(next);}
    @Override protected void init(){
        DoughBayClient.automationSessionController().setPanelOpen(true);
        int w=Math.max(100,Math.min(640,width-32)),x=(width-w)/2,half=(w-8)/2;
        addRenderableWidget(CockpitButton.builder(Component.literal("Tally source code"),b->Util.getPlatform().openUri(URI.create(ProjectStatus.SOURCE))).bounds(x,height-40,half,24).build());
        addRenderableWidget(CockpitButton.builder(Component.literal("Continue to local Tally"),b->onClose()).bounds(x+half+8,height-40,half,24).build());
    }
    @Override public void extractRenderState(GuiGraphics g,int mx,int my,float delta){
        g.fill(0,0,width,height,UiTheme.BACKGROUND);
        int w=Math.max(100,Math.min(640,width-32)),x=(width-w)/2,y=24;
        for(var line:font.split(Component.literal("UPSTREAM SUPPORT HAS ENDED"),w)){g.text(font,line,x,y,UiTheme.GOLD);y+=15;}
        y+=14;
        String text=ProjectStatus.NOTICE+"\n\nSwarm, hosted Discord sign-in, the remote dashboard, bug reports and diagnostic uploads are retired. "
                +"There is no public support or automatic patch worker.\n\nLocal trading, your settings, trade history, ownership records and recovery safeguards remain. "
                +"Existing upstream issues are not claimed fixed. Tally continues the project as a community-maintained fork.";
        for(var line:font.split(Component.literal(text),w)){g.text(font,line,x,y,UiTheme.TEXT);y+=12;}
        super.extractRenderState(g,mx,my,delta);
    }
    @Override public void onClose(){minecraft.gui.setScreen(next);}
    @Override public void removed(){DoughBayClient.automationSessionController().setPanelOpen(false);super.removed();}
    @Override public boolean isPauseScreen(){return false;}
}
