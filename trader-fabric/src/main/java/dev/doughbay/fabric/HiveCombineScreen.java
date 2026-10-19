package dev.doughbay.fabric;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/** Review and consent only. The actual import can run only at a later startup. */
final class HiveCombineScreen extends Screen {
    private final Screen parent;private final String code;
    private String message="";private boolean queued;private int scroll,maxScroll;
    HiveCombineScreen(Screen parent,String code){super(Component.literal("Review joining these accounts"));this.parent=parent;this.code=code;}
    @Override protected void init(){
        DoughBayClient.automationSessionController().setPanelOpen(true);
        int w=Math.min(600,width-24),x=(width-w)/2;
        var confirm=CockpitButton.builder(Component.literal(queued?"Combination queued":"Queue safe combination"),b->{
            try {
                if(minecraft.getConnection()!=null)throw new java.io.IOException("Disconnect before setting up a hive");
                var game=net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir();
                var target=HiveSetup.decode(code);
                if(HiveSetup.configured(game)==null){HiveSetup.create(game,DoughBayClient.activeConfig().directory(),DoughBayClient.account());}
                ActionTrace.INSTANCE.record(ActionTrace.Scope.HIVE,ActionTrace.Event.REQUEST,ActionTrace.Code.COMBINE,"NONE",-1,-1,-1);
                HiveCombine.queue(game,code);queued=true;scroll=0;
                ActionTrace.INSTANCE.record(ActionTrace.Scope.HIVE,ActionTrace.Event.RESULT,ActionTrace.Code.COMBINE,"PERSISTENCE_WAIT",-1,-1,-1);
                message="Queued. Close BOTH Minecraft instances. Start THIS instance first; when it finishes importing, start the other. Originals stay intact.";
            }catch(Exception e){FaultTrace.INSTANCE.record(FaultTrace.Stage.HIVE_SETUP,e,System.currentTimeMillis());message=e instanceof java.io.IOException||e instanceof java.sql.SQLException?e.getMessage():"Setup could not finish. Both original hives were kept.";}
            rebuildWidgets();
        }).bounds(x,height-55,w,22).build();confirm.active=!queued;addRenderableWidget(confirm);
        addRenderableWidget(CockpitButton.builder(Component.literal(queued?"Back to Hive setup":"Back without changes"),b->onClose()).bounds(x,height-27,w,20).build());
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float dt){
        g.fill(0,0,width,height,0xF0050910);int w=Math.min(600,width-24),x=(width-w)/2,y=42;
        g.text(font,UiTheme.fit(font,getTitle().getString(),w),x,18,UiTheme.GOLD);
        int top=40,end=Math.max(40,height-68);g.enableScissor(x,top,x+w,end);y=top-scroll;
        y=card(g,y,x,w,queued?"QUEUED / WHAT TO DO NEXT":"BEFORE YOU CONFIRM",queued?"Close BOTH Minecraft instances completely. Start this requesting instance first. Wait for the import confirmation before opening the other.":"This queues a startup import; it does not combine data while either account is trading. Disconnect before confirming.",UiTheme.TEAL);
        y=card(g,y,x,w,"1  PREPARE BOTH INSTANCES","Use the same GoNuts build in both. Select the OTHER account's hive code. This importer supports two single-account hives.",UiTheme.GOLD);
        y=card(g,y,x,w,"2  QUEUE, CLOSE BOTH, START THIS ONE FIRST","Choose Queue safe combination below. Close both game windows, including any background instance. Start this requesting instance and wait for completion; then start the other.",UiTheme.GOLD);
        y=card(g,y,x,w,"3  CHECK THE RESULT","A success message confirms the new shared ledger. If combination cannot finish, read the reason in Hive setup. A data validation failure is not fixed by repeatedly restarting.",UiTheme.GOLD);
        y=card(g,y,x,w,"YOUR HISTORY AND SETTINGS","Both original folders are kept. A new shared ledger contains both histories; each account keeps its orders, balances, limits and recovery state. No stock is cleared or reset. Cancel queued combine is available in Hive setup until switching begins.",UiTheme.GOOD);
        if(!message.isEmpty())y=card(g,y,x,w,"SETUP STATUS",message,UiTheme.WARN);
        maxScroll=Math.max(0,y+scroll-end);scroll=Math.min(scroll,maxScroll);g.disableScissor();super.extractRenderState(g,mx,my,dt);
    }
    private int card(GuiGraphicsExtractor g,int y,int x,int w,String heading,String body,int color){
        var lines=font.split(Component.literal(body),w-24);int h=30+lines.size()*13;UiTheme.panel(g,x,y,w,h,UiTheme.INSET);
        g.text(font,UiTheme.fit(font,heading,w-24),x+12,y+9,color);int py=y+25;for(var line:lines){g.text(font,line,x+12,py,UiTheme.TEXT);py+=13;}return y+h+9;
    }
    @Override public boolean mouseScrolled(double mx,double my,double dx,double dy){scroll=Math.max(0,Math.min(maxScroll,scroll-(int)(dy*26)));return true;}
    @Override public void onClose(){minecraft.gui.setScreen(parent);}
    @Override public void removed(){DoughBayClient.automationSessionController().setPanelOpen(false);super.removed();}
    @Override public boolean isPauseScreen(){return false;}
}
