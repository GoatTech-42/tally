package dev.doughbay.fabric;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.util.*;

/** Same-OS-user discovery and expiring requests. Never contains installation credentials. */
final class PanelBotRegistry {
    static final ObjectMapper JSON=new ObjectMapper();
    static final long FRESH=15_000,REQUEST_LIFE=30_000;
    record Bot(String id,long at,long pid,String account,String game,String root,boolean linked,String setup,String connection,List<PanelCommandQueue.View> commands) {}
    private final Path directory;private final String run;
    PanelBotRegistry(Path directory,String run){this.directory=directory;this.run=UUID.fromString(run).toString();}
    String run(){return run;}
    void advertise(Bot bot) throws Exception {
        if(!bot.id().equals(run))throw new IllegalArgumentException();write(directory.resolve(run+".bot"),bot,false);
    }
    List<Bot> bots(long now) {
        var out=new ArrayList<Bot>();
        try(var files=Files.list(directory)) {
            for(Path path:files.filter(p->p.getFileName().toString().matches("[a-f0-9-]{36}\\.bot")).limit(128).toList())try{
                if(Files.isSymbolicLink(path)||Files.size(path)>32_768)continue;
                Bot b=JSON.readValue(Files.readAllBytes(path),Bot.class);
                if(b.connection()==null)continue;if(!b.connection().isEmpty())UUID.fromString(b.connection());
                if(!path.getFileName().toString().equals(UUID.fromString(b.id())+".bot")||b.at()>now+5000||now-b.at()>FRESH
                        ||b.pid()<=0||!ProcessHandle.of(b.pid()).map(ProcessHandle::isAlive).orElse(false)
                        ||!b.account().matches("[A-Za-z0-9_]{1,16}")||!Path.of(b.game()).isAbsolute()||!Path.of(b.root()).isAbsolute())continue;
                out.add(b);
            }catch(Exception ignored){}
        }catch(Exception ignored){}
        out.sort(Comparator.comparing(Bot::id));return List.copyOf(out);
    }
    Bot target(String id,long now){return bots(now).stream().filter(b->b.id().equals(id)).findFirst().orElseThrow(()->new IllegalArgumentException("Bot is offline or needs the current update"));}
    void post(String target,String id,String action,Map<String,String> fields,long now) throws Exception {
        target(target,now);UUID.fromString(id);
        if(fields.keySet().stream().anyMatch(Set.of("target","from","id","at","action")::contains))throw new IllegalArgumentException("Invalid request fields");
        var request=JSON.createObjectNode().put("target",target).put("from",run).put("id",id).put("at",now).put("action",action);
        fields.forEach(request::put);
        write(directory.resolve(target+"-"+id+".request"),request,true);
    }
    List<com.fasterxml.jackson.databind.node.ObjectNode> take(long now) throws Exception {
        var out=new ArrayList<com.fasterxml.jackson.databind.node.ObjectNode>();
        if(!Files.isDirectory(directory))return out;
        try(var files=Files.newDirectoryStream(directory,run+"-*.request")) {
            int count=0;for(Path p:files){if(++count>32)break;
                try{
                    if(Files.isSymbolicLink(p)||Files.size(p)>4096)continue;
                    var j=JSON.readTree(Files.readAllBytes(p));
                    if(!(j instanceof com.fasterxml.jackson.databind.node.ObjectNode node)||!run.equals(j.path("target").asText()))continue;
                    UUID.fromString(j.path("id").asText());target(j.path("from").asText(),now);
                    long at=j.path("at").asLong();if(at>now+5000||now-at>REQUEST_LIFE)continue;
                    out.add(node);
                }catch(Exception ignored){}finally{Files.deleteIfExists(p);}
            }
        }
        return out;
    }
    void sweep(long now) throws Exception {
        if(!Files.isDirectory(directory))return;
        try(var files=Files.list(directory)){
            for(Path p:files.limit(256).toList()){
                String name=p.getFileName().toString();
                if(name.matches("[a-f0-9-]{36}-[a-f0-9-]{36}\\.request")&&now-Files.getLastModifiedTime(p).toMillis()>REQUEST_LIFE)
                    Files.deleteIfExists(p);
                else if(name.matches("[a-f0-9-]{36}\\.bot")&&now-Files.getLastModifiedTime(p).toMillis()>REQUEST_LIFE
                        &&Files.isRegularFile(p)&&!Files.isSymbolicLink(p)&&Files.size(p)<=32_768)try{
                    var old=JSON.readTree(Files.readAllBytes(p));long pid=old.path("pid").asLong(-1);
                    if(pid>0&&!ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false))Files.deleteIfExists(p);
                }catch(Exception ignored){}
            }
        }
    }
    private void write(Path path,Object value,boolean fresh) throws Exception {
        Files.createDirectories(directory);dev.doughbay.fabric.hosted.DashboardStore.restrict(directory);
        Path temp=Files.createTempFile(directory,".panel-",".tmp");
        try{dev.doughbay.fabric.hosted.DashboardStore.restrict(temp);Files.write(temp,JSON.writeValueAsBytes(value));
            if(fresh)Files.move(temp,path);else Files.move(temp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        }finally{Files.deleteIfExists(temp);}
    }
}
