package dev.doughbay.fabric;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/** Reopenable lessons. Opening a target page delegates navigation only. */
final class TutorialScreen extends GoNutsScreen {
    private final DoughBayScreen parent;
    private final Screen home;
    private Tutorials.Course course;
    private int step,scroll,maxScroll;
    private final java.util.Map<net.minecraft.client.gui.components.Button,Integer> libraryButtons=new java.util.LinkedHashMap<>();
    TutorialScreen(DoughBayScreen parent,boolean firstRun){
        this(parent,parent,firstRun);
    }
    TutorialScreen(DoughBayScreen parent,Screen home,boolean firstRun){
        super(Component.literal("GoNuts tutorials"));this.parent=parent;this.home=home;
        if(firstRun)course=Tutorials.COURSES.getFirst();
    }
    private int left(){return Math.max(12,(width-Math.min(680,width-24))/2);}
    private int bodyWidth(){return Math.min(680,width-24);}
    @Override protected void init(){
        DoughBayClient.automationSessionController().setPanelOpen(true);
        libraryButtons.clear();
        int x=left(),w=bodyWidth();
        if(course==null){
            for(int i=0;i<Tutorials.COURSES.size();i++){
                var item=Tutorials.COURSES.get(i);int col=i%2,row=i/2,bw=(w-8)/2;
                var button=CockpitButton.builder(Component.literal(item.title()),b->{
                    if(item.id().equals("setup")){minecraft.gui.setScreen(new QuickStartScreen(parent));return;}
                    course=item;step=scroll=0;rebuildWidgets();})
                    .bounds(x+col*(bw+8),62+row*28,bw,23).build();
                libraryButtons.put(button,62+row*28);addRenderableWidget(button);
            }
        }else{
            int bw=(w-12)/3,y=height-56;
            var back=CockpitButton.builder(Component.literal("Previous"),b->{step--;scroll=0;rebuildWidgets();}).bounds(x,y,bw,22).build();
            back.active=step>0;addRenderableWidget(back);
            var lesson=course.steps().get(step);
            addRenderableWidget(CockpitButton.builder(Component.literal(lesson.action()),b->parent.openTutorialTarget(lesson.target(),this))
                .bounds(x+bw+6,y,bw,22).build());
            addRenderableWidget(CockpitButton.builder(Component.literal(step+1==course.steps().size()?"Finish":"Next"),b->{
                if(step+1==course.steps().size()){TutorialProgress.completed(course.id());course=null;step=scroll=0;}
                else{step++;scroll=0;}rebuildWidgets();
            }).bounds(x+2*(bw+6),y,bw,22).build());
        }
        addRenderableWidget(CockpitButton.builder(Component.literal(course==null?(home instanceof QuickStartScreen?"Back to setup":"Back to GoNuts"):"All tutorials"),b->{
            if(course==null)onClose();else{course=null;step=scroll=0;rebuildWidgets();}
        }).bounds(x,height-28,(w-6)/2,20).build());
        if(course!=null)addRenderableWidget(CockpitButton.builder(Component.literal("Skip / return later"),b->onClose())
            .bounds(x+(w+6)/2,height-28,(w-6)/2,20).build());
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){
        g.fill(0,0,width,height,0xF0050505);int x=left(),w=bodyWidth();
        g.text(font,UiTheme.fit(font,course==null?"TUTORIALS · CHOOSE A GUIDE":course.title()+" · "+(step+1)+" / "+course.steps().size(),w),x,18,UiTheme.GOLD);
        if(course==null){
            maxScroll=Math.max(0,62+((Tutorials.COURSES.size()+1)/2)*28-(height-40));
            libraryButtons.forEach((button,y)->{button.setY(y-scroll);button.visible=button.getY()>=60&&button.getY()+23<=height-40;});
            g.text(font,UiTheme.fit(font,"Learn at your own pace. Guides open pages; you choose settings and when to trade.",w),x,38,UiTheme.TEXT);
        }else{
            var lesson=course.steps().get(step);int top=42,bottom=height-68,y=top-scroll;
            g.enableScissor(x,top,x+w,bottom);
            for(var line:font.split(Component.literal(lesson.title()),w)){g.text(font,line,x,y,UiTheme.GOLD);y+=14;}
            y+=10;
            for(var line:font.split(Component.literal(lesson.explanation()),w)){g.text(font,line,x,y,UiTheme.TEXT);y+=14;}
            y+=12;
            for(var line:font.split(Component.literal("Open the page below to review its controls. Use Return to tutorial on that page when you are ready to continue. No setting changes automatically."),w)){
                g.text(font,line,x,y,UiTheme.MUTED);y+=14;
            }
            maxScroll=Math.max(0,y+scroll-bottom);g.disableScissor();
        }
        super.extractRenderState(g,mx,my,delta);
    }
    @Override public boolean mouseScrolled(double mx,double my,double dx,double dy){
        scroll=Math.max(0,Math.min(maxScroll,scroll-(int)(dy*28)));return true;
    }
    @Override public void onClose(){parent.endTutorialNavigation();minecraft.gui.setScreen(home);}
    @Override public void removed(){DoughBayClient.automationSessionController().setPanelOpen(false);super.removed();}
    @Override public boolean isPauseScreen(){return false;}
}
