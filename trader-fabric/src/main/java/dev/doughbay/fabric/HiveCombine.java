package dev.doughbay.fabric;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.channels.*;
import java.nio.file.*;
import java.util.*;
import dev.doughbay.storage.HiveLedgerImport;

/** Explicit restart-only combination of two single-account hives. Never modifies either original ledger. */
final class HiveCombine {
    private static final ObjectMapper JSON=new ObjectMapper();
    private static final String REQUEST="gonuts-hive-combine.json",REDIRECT="hive-combined-into.json",READY="hive-combine-ready.json";
    private static final Map<Path,Lease> HELD=new HashMap<>();
    static volatile String startupMessage="";
    record Plan(int version,HiveSetup.Membership first,HiveSetup.Membership second,HiveSetup.Membership combined) {}
    private HiveCombine() {}
    static synchronized void hold(Path folder) throws IOException {
        Path real=folder.toRealPath();if(HELD.containsKey(real))return;
        HELD.put(real,lease(real,true));
    }
    private static Lease lease(Path folder,boolean shared) throws IOException {
        FileChannel channel=FileChannel.open(folder.resolve("hive-use.lock"),StandardOpenOption.CREATE,StandardOpenOption.READ,StandardOpenOption.WRITE);
        try {
            FileLock lock=channel.tryLock(0,Long.MAX_VALUE,shared);
            if(lock==null)throw new IOException("Close both Minecraft instances, then start the instance where you requested Combine first");
            return new Lease(channel,lock);
        }catch(IOException|RuntimeException e){channel.close();throw new IOException("Close both Minecraft instances, then start the requesting instance first",e);}
    }
    private record Lease(FileChannel channel,FileLock lock) implements AutoCloseable {
        @Override public void close() throws IOException {try{lock.release();}finally{channel.close();}}
    }
    static void queue(Path game,String otherCode) throws Exception {
        var own=HiveSetup.configured(game);var other=HiveSetup.decode(otherCode);
        if(own==null)throw new IOException("Create your first hive or join an existing hive before using Combine");
        if(own.id().equals(other.id())||own.owner().equalsIgnoreCase(other.owner()))throw new IOException("These are already the same hive or account");
        for(var m:List.of(own,other)) {
            Path dir=Path.of(m.directory());
            if(Files.exists(dir.resolve("member-profiles")))throw new IOException("This hive is already combined; adding further members needs a separate migration");
            HiveLedgerImport.inspect(dir.resolve("doughbay.db"),m.owner());
        }
        String id=UUID.randomUUID().toString();
        Path destination=Path.of(own.directory()).resolveSibling("gonuts-combined-"+id);
        Plan plan=new Plan(1,other,own,new HiveSetup.Membership(1,id,destination.toString(),other.owner()));
        writeNew(game.resolve(REQUEST),plan);
    }
    static void completePending(Path game) throws IOException {
        Path request=game.resolve(REQUEST);if(!Files.exists(request))return;
        try {
            if(Files.size(request)>24_576)throw new IOException("Invalid pending hive combination");
            Plan p=JSON.readValue(Files.readAllBytes(request),Plan.class);
            if(p.version()!=1)throw new IOException("Unsupported hive combination");
            complete(p);
            // Preserve the exact request as a local receipt rather than deleting it.
            Files.move(request,game.resolve("gonuts-hive-combine-"+p.combined().id()+".applied.json"));
            startupMessage="Hives combined. Both original folders were kept. Restart the other instance to use the shared ledger.";
        } catch(Exception e) {
            startupMessage="Combination did not finish. Close both Minecraft instances and start this one first. Original ledgers were kept.";
            // Before link publication a failed import leaves both original hives fully usable.
            Plan p=JSON.readValue(Files.readAllBytes(request),Plan.class);
            if(!Files.exists(Path.of(p.first().directory()).resolve(REDIRECT))&&!Files.exists(Path.of(p.second().directory()).resolve(REDIRECT)))return;
            // A pending redirect deliberately prevents an interrupted combination becoming two divergent ledgers.
            throw new IOException(startupMessage,e);
        }
    }
    static void complete(Plan p) throws Exception {
        complete(p,Path.of(System.getProperty("user.home"),".gonuts","local-presence"));
    }
    static void complete(Plan p,Path presence) throws Exception {
        Path a=Path.of(p.first().directory()).toRealPath(),b=Path.of(p.second().directory()).toRealPath(),out=Path.of(p.combined().directory()).toAbsolutePath().normalize();
        if(a.equals(b)||out.equals(a)||out.equals(b)||out.startsWith(a)||out.startsWith(b))throw new IOException("Invalid combined hive destination");
        UUID.fromString(p.combined().id());
        if(p.version()!=1||!out.getFileName().toString().equals("gonuts-combined-"+p.combined().id())||!out.getParent().equals(b.getParent())
                ||!p.combined().owner().equals(p.first().owner()))throw new IOException("Invalid combination request");
        if(!p.first().owner().matches("[A-Za-z0-9_.-]{1,32}")||!p.second().owner().matches("[A-Za-z0-9_.-]{1,32}"))throw new IOException("Invalid hive account");
        for(var m:List.of(p.first(),p.second())) {
            var actual=HiveSetup.read(Path.of(m.directory()).resolve("hive.json"));
            if(!actual.id().equals(m.id())||!actual.owner().equals(m.owner())||!actual.directory().equals(m.directory()))throw new IOException("Hive membership changed; originals were kept");
        }
        try(var lockA=lease(a,false);var lockB=lease(b,false)) {
            // Updated clients hold the leases for their whole process. Older compatible builds also advertise local presence.
            rejectRunning(p.first().id(),presence);rejectRunning(p.second().id(),presence);
            if(Files.exists(out.resolve(READY))) {verifyReady(out,p);return;}
            if(!Files.exists(out.resolve("hive-combine-verified.json"))) {
                if(Files.exists(out)) {
                    if(!out.getFileName().toString().equals("gonuts-combined-"+p.combined().id())||!out.getParent().equals(b.getParent()))throw new IOException("Invalid import staging folder");
                    Files.move(out,out.resolveSibling(out.getFileName()+".incomplete-"+UUID.randomUUID()));
                }
                Files.createDirectory(out);
                HiveLedgerImport.combine(a.resolve("doughbay.db"),p.first().owner(),b.resolve("doughbay.db"),p.second().owner(),out.resolve("doughbay.db"));
                profile(a,out,p.first().owner());profile(b,out,p.second().owner());
                Files.copy(a.resolve("config.json"),out.resolve("config.json"));
                var rootConfig=(com.fasterxml.jackson.databind.node.ObjectNode)JSON.readTree(Files.readAllBytes(out.resolve("config.json")));
                rootConfig.put("databasePath","doughbay.db");Files.write(out.resolve("config.json"),JSON.writeValueAsBytes(rootConfig));
                writeNew(out.resolve("hive.json"),p.combined());
                writeNew(out.resolve("hive-combine-verified.json"),p);
            } else {
                Plan verified=JSON.readValue(Files.readAllBytes(out.resolve("hive-combine-verified.json")),Plan.class);
                if(!verified.equals(p))throw new IOException("Interrupted combination does not match this request");
            }
            // Publish only after a complete verified import. Both redirects remain unusable until the final commit marker.
            redirect(a,p);redirect(b,p);guardOldBuilds(a);guardOldBuilds(b);
            writeNew(out.resolve(READY),p);
            verifyReady(out,p);
        }
    }
    static void cancelPending(Path game) throws Exception {
        Path file=game.resolve(REQUEST);if(!Files.exists(file))return;
        Plan p=JSON.readValue(Files.readAllBytes(file),Plan.class);
        if(Files.exists(Path.of(p.first().directory()).resolve(REDIRECT))||Files.exists(Path.of(p.second().directory()).resolve(REDIRECT)))
            throw new IOException("Combination link publication already began. Start the requesting instance to finish safely");
        Files.move(file,game.resolve("gonuts-hive-combine-"+p.combined().id()+".cancelled.json"));
        startupMessage="Queued combination cancelled. Both original hives and all settings remain intact.";
    }
    private static void guardOldBuilds(Path root) throws IOException {
        Path manifest=root.resolve("hive.json");var original=HiveSetup.read(manifest);if(original.version()==2)return;
        Path backup=root.resolve("hive-before-combine.json");if(!Files.exists(backup))Files.copy(manifest,backup);
        Path temp=Files.createTempFile(root,"hive-guard-",".tmp");
        try {
            Files.write(temp,JSON.writeValueAsBytes(new HiveSetup.Membership(2,original.id(),original.directory(),original.owner())));
            Files.move(temp,manifest,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        }finally{Files.deleteIfExists(temp);}
    }
    private static void profile(Path source,Path out,String owner) throws Exception {
        Path destination=out.resolve("member-profiles").resolve(owner.toLowerCase(Locale.ROOT));Files.createDirectories(destination);
        for(String name:List.of("config.json","tuning.json","secret.dat"))copyIfPresent(source.resolve(name),destination.resolve(name));
        Path config=destination.resolve("config.json");var j=JSON.readTree(Files.readAllBytes(config));
        if(!(j instanceof com.fasterxml.jackson.databind.node.ObjectNode obj))throw new IOException("Invalid source settings");
        obj.put("databasePath",out.resolve("doughbay.db").toString());
        Files.write(config,JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(obj));
        for(String file:AccountRecords.FILES) {
            Path own=source.resolve("accounts").resolve(owner).resolve(file);
            copyIfPresent(Files.exists(own)?own:source.resolve(file),destination.resolve(file));
        }
    }
    private static void copyIfPresent(Path source,Path destination) throws IOException {
        if(!Files.exists(source))return;
        if(!Files.isRegularFile(source)||Files.isSymbolicLink(source))throw new IOException("Unsupported source file; original was kept");
        Files.copy(source,destination);
    }
    private static void rejectRunning(String hive,Path root) throws Exception {
        if(!Files.isDirectory(root))return;
        try(var files=Files.list(root)) {
            for(var file:files.filter(f->f.getFileName().toString().matches("[a-f0-9]{64}\\.json")).limit(128).toList()) {
                if(Files.isSymbolicLink(file)||Files.size(file)>2048)continue;
                var j=JSON.readTree(Files.readAllBytes(file));long pid=j.path("pid").asLong(-1);
                if(hive.equals(j.path("hive").asText())&&pid>0&&pid!=ProcessHandle.current().pid()
                        &&ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false))throw new IOException("Another hive instance is still running");
            }
        }
    }
    private static void redirect(Path source,Plan p) throws Exception {
        Path file=source.resolve(REDIRECT);
        if(Files.exists(file)){if(!JSON.readValue(Files.readAllBytes(file),Plan.class).equals(p))throw new IOException("A different combination is pending");return;}
        writeNew(file,p);
    }
    static HiveSetup.Membership follow(HiveSetup.Membership membership) throws IOException {
        Path file=Path.of(membership.directory()).resolve(REDIRECT);if(!Files.exists(file))return membership;
        if(Files.size(file)>24_576)throw new IOException("Invalid combined hive link");
        Plan p=JSON.readValue(Files.readAllBytes(file),Plan.class);
        if(!membership.id().equals(p.first().id())&&!membership.id().equals(p.second().id()))throw new IOException("Combined hive link does not match the original");
        Path out=Path.of(p.combined().directory());verifyReady(out,p);return p.combined();
    }
    private static void verifyReady(Path root,Plan p) throws IOException {
        Path ready=root.resolve(READY);
        if(!Files.isRegularFile(ready)||Files.size(ready)>24_576||!JSON.readValue(Files.readAllBytes(ready),Plan.class).equals(p))
            throw new IOException("Hive combination is pending. Start the requesting instance first; no replacement ledger was created");
    }
    static Path configuration(HiveSetup.Membership membership,String account) throws IOException {
        Path root=Path.of(membership.directory());if(!Files.isDirectory(root.resolve("member-profiles")))return root;
        if(account==null||!account.matches("[A-Za-z0-9_.-]{1,32}"))throw new IOException("Sign into the hive account first");
        Path profile=root.resolve("member-profiles").resolve(account.toLowerCase(Locale.ROOT));
        if(!Files.isRegularFile(profile.resolve("config.json")))throw new IOException("No preserved settings profile for this hive account");return profile;
    }
    private static void writeNew(Path file,Object value) throws IOException {
        // Complete temp file then atomically claim the destination; never replace an existing source or receipt.
        Path temp=Files.createTempFile(file.getParent(),"hive-combine-",".tmp");
        try{Files.write(temp,JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(value));Files.move(temp,file);}
        finally{Files.deleteIfExists(temp);}
    }
}
