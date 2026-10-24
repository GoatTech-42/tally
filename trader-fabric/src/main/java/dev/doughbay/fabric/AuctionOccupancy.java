package dev.doughbay.fabric;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.util.*;

/** Reporting-only native auction counts, separate from order reads and ledger stock. */
public final class AuctionOccupancy {
    private static final ObjectMapper JSON=new ObjectMapper();
    public record Report(String account,long at,int capacity,int used,long auditAt) {}
    public record View(int capacity,int used,int fresh,int accounts,long oldestAuditAt,long sampledAt) {
        public String coverage(){return fresh+"/"+accounts+" current native counts"+(fresh==0?"; occupancy unknown":"; oldest audit "+(sampledAt-oldestAuditAt)/1000+"s ago");}
    }
    private static Path path(Path root,String account) {
        return root.resolve("auction-occupancy").resolve(OrderMetrics.path(root,account).getFileName());
    }
    public static void publish(Path root,String account,long now,int capacity,int used,long auditAt) throws Exception {
        publish(root,new Report(account,now,capacity,used,auditAt));
    }
    public static void publish(Path root,Report report) throws Exception {
        String account=report.account();
        Path target=path(root,account);Files.createDirectories(target.getParent());
        Path temp=Files.createTempFile(target.getParent(),"auction-",".tmp");
        try {Files.write(temp,JSON.writeValueAsBytes(report));
            Files.move(temp,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        } finally {Files.deleteIfExists(temp);}
    }
    public static View view(Path root,List<String> names,long now) {
        int capacity=0,used=0,fresh=0,knownCapacity=0;long oldest=Long.MAX_VALUE;
        for(String name:names)try {
            Path file=path(root,name);if(!Files.isRegularFile(file)||Files.size(file)>4096)continue;
            Report r=JSON.readValue(Files.readAllBytes(file),Report.class);
            if(!r.account().equalsIgnoreCase(name)||r.at()<=0||r.at()>now||now-r.at()>90_000
                    ||r.capacity()<1||r.capacity()>90)continue;
            capacity=Math.addExact(capacity,r.capacity());knownCapacity++;
            if(r.used()<0||r.used()>r.capacity()||r.auditAt()<=0||r.auditAt()>now||now-r.auditAt()>600_000)continue;
            used=Math.addExact(used,r.used());fresh++;oldest=Math.min(oldest,r.auditAt());
        }catch(Exception unavailable){/* unknown is not zero */}
        return new View(knownCapacity==names.size()&&!names.isEmpty()?capacity:-1,
                fresh==names.size()&&!names.isEmpty()?used:-1,fresh,names.size(),fresh==0?0:oldest,now);
    }
}
