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
                HiveCombine.queue(game,code);queued=true;
                message="Queued. Close BOTH Minecraft instances. Start THIS instance first; when it finishes importing, start the other. Originals stay intact.";
            }catch(Exception e){message=e instanceof java.io.IOException||e instanceof java.sql.SQLException?e.getMessage():"Setup could not finish. Both original hives were kept.";}
            rebuildWidgets();
        }).bounds(x,height-55,w,22).build();confirm.active=!queued;addRenderableWidget(confirm);
        addRenderableWidget(CockpitButton.builder(Component.literal(queued?"Back to Hive setup":"Back without changes"),b->onClose()).bounds(x,height-27,w,20).build());
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float dt){
        g.fill(0,0,width,height,0xF0050910);int w=Math.min(600,width-24),x=(width-w)/2,y=42;
        g.text(font,UiTheme.fit(font,getTitle().getString(),w),x,18,UiTheme.GOLD);
        String body="Both accounts keep their original folders and settings. A NEW shared database will contain both trading histories; each account keeps its own orders, balances, session limits and recovery state. Nothing is traded, cleared or reset by this setup.\n\n1. Update both instances to this build. Disconnect before queuing.\n2. Choose Queue safe combination below.\n3. CLOSE BOTH Minecraft instances completely.\n4. Start THIS instance first and allow the import to finish. Then start the other instance.\n\nThe import supports two single-account hives. Conflicting receipts, payroll/loan records, unsupported data, or a running instance block the import. The originals are never replaced. Both accounts switch only after the new database passes validation.\n\nYou can cancel a queued import from Hive setup until the accounts begin switching.";
        g.enableScissor(x,40,x+w,Math.max(40,height-68));
        for(var line:font.split(Component.literal(body+"\n\n"+message),w)){g.text(font,line,x,y-scroll,UiTheme.TEXT);y+=13;}
        maxScroll=Math.max(0,y-(height-68));g.disableScissor();super.extractRenderState(g,mx,my,dt);
    }
    @Override public boolean mouseScrolled(double mx,double my,double dx,double dy){scroll=Math.max(0,Math.min(maxScroll,scroll-(int)(dy*26)));return true;}
    @Override public void onClose(){minecraft.gui.setScreen(parent);}
    @Override public void removed(){DoughBayClient.automationSessionController().setPanelOpen(false);super.removed();}
    @Override public boolean isPauseScreen(){return false;}
}
