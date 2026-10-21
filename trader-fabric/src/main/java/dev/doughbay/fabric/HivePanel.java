package dev.doughbay.fabric;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.network.chat.Component;
import net.fabricmc.loader.api.FabricLoader;
import java.nio.file.*;
import java.util.function.Consumer;

/** One decision at a time; linking never deletes either hive's history. */
final class HivePanel {
    private int x,y,w,bottom,scroll,maxScroll,chosen,detailTop;
    private String note="",input="";
    private boolean share;
    private HiveSetup.Membership member;
    private long createConfirmUntil;
    void init(Font font,int x,int y,int width,int bottom,Consumer<AbstractWidget> add,Runnable rebuild){
        this.x=x;this.y=y;this.w=width;this.bottom=bottom;
        try{member=HiveSetup.configured(game());}catch(Exception e){note="Hive link needs attention. Restore the shared folder; do not delete its data.";}
        var detected=HiveSetup.localHives(member==null?"":member.id());int half=(w-6)/2;
        boolean wide=w>=420;int firstW=wide?half:w,secondX=wide?x+half+6:x,secondY=wide?y+62:y+134,secondW=firstW;
        detailTop=y+(wide?151:222);
        add.accept(CockpitButton.builder(Component.literal(share?"Link accounts":"Link accounts [selected]"),b->{share=false;scroll=0;rebuild.run();}).bounds(x,y+30,half,22).build());
        add.accept(CockpitButton.builder(Component.literal(share?"Share this hive [selected]":"Share this hive"),b->{share=true;scroll=0;rebuild.run();}).bounds(x+half+6,y+30,half,22).build());
        if(share){
            var create=CockpitButton.builder(Component.literal(member==null?"Create first hive & copy code":"Copy this hive's code"),b->{
                try{
                    requireOffline();
                    if(member==null){
                        if(!detected.isEmpty()&&System.currentTimeMillis()>createConfirmUntil){createConfirmUntil=System.currentTimeMillis()+8000;note="A hive already exists here. Link accounts lets you select it. Only choose Create again within 8 seconds if you deliberately want a separate hive.";rebuild.run();return;}
                        ActionTrace.INSTANCE.record(ActionTrace.Scope.HIVE,ActionTrace.Event.REQUEST,ActionTrace.Code.CREATE,"NONE",-1,-1,-1);
                        member=HiveSetup.create(game(),DoughBayClient.activeConfig().directory(),DoughBayClient.account());HiveSetup.register(member);
                    }
                    Minecraft.getInstance().keyboardHandler.setClipboard(HiveSetup.code(member));
                    ActionTrace.INSTANCE.record(ActionTrace.Scope.HIVE,ActionTrace.Event.SUCCEEDED,ActionTrace.Code.CREATE,"NONE",-1,-1,-1);
                    note="Code copied. In the OTHER instance, open Hive setup > Link accounts, paste it, then review. If both already have hives, use Combine; do not delete either hive.";
                }catch(Exception e){failed(e);}rebuild.run();
            }).bounds(x+10,y+(wide?103:93),firstW-20,22).build();
            create.setTooltip(Tooltip.create(Component.literal("Make this the first hive only once. Copy its code for the other account. Setup writes require disconnecting.")));add.accept(create);
        }else{
            var use=CockpitButton.builder(Component.literal(detected.isEmpty()?"No other hive found - paste a code":"Choose local hive ("+(Math.floorMod(chosen,Math.max(1,detected.size()))+1)+" / "+detected.size()+")"),b->{
                try{var target=detected.get(Math.floorMod(chosen,detected.size()));input=HiveSetup.code(target);chosen++;note="Selected hive for "+target.owner()+". Review connection below. Choose again to cycle through other local hives.";}
                catch(Exception e){failed(e);}rebuild.run();
            }).bounds(x+10,y+(wide?103:93),firstW-20,22).build();use.active=!detected.isEmpty();add.accept(use);
            var code=new EditBox(font,secondX+10,secondY+17,secondW-20,22,Component.literal("Other hive's code"));code.setMaxLength(12000);code.setValue(input);code.setResponder(s->input=s);add.accept(code);
            add.accept(CockpitButton.builder(Component.literal("Paste code"),b->code.setValue(Minecraft.getInstance().keyboardHandler.getClipboard())).bounds(secondX+10,secondY+45,(secondW-26)/2,22).build());
            var review=CockpitButton.builder(Component.literal("Review connection"),b->{
                try{ActionTrace.INSTANCE.record(ActionTrace.Scope.HIVE,ActionTrace.Event.REQUEST,ActionTrace.Code.JOIN,"NONE",-1,-1,-1);requireOffline();HiveSetup.decode(input);Minecraft.getInstance().gui.setScreen(new HiveCombineScreen(Minecraft.getInstance().gui.screen(),input));return;}
                catch(Exception e){failed(e);}rebuild.run();
            }).bounds(secondX+16+(secondW-26)/2,secondY+45,(secondW-26)/2,22).build();review.setTooltip(Tooltip.create(Component.literal("Review before queuing. Nothing is deleted or imported here. Disconnect from the server first.")));add.accept(review);
        }
        boolean pending=Files.exists(game().resolve("gonuts-hive-combine.json"));
        add.accept(CockpitButton.builder(Component.literal("Setup guide"),b->net.minecraft.util.Util.getPlatform().openUri(java.net.URI.create("https://gonuts.at/guide/#hive"))).bounds(x,bottom-23,pending?half:w,20).build());
        if(pending)add.accept(CockpitButton.builder(Component.literal("Cancel queued combine"),b->{try{requireOffline();HiveCombine.cancelPending(game());note=HiveCombine.startupMessage;}catch(Exception e){failed(e);}rebuild.run();}).bounds(x+half+6,bottom-23,half,20).build());
    }
    private static Path game(){return FabricLoader.getInstance().getGameDir();}
    private static void requireOffline() throws java.io.IOException {
        if(Minecraft.getInstance().getConnection()!=null)throw new java.io.IOException("Disconnect first, then open Multiplayer > GoNuts hive. Your current links and trading data are unchanged.");
    }
    private void failed(Exception e){note=e instanceof java.io.IOException&&e.getMessage()!=null?e.getMessage():"Setup could not finish. Existing data and settings were kept.";if(Minecraft.getInstance().getConnection()==null)FaultTrace.INSTANCE.record(FaultTrace.Stage.HIVE_SETUP,e,System.currentTimeMillis());}
    boolean scroll(double mx,double my,double delta){if(mx<x||mx>x+w||my<detailTop||my>bottom-30)return false;scroll=Math.max(0,Math.min(maxScroll,scroll-(int)Math.signum(delta)*26));return true;}
    void render(GuiGraphicsExtractor g,Font font){
        g.text(font,UiTheme.fit(font,"CONNECT YOUR ACCOUNTS",w),x,y,UiTheme.GOLD);
        String status=member==null?"One hive can have many accounts. Join it from each new instance.":HiveSetup.active()?"This instance is connected to a hive.":"This instance has a hive link. Restart to connect.";
        g.text(font,UiTheme.fit(font,status,w),x,y+14,UiTheme.SECONDARY);
        boolean wide=w>=420;int cw=wide?(w-6)/2:w,otherX=wide?x+cw+6:x,otherY=wide?y+62:y+134;
        if(share){
            panel(g,font,x,y+62,cw,wide?80:64,"1  SHARE FROM THE FIRST ACCOUNT","Create once. Copy the code for the other instance.");
            panel(g,font,otherX,otherY,cw,wide?80:79,"2  OPEN THE OTHER INSTANCE","Use Link accounts there. Already made two hives? Combine instead of deleting either one.");
        }else{
            panel(g,font,x,y+62,cw,wide?80:64,"1  SELECT THE OTHER HIVE","Choose here or paste a code.");
            panel(g,font,otherX,otherY,cw,wide?80:79,"2  PASTE AND REVIEW","");
        }
        int top=detailTop,end=bottom-48;if(end<=top)return;
        g.enableScissor(x,top,x+w,end);int row=top-scroll;
        row=detail(g,font,row,"3  FINISH AFTER REVIEW",share?"Each additional account reviews the connection. Hive sharing works on this PC; Swarm is separate.":"New account: choose Join existing hive, then restart only that new instance. Existing members can keep running. Combining two existing histories instead requires closing BOTH instances and starting the requesting instance first.");
        String guidance=!note.isEmpty()?note:!HiveCombine.startupMessage.isEmpty()?HiveCombine.startupMessage:"Hive missing from the list? On an existing account, disconnect and open Multiplayer > GoNuts hive > Share this hive. Copy its code, then paste it here. Older shared-folder setups need this registration once. Do not delete either folder.";
        row=detail(g,font,row,"SETUP STATUS / HELP",guidance);
        maxScroll=Math.max(0,row+scroll-end);scroll=Math.min(scroll,maxScroll);g.disableScissor();
        if(maxScroll>0)g.text(font,"Scroll for setup status",x,bottom-43,UiTheme.MUTED);
    }
    private void panel(GuiGraphicsExtractor g,Font font,int left,int row,int width,int h,String title,String body){UiTheme.panel(g,left,row,width,h,UiTheme.INSET);g.text(font,UiTheme.fit(font,title,width-20),left+10,row+8,UiTheme.TEAL);int py=row+21;for(var line:font.split(Component.literal(body),width-20)){if(py+9>row+h-8)break;g.text(font,line,left+10,py,UiTheme.SECONDARY);py+=12;}}
    private int detail(GuiGraphicsExtractor g,Font font,int row,String title,String body){var lines=font.split(Component.literal(body),Math.max(30,w-22));int h=29+lines.size()*12;UiTheme.panel(g,x,row,w,h,UiTheme.INSET);g.text(font,UiTheme.fit(font,title,w-20),x+10,row+9,UiTheme.GOLD);int py=row+24;for(var line:lines){g.text(font,line,x+10,py,UiTheme.TEXT);py+=12;}return row+h+8;}
}
