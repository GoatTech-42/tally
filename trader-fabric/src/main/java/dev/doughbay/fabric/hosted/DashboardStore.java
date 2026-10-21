package dev.doughbay.fabric.hosted;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.util.*;

/** Installation secrets are outside the hive folder and never included in toString or telemetry. */
public final class DashboardStore {
    private static final ObjectMapper JSON=new ObjectMapper();
    private final Path file;
    public static final class Link {
        public final long installationId;public final String credential,name;
        public Link(long id,String credential,String name){
            if(id<=0||credential==null||!credential.matches("gni1_[A-Za-z0-9_-]{43}"))throw new IllegalArgumentException("Invalid installation credential");
            this.installationId=id;this.credential=credential;this.name=DashboardProtocol.label(name);
        }
        @Override public String toString(){return "Dashboard installation "+installationId;}
    }
    public DashboardStore(Path gameDirectory){file=gameDirectory.resolve("gonuts-dashboard/installation.json");}
    public Link load() throws Exception {
        if(!Files.exists(file))return null;
        if(Files.size(file)>8192)throw new java.io.IOException("Invalid local dashboard file");
        restrict(file);var j=JSON.readTree(Files.readAllBytes(file));
        return new Link(j.path("installationId").asLong(),j.path("credential").asText(),j.path("name").asText());
    }
    public void save(Link link) throws Exception {
        Files.createDirectories(file.getParent());restrict(file.getParent());
        Path temp=Files.createTempFile(file.getParent(),".installation-",".tmp");
        try {
            restrict(temp);
            var j=JSON.createObjectNode().put("installationId",link.installationId).put("credential",link.credential).put("name",link.name);
            Files.write(temp,JSON.writeValueAsBytes(j));
            Files.move(temp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        }finally{Files.deleteIfExists(temp);}
    }
    public void forget() throws Exception {Files.deleteIfExists(file);}
    public static void restrict(Path path) throws Exception {
        var acl=Files.getFileAttributeView(path,AclFileAttributeView.class);
        if(acl!=null){
            var entry=AclEntry.newBuilder().setType(AclEntryType.ALLOW).setPrincipal(Files.getOwner(path)).setPermissions(EnumSet.allOf(AclEntryPermission.class));
            if(Files.isDirectory(path))entry.setFlags(AclEntryFlag.FILE_INHERIT,AclEntryFlag.DIRECTORY_INHERIT);
            acl.setAcl(List.of(entry.build()));return;
        }
        var posix=Files.getFileAttributeView(path,PosixFileAttributeView.class);
        if(posix!=null){posix.setPermissions(PosixFilePermissions.fromString(Files.isDirectory(path)?"rwx------":"rw-------"));return;}
        throw new java.io.IOException("Private file permissions unavailable");
    }
    public static String hive(Path sharedDirectory) throws Exception {
        Files.createDirectories(sharedDirectory);Path path=sharedDirectory.resolve("dashboard-hive-id.txt");
        try(var channel=java.nio.channels.FileChannel.open(sharedDirectory.resolve("dashboard-hive.lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE);
            var lock=channel.lock()) {
            if(!Files.exists(path))Files.writeString(path,"hive_"+UUID.randomUUID().toString().replace("-",""),StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE);
            String value=Files.readString(path).strip();
            if(!value.matches("hive_[a-f0-9]{32}"))throw new java.io.IOException("Invalid shared dashboard group");
            return value;
        }
    }
}
