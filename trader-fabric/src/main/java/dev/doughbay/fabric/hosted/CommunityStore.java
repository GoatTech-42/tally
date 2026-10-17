package dev.doughbay.fabric.hosted;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;

/** Per-installation opt-in, credential and one durable retry batch. Never stored in the shared hive. */
public final class CommunityStore {
    private final Path root;
    public CommunityStore(Path game){root=game.resolve("gonuts-community");}
    public ObjectNode read(String name)throws Exception{
        validName(name);
        Path file=root.resolve(name);if(!Files.exists(file))return null;
        if(Files.size(file)>600_000)throw new java.io.IOException("Invalid community settings");
        DashboardStore.restrict(file);var json=DashboardProtocol.JSON.readTree(Files.readAllBytes(file));
        return json instanceof ObjectNode node?node:null;
    }
    public void write(String name,ObjectNode json)throws Exception{
        validName(name);
        Files.createDirectories(root);DashboardStore.restrict(root);Path temp=Files.createTempFile(root,".community-",".tmp");
        try{DashboardStore.restrict(temp);Files.write(temp,DashboardProtocol.JSON.writeValueAsBytes(json));Files.move(temp,root.resolve(name),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
        finally{Files.deleteIfExists(temp);}
    }
    public void remove(String name)throws Exception{validName(name);Files.deleteIfExists(root.resolve(name));}
    private static void validName(String name){if(!java.util.Set.of("settings.json","pending.json","performance.json","order-shadow.json","snapshot-validation.json").contains(name))throw new IllegalArgumentException();}
}
