package dev.doughbay.fabric;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.*;
import java.util.function.Consumer;

/** Guided local report. Reading or navigating never applies suggested settings. */
final class AnalyzerPanel {
    private int x,y,w,bottom,index,scroll,maxScroll;
    private String message="";
    private boolean showCapital;
    private AbstractWidget previous,next,save,copy;
    private final CapitalCharts capitalCharts=new CapitalCharts();
    boolean scroll(double mx,double my,double delta){if(mx<x||mx>x+w||my<y+77||my>bottom-30)return false;scroll=Math.max(0,Math.min(maxScroll,scroll-(int)Math.signum(delta)*26));return true;}
    void init(int x,int y,int w,int bottom,Consumer<AbstractWidget> add,Runnable rebuild,Screen parent){
        this.x=x;this.y=y;this.w=w;this.bottom=bottom;
        int half=(w-6)/2,third=(w-12)/3;
        add.accept(CockpitButton.builder(Component.literal("Analyze local data"),b->{message="";SettingsAnalyzer.refresh();}).bounds(x,y+26,half,20).build());
        add.accept(CockpitButton.builder(Component.literal(showCapital?"Back to report":"Capital charts"),b->{showCapital=!showCapital;scroll=0;rebuild.run();}).bounds(x+half+6,y+26,half,20).build());
        previous=CockpitButton.builder(Component.literal("Previous"),b->{if(showCapital)capitalCharts.step(-1);else index=Math.max(0,index-1);scroll=0;}).bounds(x,y+50,third,20).build();add.accept(previous);
        add.accept(CockpitButton.builder(Component.literal(showCapital?capitalCharts.rangeLabel():"Overview"),b->{if(showCapital)capitalCharts.range();else index=0;scroll=0;rebuild.run();}).bounds(x+third+6,y+50,third,20).build());
        next=CockpitButton.builder(Component.literal("Next"),b->{if(showCapital)capitalCharts.step(1);else {var r=SettingsAnalyzer.report();if(r!=null)index=Math.min(r.findings().size(),index+1);}scroll=0;}).bounds(x+2*(third+6),y+50,third,20).build();add.accept(next);
        save=CockpitButton.builder(Component.literal("Review suggested preset"),b->{var r=SettingsAnalyzer.report();if(r!=null&&(!r.numbers().isEmpty()||!r.lists().isEmpty()))Minecraft.getInstance().setScreen(new PresetsScreen(parent,r.proposedCode()));else message="No settings change suggested. Review the findings for manual next steps.";}).bounds(x,bottom-23,Math.max(80,w-110),20).build();
        save.setTooltip(Tooltip.create(Component.literal("Open a separate preset review. Nothing is applied by the Analyzer.")));add.accept(save);
        copy=CockpitButton.builder(Component.literal("Copy report"),b->{var r=SettingsAnalyzer.report();if(r!=null)try{Minecraft.getInstance().keyboardHandler.setClipboard(new com.fasterxml.jackson.databind.ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(r));message="Report copied. Review it before sharing.";}catch(Exception ignored){message="Could not copy the report. Your settings are unchanged.";}}).bounds(x+w-104,bottom-23,104,20).build();add.accept(copy);
    }
    void render(GuiGraphics g,Font font,int mx,int my,Charts.Pending tooltip){
        g.drawString(font,UiTheme.fit(font,"UNDERSTAND YOUR TRADING",w),x,y,UiTheme.GOLD);
        g.drawString(font,UiTheme.fit(font,"Analyze > review findings > choose a small experiment",w),x,y+13,UiTheme.SECONDARY);
        var r=SettingsAnalyzer.report();
        previous.active=showCapital||(r!=null&&index>0);next.active=showCapital||(r!=null&&index<r.findings().size());
        save.active=r!=null&&(!r.numbers().isEmpty()||!r.lists().isEmpty());copy.active=r!=null;
        int top=y+78,end=bottom-32;if(end<=top)return;
        if(showCapital&&r!=null){capitalCharts.render(g,font,r,x,top,w,end,mx,my,tooltip);return;}
        g.enableScissor(x,top,x+w,end);int row=top-scroll;
        g.drawString(font,UiTheme.fit(font,message.isEmpty()?SettingsAnalyzer.status():message,w-10),x,row,UiTheme.SECONDARY);row+=20;
        if(r==null){
            row=card(g,font,row,"1  START HERE","Choose Analyze local data above. The report reads your local trading history and recent observations.",UiTheme.GOLD);
            row=card(g,font,row,"2  UNDERSTAND THE RESULT","Review your baseline, then use Next for each finding. Each one explains the evidence, a next step and the limits of any estimate.",UiTheme.GOLD);
            row=card(g,font,row,"3  YOU CHOOSE WHAT CHANGES","No setting changes automatically. If a supported change is suggested, Review suggested preset opens a separate review.",UiTheme.GOOD);
        }else{
            index=Math.min(index,r.findings().size());
            if(index==0){
                row=card(g,font,row,"YOUR NEXT STEP",r.findings().isEmpty()?"No findings in this snapshot. Keep collecting completed sales, then analyze again. This is not a guarantee of healthy or profitable trading.":r.findings().size()+" finding(s) to review. Choose Next to open the first. Start with the evidence before changing a setting.",UiTheme.TEAL);
                var baseline=r.baseline();int columns=w>=420?2:1,cw=(w-7-(columns-1)*7)/columns;
                for(int i=0;i<baseline.size();i+=columns){int height=0;
                    for(int c=0;c<columns&&i+c<baseline.size();c++)height=Math.max(height,29+font.split(Component.literal(baseline.get(i+c)),Math.max(30,cw-24)).size()*(font.lineHeight+3));
                    for(int c=0;c<columns&&i+c<baseline.size();c++)cardAt(g,font,x+c*(cw+7),row,cw,height,baselineTitle(baseline.get(i+c)),baseline.get(i+c),UiTheme.GOLD);
                    row+=height+7;
                }
            }else{
                var f=r.findings().get(index-1);
                row=card(g,font,row,"FINDING "+index+" OF "+r.findings().size(),f.title(),UiTheme.GOLD);
                row=card(g,font,row,"WHAT WE OBSERVED",f.evidence(),UiTheme.TEAL);
                row=card(g,font,row,"WHAT YOU CAN TRY",f.suggestion(),UiTheme.GOOD);
                row=card(g,font,row,"WHAT THE ESTIMATE MEANS",f.estimate(),UiTheme.WARN);
                row=card(g,font,row,"AFTER REVIEWING",save.active?"Use Review suggested preset to inspect supported changes before applying. Keep the baseline so you can compare actual results.":"This finding has no automatic settings change. Use its next step as guidance, then analyze again after collecting more evidence.",UiTheme.MUTED);
            }
        }
        maxScroll=Math.max(0,row+scroll-end);scroll=Math.min(scroll,maxScroll);g.disableScissor();
        if(maxScroll>0){int track=end-top,thumb=Math.max(16,track*track/(track+maxScroll));int sy=top+(track-thumb)*scroll/Math.max(1,maxScroll);g.fill(x+w-3,sy,x+w,sy+thumb,UiTheme.TEAL);}
    }
    private static String baselineTitle(String line){
        if(line.startsWith("24h local ledger"))return "COMPLETED SALES";
        if(line.startsWith("24h wall-clock"))return "DAILY AVERAGE";
        if(line.startsWith("This account bank"))return "CASH & PURCHASE LIMIT";
        if(line.contains("active client"))return "CLIENTS & CAPACITY";
        if(line.startsWith("This account occupied"))return "LISTING SLOTS";
        if(line.contains("active time"))return "RECORDED ACTIVITY";
        if(line.startsWith("Explicit tracked"))return "TRACKED MARKETS";
        if(line.startsWith("Follow observations"))return "PUBLIC OBSERVATIONS";
        return "LOCAL BASELINE";
    }
    private int card(GuiGraphics g,Font font,int row,String title,String body,int accent){
        var lines=font.split(Component.literal(body),Math.max(30,w-30));int h=29+lines.size()*(font.lineHeight+3);
        cardAt(g,font,x,row,w-7,h,title,body,accent);return row+h+7;
    }
    private void cardAt(GuiGraphics g,Font font,int left,int row,int width,int h,String title,String body,int accent){
        UiTheme.panel(g,left,row,width,h,UiTheme.INSET);g.fill(left+1,row+9,left+3,row+h-9,accent);
        g.drawString(font,UiTheme.fit(font,title,width-24),left+12,row+9,accent);int py=row+24;
        for(var line:font.split(Component.literal(body),Math.max(30,width-24))){g.drawString(font,line,left+12,py,UiTheme.TEXT);py+=font.lineHeight+3;}
    }
}
