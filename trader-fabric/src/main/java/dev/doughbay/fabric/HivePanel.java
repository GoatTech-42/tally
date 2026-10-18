package dev.doughbay.fabric;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.network.chat.Component;
import net.fabricmc.loader.api.FabricLoader;
import java.nio.file.Path;
import java.util.function.Consumer;

/** Copy from the first instance, paste into the second. Applied only after restart. */
final class HivePanel {
    private static final String DISCONNECT_HELP = "Disconnect from the server, then open Multiplayer > GoNuts hive. Create the code in the instance whose history you want to keep; paste it in a second instance on this same PC. Restart each instance after linking.";
    private int x,y,w,bottom;
    private String note = "", input = "";
    private HiveSetup.Membership member;
    private long createConfirmUntil;
    void init(Font font, int x, int y, int width, int bottom, Consumer<AbstractWidget> add, Runnable rebuild) {
        this.x=x; this.y=y; this.w=width; this.bottom=bottom;
        try { member=HiveSetup.configured(game()); }
        catch(Exception e) { note="Hive link needs attention. Restore the shared folder; do not delete its data."; }
        var detected=HiveSetup.localHives(member==null?"":member.id());
        int half=(w-8)/2;
        var use=CockpitButton.builder(Component.literal(detected.isEmpty()?"No other local hive found":"Use existing hive: "+detected.getFirst().owner()),b->{
            try{input=HiveSetup.code(detected.getFirst());note="Existing hive selected. Paste another code to choose a different hive. Review Join / Combine below.";}
            catch(Exception e){note=message(e);}rebuild.run();
        }).bounds(x,y+65,half,22).build();
        use.active=!detected.isEmpty();add.accept(use);
        var create=CockpitButton.builder(Component.literal(member==null?"Create hive & copy code":"Copy hive code"),b->{
            try {
                if(Minecraft.getInstance().getConnection()!=null) throw new java.io.IOException(DISCONNECT_HELP);
                if(member==null) {
                    if(!detected.isEmpty()&&System.currentTimeMillis()>createConfirmUntil){createConfirmUntil=System.currentTimeMillis()+8000;note="A hive already exists on this PC. Use existing hive to join it. Only click Create again within 8 seconds if you want a separate hive.";rebuild.run();return;}
                    member=HiveSetup.create(game(),DoughBayClient.activeConfig().directory(),DoughBayClient.account());HiveSetup.register(member);
                }
                Minecraft.getInstance().keyboardHandler.setClipboard(HiveSetup.code(member));
                note="Code copied. Restart this instance, then paste it in the second instance's Hive tab.";
            } catch(Exception e) { note=message(e); }
            rebuild.run();
        }).bounds(x+half+8,y+65,half,22).build();
        // Keep the action reachable: a disabled button could not explain the required offline route.
        // The handler still refuses every setup write while connected.
        create.setTooltip(Tooltip.create(Component.literal(DISCONNECT_HELP)));
        add.accept(create);
        var code=new EditBox(font,x,y+134,w,22,Component.literal("Hive setup code"));
        code.setMaxLength(12000);code.setValue(input);code.setResponder(s->input=s);add.accept(code);
        add.accept(CockpitButton.builder(Component.literal("Paste code"),b->code.setValue(Minecraft.getInstance().keyboardHandler.getClipboard())).bounds(x,y+163,half,22).build());
        var join=CockpitButton.builder(Component.literal(member==null?"Review joining hive":"Review combining hives"),b->{
            try {
                if(Minecraft.getInstance().getConnection()!=null) throw new java.io.IOException(DISCONNECT_HELP);
                HiveSetup.decode(code.getValue());
                Minecraft.getInstance().gui.setScreen(new HiveCombineScreen(Minecraft.getInstance().gui.screen(),code.getValue()));return;
            } catch(Exception e) { note=message(e); }
            rebuild.run();
        }).bounds(x+half+8,y+163,half,22).build();
        join.setTooltip(Tooltip.create(Component.literal(member==null?DISCONNECT_HELP:"Select the other hive above or paste its code, then review combining. Both original histories and per-account settings are preserved; both instances must be closed for the startup import.")));
        add.accept(join);
        add.accept(CockpitButton.builder(Component.literal("Setup guide"),b->net.minecraft.util.Util.getPlatform().openUri(java.net.URI.create("https://cody-raves.github.io/Gonuts/guide/#hive"))).bounds(x,y+218,130,22).build());
        if(java.nio.file.Files.exists(game().resolve("gonuts-hive-combine.json")))add.accept(CockpitButton.builder(Component.literal("Cancel queued combine"),b->{
            try{HiveCombine.cancelPending(game());note=HiveCombine.startupMessage;}catch(Exception e){note=message(e);}rebuild.run();
        }).bounds(x+138,y+218,Math.min(200,w-138),22).build());
    }
    private static Path game(){ return FabricLoader.getInstance().getGameDir(); }
    private static String message(Exception e){ return e instanceof java.io.IOException&&e.getMessage()!=null?e.getMessage():"Setup could not finish. Existing data and settings were kept."; }
    void render(GuiGraphicsExtractor g,Font font) {
        String status=member==null?"Two accounts. One shared hive. One web panel.":HiveSetup.active()?"Hive connected - "+DoughBayClient.hiveAccounts().size()+" known account(s)":"Hive linked - restart this instance to connect";
        g.text(font,UiTheme.fit(font,status,w),x,y,UiTheme.GOLD);
        line(g,font,"1. Disconnect. Use an existing hive when one is found.",y+23);
        line(g,font,"2. Join from a fresh instance, or Combine two existing hives.",y+39);
        line(g,font,"Hive code (local folder link; do not post it publicly)",y+116);
        line(g,font,"Combine: close BOTH instances; start the requesting one first.",y+197);
        int row=y+253;
        String guidance=!note.isEmpty()?note:!HiveCombine.startupMessage.isEmpty()?HiveCombine.startupMessage:Minecraft.getInstance().getConnection()!=null?DISCONNECT_HELP:"";
        for(var text:font.split(Component.literal(guidance),w)){if(row+10>bottom)break;g.text(font,text,x,row,UiTheme.TEXT);row+=12;}
        row=Math.max(row+12,y+285);
        for(var text:font.split(Component.literal("One PC only. Original folders are kept. Combined members share market history and keep separate settings; logins and proxies stay local."),w)){if(row+10>bottom)break;g.text(font,text,x,row,UiTheme.SECONDARY);row+=12;}
    }
    private void line(GuiGraphicsExtractor g,Font f,String text,int row){g.text(f,UiTheme.fit(f,text,w),x,row,UiTheme.SECONDARY);}
}
