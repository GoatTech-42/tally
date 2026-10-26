package dev.doughbay.fabric;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.*;

/** Saved settings manager. Applying a copy never modifies that saved copy. */
public final class PresetsScreen extends GoNutsScreen {
    private final Screen parent;
    private final String proposal;
    private final boolean itemOnly;
    private String selected="", nameDraft="", message="", confirm="";
    private int page;
    private List<SavedPresets.Entry> entries=List.of();
    public PresetsScreen(Screen parent){this(parent,"");}
    public PresetsScreen(Screen parent,String proposal){this(parent,proposal,false);}
    public static PresetsScreen itemLoadouts(Screen parent){return new PresetsScreen(parent,"",true);}
    private PresetsScreen(Screen parent,String proposal,boolean itemOnly){super(Component.literal(itemOnly?"Item loadouts":"Saved presets"));this.parent=parent;this.proposal=proposal;this.itemOnly=itemOnly;}
    private SavedPresets store(){return itemOnly?SavedPresets.items():SavedPresets.current();}
    private String capture(){return itemOnly?SettingsCode.exportItemLoadout():SettingsCode.exportPreset(Map.of(),Map.of());}
    @Override protected void init(){
        int w=Math.min(640,width-30),x=(width-w)/2,y=45;
        try{entries=store().entries();}catch(Exception e){message=e.getMessage();entries=List.of();}
        int count=Math.max(1,(height-195)/23);page=Math.min(page,Math.max(0,(entries.size()-1)/count));
        for(int i=page*count;i<Math.min(entries.size(),(page+1)*count);i++){
            var entry=entries.get(i); int rowY=y+(i-page*count)*23;
            addRenderableWidget(CockpitButton.builder(Component.literal((selected.equals(entry.id())?"> ":"")+entry.name()),b->{selected=entry.id();nameDraft=entry.name();confirm="";rebuildWidgets();}).bounds(x,rowY,w,20).build());
        }
        int footer=height-117;
        addRenderableWidget(CockpitButton.builder(Component.literal("Previous"),b->{page=Math.max(0,page-1);rebuildWidgets();}).bounds(x,footer-24,78,20).build());
        addRenderableWidget(CockpitButton.builder(Component.literal("Next"),b->{if((page+1)*count<entries.size())page++;rebuildWidgets();}).bounds(x+82,footer-24,65,20).build());
        var name=new EditBox(font,x,footer,w,20,Component.literal("Preset name"));name.setHint(Component.literal("Preset name"));name.setMaxLength(48);name.setValue(nameDraft);name.setResponder(v->nameDraft=v);addRenderableWidget(name);
        int bw=(w-12)/4;
        action(x,footer+24,bw,proposal.isEmpty()?"Save current as new":"Save proposal as new",()->{
            var saved=store().save("",nameDraft,proposal.isEmpty()?capture():proposal);selected=saved.id();message="Saved a separate copy; settings unchanged";
        });
        action(x+bw+4,footer+24,bw,"Apply selected",()->{
            var result=itemOnly?SettingsCode.applyItemLoadout(store().get(selected).code()):SettingsCode.apply(store().get(selected).code());
            if(!result.ok())throw new java.io.IOException(result.error());message=itemOnly?"Applied item selections; saved loadout kept":"Applied "+result.applied()+" settings; saved preset kept";
        });
        action(x+2*(bw+4),footer+24,bw,"Copy code",()->{minecraft.keyboardHandler.setClipboard(store().get(selected).code());message="Settings code copied";});
        action(x+3*(bw+4),footer+24,bw,"Rename",()->{var saved=store().get(selected);store().save(selected,nameDraft,saved.code());message="Preset renamed; saved settings kept";});
        int half=(w-4)/2;
        action(x,footer+48,half,confirm.equals("replace")?"Confirm replace with current":"Replace selected with current",()->{
            if(!confirm.equals("replace")){confirm="replace";message="Press again to overwrite the selected preset";return;}
            store().save(selected,nameDraft,capture());confirm="";message="Saved current settings over selected preset";
        });
        action(x+half+4,footer+48,half,confirm.equals("delete")?"Confirm delete":"Delete selected",()->{
            if(!confirm.equals("delete")){confirm="delete";message="Press again to delete the selected preset";return;}
            store().delete(selected);selected="";confirm="";message="Preset deleted; current settings unchanged";
        });
        addRenderableWidget(CockpitButton.builder(Component.literal("Done"),b->onClose()).bounds(width/2-45,height-25,90,20).build());
    }
    private interface Action{void run()throws Exception;}
    private void action(int x,int y,int w,String label,Action action){addRenderableWidget(CockpitButton.builder(Component.literal(label),b->{try{action.run();}catch(Exception e){message=e.getMessage()==null?"Could not change presets":e.getMessage();}rebuildWidgets();}).bounds(x,y,w,20).build());}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){
        g.fill(0,0,width,height,0xFA0C121B);g.text(font,itemOnly?"ITEM LOADOUTS":"SAVED PRESETS",15,12,UiTheme.GOLD);
        g.text(font,UiTheme.fit(font,itemOnly?"Save included, excluded and tracked items. Applying changes only these item selections.":"Save a baseline, experiment, then apply a saved copy to switch back. Slot counts stay account-local.",width-30),15,27,UiTheme.SECONDARY);
        g.text(font,UiTheme.fit(font,message,width-30),15,height-43,UiTheme.SECONDARY);super.extractRenderState(g,mx,my,delta);
    }
    @Override public void onClose(){minecraft.gui.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
}
