package dev.doughbay.fabric;

import com.fasterxml.jackson.databind.node.ObjectNode;
import net.minecraft.client.Minecraft;
import net.fabricmc.loader.api.FabricLoader;
import java.nio.file.*;
import java.util.*;

/** Both authenticated panel transports use this same install-local control surface. */
final class PanelBots {
    private static final PanelBotRegistry REGISTRY=new PanelBotRegistry(Path.of(System.getProperty("user.home"),".gonuts","panel-bots"),UUID.randomUUID().toString());
    private record Review(String target,String command,String connection,long at) {}
    private record Join(String source,String request,long at) {}
    private static final Map<String,Review> REVIEWS=new LinkedHashMap<>();
    private static Join join;
    private static boolean share,awaitingRestart;
    private static PanelHiveJoin joining;
    private static long shareUntil;
    private static long nextRefresh;
    private static long nextSweep;
    private static Object connection;
    private static String connectionToken="";
    private static String setup="";
    private PanelBots() {}
    static synchronized boolean ownsConnection(){return join!=null||awaitingRestart;}
    private static Path game(){return FabricLoader.getInstance().getGameDir();}
    private static Path root(){return HiveSetup.sharedDirectoryOr(DoughBayClient.activeConfig().directory());}
    private static boolean sameRoot(String directory){try{return Files.isSameFile(root(),Path.of(directory));}catch(Exception e){return false;}}
    static synchronized String data(Map<String,String> query) {
        var out=PanelBotRegistry.JSON.createObjectNode().put("self",REGISTRY.run()).put("setup",setup);
        var bots=out.putArray("bots");long now=System.currentTimeMillis();int masked=0;
        var detected=REGISTRY.bots(now);
        for(var b:detected){
            var row=bots.addObject().put("id",b.id()).put("label",NameMask.on()?"Bot "+(++masked):b.account())
                    .put("sameHive",sameRoot(b.root())).put("linked",b.linked()).put("self",b.id().equals(REGISTRY.run())).put("setup",b.setup())
                    .put("group",dev.doughbay.fabric.hosted.DashboardHive.id(b.root().toLowerCase(Locale.ROOT)))
                    .put("hiveSize",Math.max(sameRoot(b.root())?DoughBayClient.hiveAccounts().size():1,
                            detected.stream().filter(other->other.root().equals(b.root())).map(PanelBotRegistry.Bot::account).distinct().count()));
            if(b.id().equals(query.getOrDefault("bot",REGISTRY.run()))&&sameRoot(b.root()))
                row.set("commands",PanelBotRegistry.JSON.valueToTree(b.commands()));
        }
        return out.toString();
    }
    static synchronized String change(Map<String,String> form) {
        long now=System.currentTimeMillis();String action=form.getOrDefault("action","");
        try{
            if(action.equals("review")) {
                var b=REGISTRY.target(form.getOrDefault("bot",""),now);
                if(!sameRoot(b.root()))return answer(false,"Choose a bot already in this Hive");
                if(b.connection().isEmpty())return answer(false,"Connect the selected bot before reviewing a server command");
                if(REGISTRY.bots(now).stream().filter(other->other.account().equalsIgnoreCase(b.account())).count()!=1)
                    return answer(false,"This account is running in more than one instance. Close the duplicate before queuing commands");
                String command=PanelCommandQueue.normalize(form.get("command"));
                REVIEWS.entrySet().removeIf(e->now-e.getValue().at()>30_000);
                if(REVIEWS.size()>=32)return answer(false,"Too many pending command reviews");
                String id=UUID.randomUUID().toString();REVIEWS.put(id,new Review(b.id(),command,b.connection(),now));
                return PanelBotRegistry.JSON.createObjectNode().put("ok",true).put("note","Review the exact bot and command, then queue once")
                        .put("review",id).put("bot",NameMask.on()?"Selected bot":b.account()).put("command","/"+command).toString();
            }
            if(action.equals("send")) {
                String id=form.getOrDefault("review","");var r=REVIEWS.remove(id);
                if(r==null||now-r.at()>30_000)return answer(false,"Review expired or already submitted; check queue status before sending again");
                var b=REGISTRY.target(r.target(),now);if(!sameRoot(b.root()))return answer(false,"Hive membership changed; review again");
                if(!b.connection().equals(r.connection()))return answer(false,"The selected bot's connection changed. Review again; nothing was queued");
                REGISTRY.post(b.id(),id,"command",Map.of("command",r.command(),"connection",r.connection()),now);
                return answer(true,"Queued for the selected bot; waiting for its safe checkpoint");
            }
            if(action.equals("cancel")) {
                var b=REGISTRY.target(form.getOrDefault("bot",""),now);if(!sameRoot(b.root()))return answer(false,"Choose a bot in this Hive");
                UUID.fromString(form.get("id"));REGISTRY.post(b.id(),UUID.randomUUID().toString(),"cancel",Map.of("job",form.get("id")),now);
                return answer(true,"Cancellation requested; a command already sent cannot be withdrawn");
            }
            if(action.equals("join")) {
                var target=REGISTRY.target(form.getOrDefault("bot",""),now);var source=REGISTRY.target(form.getOrDefault("hive",""),now);
                if(target.root().equals(source.root()))return answer(false,"These bots already share a Hive");
                REGISTRY.post(source.id(),UUID.randomUUID().toString(),"share",Map.of(),now);
                REGISTRY.post(target.id(),UUID.randomUUID().toString(),"join",Map.of("source",source.id()),now);
                return answer(true,"Hive setup queued. The new bot will leave its world safely; only that instance needs a normal restart after linking");
            }
            return answer(false,"Unknown bot action");
        }catch(IllegalArgumentException e){return answer(false,e.getMessage()==null?"Check the selected bot and command":e.getMessage());}
        catch(Exception e){return answer(false,"Could not submit safely. Check the selected bot's status before repeating");}
    }
    private static String answer(boolean ok,String note){return PanelBotRegistry.JSON.createObjectNode().put("ok",ok).put("note",note).toString();}
    static synchronized void tick(Minecraft mc) {
        long now=System.currentTimeMillis();if(now<nextRefresh)return;nextRefresh=now+1000;
        try{
            if(now>=nextSweep){REGISTRY.sweep(now);nextSweep=now+30_000;}
            if(connection!=mc.getConnection()){connection=mc.getConnection();connectionToken=connection==null?"":UUID.randomUUID().toString();}
            REGISTRY.advertise(new PanelBotRegistry.Bot(REGISTRY.run(),now,ProcessHandle.current().pid(),DoughBayClient.account(),game().toRealPath().toString(),root().toRealPath().toString(),HiveSetup.configured(game())!=null,setup,connectionToken,PanelCommands.QUEUE.views()));
            for(ObjectNode request:REGISTRY.take(now)) {
                try{
                var sender=REGISTRY.target(request.path("from").asText(),now);
                switch(request.path("action").asText()) {
                    case "command" -> {if(sameRoot(sender.root())){
                        String id=request.path("id").asText();
                        PanelCommands.QUEUE.add(id,request.path("command").asText(),now,connection==null?new Object():connection);
                        if(connectionToken.isEmpty()||!connectionToken.equals(request.path("connection").asText()))PanelCommands.QUEUE.cancel(id,PanelCommands.port(mc));
                    }}
                    case "cancel" -> {if(sameRoot(sender.root()))PanelCommands.QUEUE.cancel(request.path("job").asText(),PanelCommands.port(mc));}
                    case "share" -> {share=true;shareUntil=now+30_000;}
                    case "join" -> {if(join==null&&!awaitingRestart){join=new Join(request.path("source").asText(),request.path("id").asText(),now);joining=new PanelHiveJoin(game(),DoughBayClient.activeConfig().directory(),DoughBayClient.account());}}
                    default -> {}
                }
                }catch(IllegalArgumentException e){setup=e.getMessage()==null?"Request was refused; check the selected bot":e.getMessage();}
                catch(Exception e){setup="Request could not be accepted safely. Check this bot before sending again";}
            }
            if(share&&now>shareUntil){share=false;setup="Hive registration timed out waiting for a safe checkpoint. No link was changed";}
            if(share&&DoughBayClient.automationSessionController().safeToLeave()) {
                // Metadata registration only: no DB copy, migration, hot switch, or connection change.
                var m=HiveSetup.configured(game());if(m==null)m=HiveSetup.create(game(),root(),DoughBayClient.account());
                HiveSetup.register(m);share=false;setup="Hive ready for additional accounts";
            }
            if(join!=null)finishJoin(mc,now);
        }catch(Exception e){
            String message=String.valueOf(e.getMessage());
            setup=message.contains("trading history")?"This bot already has trading history. Its data was kept; only a fresh instance can use Add to Hive":
                    message.contains("order or balance")||message.contains("account records")?"This bot has saved account records. Its data was kept; choose a fresh instance":
                    "Setup did not finish safely. Keep existing folders and report Hive setup for review";
            share=false;join=null;
        }
    }
    private static void finishJoin(Minecraft mc,long now) throws Exception {
        if(now-join.at()>30_000){setup="Setup timed out without replacing this bot's link. Refresh and review again";join=null;return;}
        var source=REGISTRY.target(join.source(),now);
        var target=HiveSetup.configured(Path.of(source.game()));
        boolean done=joining.step(target,new PanelHiveJoin.Client(){
            public boolean safe(){return DoughBayClient.automationSessionController().safeToLeave();}
            public boolean active(){return DoughBayClient.automationSessionController().snapshot().active();}
            public boolean cursorEmpty(){return mc.player==null||(mc.player.containerMenu.getCarried().isEmpty()&&mc.player.inventoryMenu.getCarried().isEmpty());}
            public boolean connected(){return mc.getConnection()!=null;}
            public void pause(){DoughBayClient.automationSessionController().pause("Hive setup for a fresh account");}
            public void disconnect(){mc.disconnectWithSavingScreen();}
        });
        setup=joining.status();if(done){awaitingRestart=true;join=null;joining=null;}
    }
}
