package dev.doughbay.fabric;

import java.nio.file.*;
import java.util.*;

/** Local tutorial preferences only. Existing installations never receive an automatic tour. */
final class TutorialProgress {
    private static TutorialProgress current;
    private final Path file;
    private boolean offer, readable=true;
    private final Set<String> completed=new HashSet<>();
    static void initialize(Path directory,boolean existingInstall){current=new TutorialProgress(directory,existingInstall);}
    static boolean takeInitialOffer(){return current!=null&&current.takeOffer();}
    static void completed(String course){if(current!=null)current.complete(course);}
    TutorialProgress(Path directory,boolean existingInstall){
        file=directory.resolve("tutorial-progress.json");
        if(Files.exists(file)){
            try{
                if(Files.size(file)>16_384)throw new IllegalArgumentException();
                var json=dev.doughbay.fabric.hosted.DashboardProtocol.JSON.readTree(Files.readAllBytes(file));
                if(json.path("v").asInt()!=1||!json.path("offer").isBoolean())throw new IllegalArgumentException();
                offer=json.path("offer").asBoolean();
                for(var item:json.path("completed"))if(item.isTextual())completed.add(item.asText());
            }catch(Exception e){readable=false;offer=false;}
        }else{offer=!existingInstall;save();}
    }
    boolean takeOffer(){
        if(!offer)return false;
        offer=false;return save();
    }
    void complete(String id){completed.add(id);save();}
    boolean isComplete(String id){return completed.contains(id);}
    private boolean save(){
        if(!readable)return false;
        Path temp=null;
        try{
            Files.createDirectories(file.getParent());
            var json=dev.doughbay.fabric.hosted.DashboardProtocol.JSON.createObjectNode().put("v",1).put("offer",offer);
            var list=json.putArray("completed");completed.stream().sorted().forEach(list::add);
            temp=Files.createTempFile(file.getParent(),"tutorial-",".tmp");
            Files.write(temp,dev.doughbay.fabric.hosted.DashboardProtocol.JSON.writeValueAsBytes(json));
            Files.move(temp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);return true;
        }catch(Exception e){return false;}
        finally{if(temp!=null)try{Files.deleteIfExists(temp);}catch(Exception ignored){}}
    }
}
