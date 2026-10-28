package dev.doughbay.fabric;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/** Two-step setup: choose limits, then deliberately enable and start through existing safety gates. */
final class QuickStartScreen extends GoNutsScreen {
    private final DoughBayScreen parent;
    private final Screen home;
    private boolean review;
    private String trade,session,message="";
    private EditBox tradeField,sessionField;
    private net.minecraft.client.gui.components.Button primary;
    private int scroll,maxScroll;
    QuickStartScreen(DoughBayScreen parent){
        this(parent,parent);
    }
    QuickStartScreen(DoughBayScreen parent,Screen home){
        super(Component.literal("GoNuts quick start"));this.parent=parent;this.home=home;
        var config=DoughBayClient.activeConfig();
        trade=MoneyInput.compact(config==null?5_000_000:config.continuousMaxPurchasePrice());
        session=MoneyInput.compact(config==null?75_000_000:config.continuousMaxSessionSpend());
    }
    private int left(){return (width-bodyWidth())/2;}
    private int bodyWidth(){return Math.min(620,width-24);}
    @Override protected void init(){
        DoughBayClient.automationSessionController().setPanelOpen(true);
        int x=left(),w=bodyWidth();
        if(!review){
            int fw=(w-10)/2;
            tradeField=new EditBox(font,x,90,fw,22,Component.literal("Maximum per trade"));
            tradeField.setMaxLength(16);tradeField.setValue(trade);tradeField.setResponder(v->{trade=v;message="";});addRenderableWidget(tradeField);
            sessionField=new EditBox(font,x+fw+10,90,fw,22,Component.literal("Maximum session spend"));
            sessionField.setMaxLength(16);sessionField.setValue(session);sessionField.setResponder(v->{session=v;message="";});addRenderableWidget(sessionField);
            addRenderableWidget(CockpitButton.builder(Component.literal("Project status: retired services"),b->parent.openSetupTarget("swarm",this))
                .bounds(x,126,(w-6)/2,22).build());
            addRenderableWidget(CockpitButton.builder(Component.literal("Review protections"),b->parent.openSetupTarget("evasion",this))
                .bounds(x+(w+6)/2,126,(w-6)/2,22).build());
        }
        primary=CockpitButton.builder(Component.literal(review?"Start trading":"Review & continue"),b->{
            long perTrade=MoneyInput.parse(trade),perSession=MoneyInput.parse(session);
            String invalid=QuickStartFlow.validate(perTrade,perSession);
            if(invalid!=null){message=invalid;return;}
            if(!review){review=true;scroll=0;message="";rebuildWidgets();return;}
            var result=parent.startQuickTrading(perTrade,perSession);message=result.detail();
            if(result.ok()){parent.endTutorialNavigation();minecraft.gui.setScreen(null);}
        }).bounds(x,height-55,w,23).build();addRenderableWidget(primary);
        addRenderableWidget(CockpitButton.builder(Component.literal(review?"Back to limits":home instanceof StartHereScreen?"Back to Start here":"Back to GoNuts"),b->{
            if(review){review=false;scroll=0;message="";rebuildWidgets();}else onClose();
        }).bounds(x,height-27,(w-6)/2,20).build());
        addRenderableWidget(CockpitButton.builder(Component.literal("Optional tutorials"),b->minecraft.gui.setScreen(new TutorialScreen(parent,this,false)))
            .bounds(x+(w+6)/2,height-27,(w-6)/2,20).build());
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float dt){
        g.fill(0,0,width,height,0xF0050505);int x=left(),w=bodyWidth();
        g.text(font,review?"2 / 2 · REVIEW & START":"1 / 2 · CHOOSE YOUR LIMITS",x,18,UiTheme.GOLD);
        int top=review?46:164,bottom=height-68,y=top-scroll;
        if(!review){
            g.text(font,"Maximum per trade",x,74,UiTheme.TEXT);g.text(font,"Maximum session spend",x+(w+10)/2,74,UiTheme.TEXT);
            g.text(font,UiTheme.fit(font,"Set your caps, review, then Start. Example: 500k or 5m. No API key is needed in keyless mode.",w),x,44,UiTheme.TEXT);
        }
        g.enableScissor(x,top,x+w,bottom);
        if(review){
            y=paragraph(g,"Per trade: "+trade+" coins · Session spend: "+session+" coins.",x,y,w,UiTheme.GOLD);
            y=paragraph(g,"Start trading saves these fixed limits for this bot and enables continuous buying and listing on the supported server you joined. This bot uses the fixed caps instead of bank-linked percentages. Existing item filters, slots, protections and trade checks still apply.",x,y,w,UiTheme.TEXT);
            String hold=parent.quickStartHold();primary.active=hold==null;
            if(hold!=null)y=paragraph(g,hold,x,y,w,UiTheme.BAD);
        }
        y=paragraph(g,"Swarm and the other hosted services are retired with the upstream project: shared sale history and remote control are unavailable. Solo mode gathers its own data. No profit or immediate purchase is guaranteed.",x,y,w,UiTheme.TEXT);
        var guard=DoughBayClient.evasionGuard();
        y=paragraph(g,"Evasion: "+(guard!=null&&guard.enabled()?"enabled":"off")+". Staff Watch spectator detection is not proof that staff are watching you. Review protection options if you are unsure.",x,y,w,UiTheme.MUTED);
        var warm=DoughBayClient.automationSessionController().warmUp();
        y=paragraph(g,"Market readiness: "+warm.detail()+". Starting a session may begin data collection; it buys only when current market and safety checks pass.",x,y,w,UiTheme.MUTED);
        if(!message.isBlank())y=paragraph(g,message,x,y,w,UiTheme.BAD);
        maxScroll=Math.max(0,y+scroll-bottom);g.disableScissor();super.extractRenderState(g,mx,my,dt);
    }
    private int paragraph(GuiGraphicsExtractor g,String text,int x,int y,int w,int color){
        for(var line:font.split(Component.literal(text),w)){g.text(font,line,x,y,color);y+=13;}return y+10;
    }
    @Override public boolean mouseScrolled(double mx,double my,double dx,double dy){scroll=Math.max(0,Math.min(maxScroll,scroll-(int)(dy*28)));return true;}
    @Override public void onClose(){parent.endTutorialNavigation();minecraft.gui.setScreen(home);}
    @Override public void removed(){DoughBayClient.automationSessionController().setPanelOpen(false);super.removed();}
    @Override public boolean isPauseScreen(){return false;}
}
