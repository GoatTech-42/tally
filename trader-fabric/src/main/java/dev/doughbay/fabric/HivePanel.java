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
    private int x,y,w,bottom;
    private String note = "", input = "";
    private HiveSetup.Membership member;
    void init(Font font, int x, int y, int width, int bottom, Consumer<AbstractWidget> add, Runnable rebuild) {
        this.x=x; this.y=y; this.w=width; this.bottom=bottom;
        try { member=HiveSetup.configured(game()); }
        catch(Exception e) { note="Hive link needs attention. Restore the shared folder; do not delete its data."; }
        boolean offline=Minecraft.getInstance().getConnection()==null;
        var create=CockpitButton.builder(Component.literal(member==null?"Create hive & copy code":"Copy hive code"),b->{
            try {
                if(Minecraft.getInstance().getConnection()!=null) throw new java.io.IOException("Disconnect from the server before setting up a hive");
                if(member==null) member=HiveSetup.create(game(),DoughBayClient.activeConfig().directory(),DoughBayClient.account());
                Minecraft.getInstance().keyboardHandler.setClipboard(HiveSetup.code(member));
                note="Code copied. Restart this instance, then paste it in the second instance's Hive tab.";
            } catch(Exception e) { note=message(e); }
            rebuild.run();
        }).bounds(x,y+65,Math.min(w,260),22).build();
        create.active=offline;add.accept(create);
        var code=new EditBox(font,x,y+134,w,22,Component.literal("Hive setup code"));
        code.setMaxLength(12000);code.setValue(input);code.setResponder(s->input=s);add.accept(code);
        int half=(w-8)/2;
        add.accept(CockpitButton.builder(Component.literal("Paste code"),b->code.setValue(Minecraft.getInstance().keyboardHandler.getClipboard())).bounds(x,y+163,half,22).build());
        var join=CockpitButton.builder(Component.literal("Join hive"),b->{
            try {
                if(Minecraft.getInstance().getConnection()!=null) throw new java.io.IOException("Disconnect from the server before setting up a hive");
                member=HiveSetup.join(game(),DoughBayClient.activeConfig().directory(),code.getValue());
                input="";
                note="Hive linked. Close and reopen Minecraft to connect. Original local files are untouched.";
            } catch(Exception e) { note=message(e); }
            rebuild.run();
        }).bounds(x+half+8,y+163,half,22).build();
        join.active=offline&&member==null;add.accept(join);
        add.accept(CockpitButton.builder(Component.literal("Setup guide"),b->net.minecraft.util.Util.getPlatform().openUri(java.net.URI.create("https://cody-raves.github.io/Gonuts/guide/#hive"))).bounds(x,y+218,130,22).build());
    }
    private static Path game(){ return FabricLoader.getInstance().getGameDir(); }
    private static String message(Exception e){ return e instanceof java.io.IOException&&e.getMessage()!=null?e.getMessage():"Setup could not finish. Existing data and settings were kept."; }
    void render(GuiGraphicsExtractor g,Font font) {
        String status=member==null?"Two accounts. One shared hive. One web panel.":HiveSetup.active()?"Hive connected - "+DoughBayClient.hiveAccounts().size()+" known account(s)":"Hive linked - restart this instance to connect";
        g.text(font,UiTheme.fit(font,status,w),x,y,UiTheme.GOLD);
        line(g,font,"1. In the instance whose trading history you want to keep, create a hive.",y+23);
        line(g,font,"2. On the same PC, open the second instance and paste the code below.",y+39);
        line(g,font,"Hive code (local folder link; do not post it publicly)",y+116);
        line(g,font,"Restart both once. Disconnect from the server before setup.",y+197);
        int row=y+253;
        for(var text:font.split(Component.literal(note),w)){if(row+10>bottom)break;g.text(font,text,x,row,UiTheme.TEXT);row+=12;}
        row=Math.max(row+12,y+285);
        for(var text:font.split(Component.literal("One PC only. Keep the original folder. Settings and history are shared; logins and proxies stay local. See Setup guide for details."),w)){if(row+10>bottom)break;g.text(font,text,x,row,UiTheme.SECONDARY);row+=12;}
    }
    private void line(GuiGraphicsExtractor g,Font f,String text,int row){g.text(f,UiTheme.fit(f,text,w),x,row,UiTheme.SECONDARY);}
}
