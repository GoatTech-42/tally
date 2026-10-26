package dev.doughbay.fabric;

import java.util.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Manual item preferences only; opening or changing this screen never starts trading. */
final class ItemPriorityScreen extends GoNutsScreen {
    private final Screen parent;
    private final List<String> selected;
    ItemPriorityScreen(Screen parent,Collection<String> selected){super(Component.literal("Item priority"));this.parent=parent;this.selected=List.copyOf(selected);}
    private int bodyWidth(){return Math.max(1,Math.min(440,width-24));}
    private int left(){return (width-bodyWidth())/2;}
    private int top(){return Math.max(8,(height-174)/2);}
    @Override protected void init(){
        DoughBayClient.automationSessionController().setPanelOpen(true);
        var levels=List.of(ItemPriorities.Level.HIGH,ItemPriorities.Level.STANDARD,ItemPriorities.Level.LOW);
        for(int i=0;i<levels.size();i++){
            var level=levels.get(i);
            var button=CockpitButton.builder(Component.literal(level.label()),b->{ItemPriorities.set(selected,level);onClose();})
                .bounds(left(),top()+58+i*24,bodyWidth(),20).build();
            button.active=!selected.isEmpty();addRenderableWidget(button);
        }
        addRenderableWidget(CockpitButton.builder(Component.literal("Back to Items"),b->onClose())
            .bounds(left(),Math.min(height-24,top()+140),bodyWidth(),20).build());
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float dt){
        g.fill(0,0,width,height,UiTheme.BACKGROUND);int x=left(),y=top(),w=bodyWidth();
        g.text(font,"ITEM PRIORITY",x,y,UiTheme.GOLD);
        var counts=new EnumMap<ItemPriorities.Level,Integer>(ItemPriorities.Level.class);
        for(String item:selected)counts.merge(ItemPriorities.level(item),1,Integer::sum);
        String summary=selected.isEmpty()?"Tick item icons in Items before choosing a priority.":selected.size()+" selected · High "+counts.getOrDefault(ItemPriorities.Level.HIGH,0)+" · Standard "+counts.getOrDefault(ItemPriorities.Level.STANDARD,0)+" · Low "+counts.getOrDefault(ItemPriorities.Level.LOW,0);
        g.text(font,UiTheme.fit(font,summary,w),x,y+18,UiTheme.TEXT);
        int line=y+33;
        for(var text:font.split(Component.literal("New trades only. Limits and safety checks still apply."),w)){g.text(font,text,x,line,UiTheme.SECONDARY);line+=10;}
        super.extractRenderState(g,mx,my,dt);
    }
    @Override public void onClose(){minecraft.gui.setScreen(parent);}
    @Override public void removed(){DoughBayClient.automationSessionController().setPanelOpen(false);super.removed();}
    @Override public boolean isPauseScreen(){return false;}
}
