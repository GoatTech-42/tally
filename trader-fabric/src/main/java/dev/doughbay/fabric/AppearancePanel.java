package dev.doughbay.fabric;

import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;

/** Independent theme/character choices, with live state previews. */
final class AppearancePanel {
    private int x, y, width, bottom, scroll;
    private final java.util.Map<AbstractWidget,Integer> widgetOffsets = new java.util.IdentityHashMap<>();
    private Mascot.Face preview = Mascot.Face.IDLE;

    void init(int x, int y, int width, int bottom, Consumer<AbstractWidget> add, Runnable rebuild) {
        this.x=x; this.y=y; this.width=width; this.bottom=bottom; widgetOffsets.clear();
        Consumer<AbstractWidget> attach = widget -> { widgetOffsets.put(widget,widget.getY()-y);add.accept(widget); };
        int themeWidth=(width-30)/4;
        int i=0;
        for(var theme:Appearance.Theme.values()) {
            var button=CockpitButton.builder(Component.literal(label(theme.name())), b->{
                Appearance.choose(theme,Appearance.mascot()); rebuild.run();
            }).bounds(x+6+i++*(themeWidth+6),y+35,themeWidth,22).build();
            ((CockpitButton)button).navigation(Appearance.theme()==theme);
            attach.accept(button);
        }
        int card=(width-16)/3;
        int artSize=artSize();
        i=0;
        for(var character:Appearance.Character.values()) {
            var button=CockpitButton.builder(Component.literal(Appearance.mascot()==character?"Selected":"Choose "+label(character.name())),b->{
                Appearance.choose(Appearance.theme(),character); rebuild.run();
            }).bounds(x+i++*(card+8)+6,y+112+artSize,card-12,22).build();
            ((CockpitButton)button).navigation(Appearance.mascot()==character);
            attach.accept(button);
        }
        attach.accept(CockpitButton.builder(Component.literal("Preview: "+label(preview.name())), b->{
            preview=Mascot.Face.values()[(preview.ordinal()+1)%Mascot.Face.values().length];
            b.setMessage(Component.literal("Preview: "+label(preview.name())));
        }).bounds(x+6,y+150+artSize,Math.min(width-12,230),22).build());
    }

    private int artSize(){return Math.max(48,Math.min(112,bottom-y-200));}
    private int maxScroll(){return Math.max(0,artSize()+199-(bottom-y));}
    boolean scroll(double mx,double my,double delta){if(mx<x||mx>x+width||my<y||my>bottom)return false;scroll=Math.max(0,Math.min(maxScroll(),scroll-(int)(delta*24)));return true;}
    void render(GuiGraphics g, Font font) {
        int top=this.y;scroll=Math.min(scroll,maxScroll());int y=top-scroll;
        widgetOffsets.forEach((widget,offset)->{int wy=top+offset-scroll;widget.setY(wy);widget.visible=wy>=top&&wy+widget.getHeight()<=bottom;});
        g.enableScissor(x,top,x+width,bottom);
        g.drawString(font,"MAKE IT YOURS",x+6,y+8,UiTheme.GOLD);
        g.drawString(font,UiTheme.fit(font,"Pick a theme and a character. Changes save immediately.",width-12),x+6,y+22,UiTheme.SECONDARY);
        int card=(width-16)/3;
        int artSize=artSize();
        int i=0;
        for(var character:Appearance.Character.values()) {
            int left=x+i++*(card+8);
            UiTheme.panel(g,left,y+72,card,artSize+70,UiTheme.SURFACE);
            g.drawCenteredString(font,label(character.name()),left+card/2,y+82,UiTheme.GOLD);
            Mascot.drawCharacter(g,preview,left+(card-artSize)/2,y+96,artSize,character);
        }
        g.drawString(font,UiTheme.fit(font,"Animations follow activity; this preview never starts the bot.",width-12),x+6,y+181+artSize,UiTheme.MUTED);
        g.disableScissor();
    }

    private static String label(String value) {
        return value.substring(0,1)+value.substring(1).toLowerCase(java.util.Locale.ROOT);
    }
}
