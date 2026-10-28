package dev.doughbay.fabric;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Offered only during deliberate panel navigation; never takes over an active trading screen. */
final class HiveDiscoveryScreen extends GoNutsScreen {
    private final Screen parent;
    private int scroll,maxScroll;
    HiveDiscoveryScreen(Screen parent) { super(Component.literal("Another GoNuts account detected")); this.parent=parent; }
    @Override protected void init() {
        DoughBayClient.automationSessionController().setPanelOpen(true);
        int w=Math.min(480,width-24),x=(width-w)/2,y=height-86;
        addRenderableWidget(CockpitButton.builder(Component.literal("Review setup"),b->DoughBayClient.openHiveSetup()).bounds(x,y,w,22).build());
        addRenderableWidget(CockpitButton.builder(Component.literal("Not now"),b->onClose()).bounds(x,y+28,(w-6)/2,22).build());
        addRenderableWidget(CockpitButton.builder(Component.literal("Don't ask again"),b->{HiveDiscovery.dismissAll();onClose();}).bounds(x+(w+6)/2,y+28,(w-6)/2,22).build());
    }
    @Override public void extractRenderState(GuiGraphics g,int mx,int my,float dt) {
        g.fill(0,0,width,height,0xF0050505); int w=Math.min(480,width-24),x=(width-w)/2,y=24;
        for(var line:font.split(getTitle(),w)){g.drawString(font,line,x,y,UiTheme.GOLD);y+=14;}
        y+=14;int top=y;
        String text="Another running GoNuts account was detected on this computer. Would you like to review linking them into a hive?\n\nNothing has been linked or changed. Use an existing hive instead of creating a separate one. If both accounts already made separate hives, Review combining hives preserves their original ledgers and per-account settings. The import runs at startup after BOTH instances are closed. Review the requirements before confirming.\n\nThis notice appears once per newly detected account when you open GoNuts, never over a trade. Hive is local sharing; the community Swarm is separate.";
        g.enableScissor(x,top,x+w,Math.max(top,height-94));
        for(var line:font.split(Component.literal(text),w)){g.drawString(font,line,x,y-scroll,UiTheme.TEXT);y+=13;}
        maxScroll=Math.max(0,y-(height-94));
        g.disableScissor();super.extractRenderState(g,mx,my,dt);
    }
    @Override public void onClose(){minecraft.setScreen(parent);}
    @Override public boolean mouseScrolled(double mx,double my,double dx,double dy){scroll=Math.max(0,Math.min(maxScroll,scroll-(int)(dy*26)));return true;}
    @Override public void removed(){DoughBayClient.automationSessionController().setPanelOpen(false);super.removed();}
    @Override public boolean isPauseScreen(){return false;}
}
