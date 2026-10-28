package dev.doughbay.fabric;

import dev.doughbay.fabric.hosted.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import java.net.URI;
import java.util.*;
import java.util.function.Consumer;

/** One discoverable home for pairing, contribution and current Swarm results. */
final class SwarmPanel {
    private int x, top, width, bottom, scroll, contentHeight, extra;
    private static final String INTRO="Find markets sooner with other bots' recent observations and fewer repeated discovery scans. Link for remote control and Swarm.";
    private String codeDraft = "", nameDraft = "GoNuts client", note = "";
    private boolean previousLinked;
    private EditBox code, name;
    private Button link, reconnect, retry, unlink, paste, website, copyWebsite, guide;
    private final DashboardWebsiteAccess websiteAccess;
    SwarmPanel() {
        this(new DashboardWebsiteAccess(uri -> Util.getPlatform().openUri(uri),
                value -> Minecraft.getInstance().keyboardHandler.setClipboard(value)));
    }
    SwarmPanel(DashboardWebsiteAccess websiteAccess) { this.websiteAccess = websiteAccess; }
    private final Map<AbstractWidget,Integer> widgets = new IdentityHashMap<>();
    void init(Font font, int x, int top, int width, int bottom, Consumer<AbstractWidget> add) {
        this.x=x;this.top=top;this.width=width;this.bottom=bottom;widgets.clear();
    }
    private int maxScroll(){return Math.max(0,contentHeight-(bottom-top));}
    boolean scroll(double mx,double my,double delta){
        return false;
    }
    private int paragraph(GuiGraphics g,Font font,String text,int y,int color){
        for(var line:font.split(Component.literal(text),Math.max(40,width-16))){g.drawString(font,line,x+8,y,color);y+=12;}
        return y+8;
    }
    void render(GuiGraphics g,Font font){
        paragraph(g,font,ProjectStatus.NOTICE,top+8,UiTheme.TEXT);
    }
    static boolean validCode(String value){
        try{DashboardProtocol.code(value);return true;}catch(IllegalArgumentException e){return false;}
    }
}
