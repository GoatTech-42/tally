package dev.doughbay.fabric;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Review and consent only. The actual import can run only at a later startup. */
final class HiveCombineScreen extends GoNutsScreen {
    private final Screen parent;private final String code;
    private String message="";private boolean queued,joined;private int scroll,maxScroll;
    HiveCombineScreen(Screen parent,String code){super(Component.literal("Review joining these accounts"));this.parent=parent;this.code=code;}
    @Override protected void init(){
        DoughBayClient.automationSessionController().setPanelOpen(true);
        int w=Math.min(600,width-24),x=(width-w)/2;
        var join=CockpitButton.builder(Component.literal(joined?"Account linked - restart this instance":"Join existing hive (new account)"),b->{
            try {
                if(minecraft.getConnection()!=null)throw new java.io.IOException("Disconnect before setting up a hive");
                var game=net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir();
                HiveSetup.joinFresh(game,DoughBayClient.activeConfig().directory(),code,DoughBayClient.account());
                joined=true;scroll=0;
                ActionTrace.INSTANCE.record(ActionTrace.Scope.HIVE,ActionTrace.Event.SUCCEEDED,ActionTrace.Code.JOIN,"PERSISTENCE_WAIT",-1,-1,-1);
                message="Account linked. Close and restart ONLY this Minecraft instance. Existing hive members can keep running. Your settings and Swarm link are kept; trading is not started by joining.";
            }catch(Exception e){FaultTrace.INSTANCE.record(FaultTrace.Stage.HIVE_SETUP,e,System.currentTimeMillis());message=e instanceof java.io.IOException?e.getMessage():"This account could not join safely. Existing data was kept.";}
            rebuildWidgets();
        }).bounds(x,height-107,w,22).build();join.active=!queued&&!joined;addRenderableWidget(join);
        var restore=CockpitButton.builder(Component.literal(joined?"Account linked - restart this instance":"Restore existing Hive account"),b->{
            try {
                if(minecraft.getConnection()!=null)throw new java.io.IOException("Disconnect before restoring a Hive account");
                var game=net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir();
                HiveSetup.restoreExisting(game,DoughBayClient.activeConfig().directory(),code,DoughBayClient.account());
                joined=true;scroll=0;
                message="Saved account restored. Close and restart ONLY this instance. Its original settings, stock, orders, limits and recovery state remain in the Hive. Nothing was deleted, reset or transferred; native recovery checks still apply.";
            }catch(Exception e){FaultTrace.INSTANCE.record(FaultTrace.Stage.HIVE_SETUP,e,System.currentTimeMillis());message=e instanceof java.io.IOException?e.getMessage():"The saved account could not be restored safely. Existing data was kept.";}
            rebuildWidgets();
        }).bounds(x,height-81,w,22).build();restore.active=!queued&&!joined;addRenderableWidget(restore);
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
        }).bounds(x,height-55,w,22).build();confirm.active=!queued&&!joined;addRenderableWidget(confirm);
        addRenderableWidget(CockpitButton.builder(Component.literal(queued||joined?"Back to Hive setup":"Back without changes"),b->onClose()).bounds(x,height-27,w,20).build());
    }
    @Override public void render(GuiGraphics g,int mx,int my,float dt){
        g.fill(0,0,width,height,0xF0050505);int w=Math.min(600,width-24),x=(width-w)/2,y=42;
        g.drawString(font,UiTheme.fit(font,getTitle().getString(),w),x,18,UiTheme.GOLD);
        int top=40,end=Math.max(40,height-120);g.enableScissor(x,top,x+w,end);y=top-scroll;
        if(joined){
            y=card(g,y,x,w,"ACCOUNT LINKED / NEXT STEP",message,UiTheme.GOOD);
        }else{
        y=card(g,y,x,w,"ADDING ANOTHER ACCOUNT TO A HIVE?","A hive can have many accounts. For a fresh account without trading history, choose Join existing hive. Only this new instance needs a restart; existing members can keep running. Your settings and Swarm link stay separate.",UiTheme.GOOD);
        y=card(g,y,x,w,"DELETED THE OLD INSTANCE?","Sign into that same Minecraft account in this fresh instance, select its original Hive, then choose Restore existing Hive account. Close any other instance using that account first. The original saved profile and owned records are reused; this is not a reset or a way to delete an account.",UiTheme.TEAL);
        y=card(g,y,x,w,"ALREADY HAVE TRADING HISTORY?","Do not use the fresh-account join to discard it. The separate combination tool below preserves two supported single-account histories. Its two-hive restriction is not a limit on hive membership.",UiTheme.GOLD);
        y=card(g,y,x,w,queued?"COMBINATION QUEUED / NEXT STEP":"ONLY WHEN COMBINING EXISTING HISTORIES",queued?"Close BOTH Minecraft instances completely. Start this requesting instance first. Wait for the import confirmation before opening the other. If validation fails, read the reason in Hive setup; do not repeatedly restart.":"Use the same GoNuts build in both instances. Queue safe combination, close BOTH Minecraft windows, then start this requesting instance first. Wait for import confirmation before opening the other. Both original folders are kept; each account keeps its settings, limits and recovery state. Cancel queued combine is available in Hive setup until switching begins.",UiTheme.TEAL);
        if(!message.isEmpty())y=card(g,y,x,w,"SETUP STATUS",message,UiTheme.WARN);
        }
        maxScroll=Math.max(0,y+scroll-end);scroll=Math.min(scroll,maxScroll);g.disableScissor();super.render(g,mx,my,dt);
    }
    private int card(GuiGraphics g,int y,int x,int w,String heading,String body,int color){
        var lines=font.split(Component.literal(body),w-24);int h=30+lines.size()*13;UiTheme.panel(g,x,y,w,h,UiTheme.INSET);
        g.drawString(font,UiTheme.fit(font,heading,w-24),x+12,y+9,color);int py=y+25;for(var line:lines){g.drawString(font,line,x+12,py,UiTheme.TEXT);py+=13;}return y+h+9;
    }
    @Override public boolean mouseScrolled(double mx,double my,double dx,double dy){scroll=Math.max(0,Math.min(maxScroll,scroll-(int)(dy*26)));return true;}
    @Override public void onClose(){minecraft.setScreen(parent);}
    @Override public void removed(){DoughBayClient.automationSessionController().setPanelOpen(false);super.removed();}
    @Override public boolean isPauseScreen(){return false;}
}
