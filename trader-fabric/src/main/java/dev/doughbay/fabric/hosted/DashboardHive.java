package dev.doughbay.fabric.hosted;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;

/** Local hive IPC only. Pairing codes expire and are consumed before execution; credentials never enter the hive. */
public final class DashboardHive {
    private final Path root;
    private final String run=UUID.randomUUID().toString();
    public static final long FRESH_MS=15_000, REQUEST_MS=30_000;
    public DashboardHive(Path shared){root=shared.resolve("dashboard-local-ipc");}
    public static String id(String account){
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(account.toLowerCase(java.util.Locale.ROOT).getBytes(StandardCharsets.UTF_8)));}
        catch(Exception e){throw new IllegalStateException();}
    }
    private void prepare() throws Exception {Files.createDirectories(root);DashboardStore.restrict(root);}
    private void write(Path path,ObjectNode value) throws Exception {
        prepare();Path temp=Files.createTempFile(root,".ipc-",".tmp");
        try{DashboardStore.restrict(temp);Files.write(temp,DashboardProtocol.JSON.writeValueAsBytes(value));Files.move(temp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
        finally{Files.deleteIfExists(temp);}
    }
    private ObjectNode read(Path path) throws Exception {
        if(!Files.isRegularFile(path)||Files.size(path)>4096)return null;
        var value=DashboardProtocol.JSON.readTree(Files.readAllBytes(path));return value instanceof ObjectNode node?node:null;
    }
    public void publish(String account,HostedDashboardClient.View view,long now) throws Exception {
        if(account==null||account.isBlank())return;
        write(root.resolve(id(account)+".status"),DashboardProtocol.JSON.createObjectNode().put("at",now)
            .put("run",run).put("linked",view.linked()).put("online",view.online()).put("busy",view.busy()).put("name",view.name())
            .put("status",view.status()).put("retryAt",view.retryAt()).set("community",CommunityClient.INSTANCE.view()));
    }
    public ObjectNode status(String account,long now){
        try{var value=read(root.resolve(id(account)+".status"));if(value==null||now-value.path("at").asLong()>FRESH_MS||value.path("at").asLong()>now+5000)return null;return value;}
        catch(Exception e){return null;}
    }
    public synchronized boolean post(String account,String action,String code,String name,long now) throws Exception {
        if(!Set.of("link","unlink","reconnect","community_enable","community_disable").contains(action))throw new IllegalArgumentException("Unknown dashboard action");
        var current=status(account,now);if(current==null||current.path("busy").asBoolean())return false;
        if(action.equals("link")&&(current.path("linked").asBoolean()||now<current.path("retryAt").asLong()))return false;
        if(!action.equals("link")&&!action.equals("community_disable")&&!current.path("linked").asBoolean())return false;
        var request=DashboardProtocol.JSON.createObjectNode().put("at",now).put("run",current.path("run").asText()).put("action",action);
        if(action.equals("link"))request.put("code",DashboardProtocol.code(code)).put("name",DashboardProtocol.label(name));
        prepare();Path path=root.resolve(id(account)+".request");
        // One outstanding action per target. Never replace an earlier pairing request.
        try(var channel=java.nio.channels.FileChannel.open(root.resolve(id(account)+".lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE);var lock=channel.lock()){
            var previous=read(path);if(previous!=null&&now-previous.path("at").asLong()<REQUEST_MS)return false;
            write(path,request);return true;
        }
    }
    public synchronized void sweep(long now) throws Exception {
        if(!Files.isDirectory(root))return;
        try(var entries=Files.newDirectoryStream(root,"*.request")){
            for(Path path:entries){
                String key=path.getFileName().toString().replace(".request","");
                if(!key.matches("[a-f0-9]{64}"))continue;
                try(var channel=java.nio.channels.FileChannel.open(root.resolve(key+".lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE);var lock=channel.lock()){
                    var value=read(path);if(value==null||now-value.path("at").asLong()>REQUEST_MS)Files.deleteIfExists(path);
                }
            }
        }
        // An interrupted atomic writer/consumer may leave a private temporary file. Never retain its code indefinitely.
        try(var entries=Files.newDirectoryStream(root)){
            for(Path path:entries){String name=path.getFileName().toString();
                if((name.startsWith(".claimed-")||name.startsWith(".ipc-"))&&now-Files.getLastModifiedTime(path).toMillis()>REQUEST_MS)Files.deleteIfExists(path);
            }
        }
    }
    public synchronized ObjectNode take(String account,long now) throws Exception {
        if(account==null||account.isBlank())return null;
        Path path=root.resolve(id(account)+".request");if(!Files.exists(path))return null;
        Path claimed=root.resolve(".claimed-"+UUID.randomUUID());
        try(var channel=java.nio.channels.FileChannel.open(root.resolve(id(account)+".lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE);var lock=channel.lock()){
            try{Files.move(path,claimed,StandardCopyOption.ATOMIC_MOVE);}catch(NoSuchFileException e){return null;}
        }
        try{var value=read(claimed);if(value==null)return null;long at=value.path("at").asLong();
            if(at<=0||now-at>REQUEST_MS||at>now+5000||!run.equals(value.path("run").asText()))return null;
            if(!Set.of("link","unlink","reconnect","community_enable","community_disable").contains(value.path("action").asText()))return null;
            return value;
        }finally{Files.deleteIfExists(claimed);}
    }
}
