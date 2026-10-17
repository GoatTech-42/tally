package dev.doughbay.fabric.hosted;

import java.nio.file.*;
import java.util.*;

/** Successful ledger observations only. Never reconstructs offline or missing intervals. Worker-thread only. */
final class SwarmRankCoverage {
    static final long WINDOW=86_400_000L, MAX_GAP=60_000L;
    private final Path file;
    private final List<long[]> intervals=new ArrayList<>();
    private long last;
    private boolean valid=true,continuous;
    SwarmRankCoverage(Path file) {
        this.file=file;
        if(!Files.exists(file))return;
        try {
            if(Files.size(file)>100_000)throw new IllegalArgumentException();
            var root=DashboardProtocol.JSON.readTree(Files.readAllBytes(file));
            if(root.path("v").asInt()!=1||!root.path("intervals").isArray())throw new IllegalArgumentException();
            if(!root.path("last").isIntegralNumber())throw new IllegalArgumentException();
            last=root.path("last").asLong();continuous=root.path("continuous").asBoolean(false);long end=0;
            if(last<=0)throw new IllegalArgumentException();
            for(var i:root.path("intervals")) {
                if(!i.isArray()||i.size()!=2||!i.get(0).isIntegralNumber()||!i.get(1).isIntegralNumber())throw new IllegalArgumentException();
                long a=i.get(0).asLong(),b=i.get(1).asLong();
                if(a<=end||b<=a||b>last)throw new IllegalArgumentException();
                intervals.add(new long[]{a,b});end=b;
            }
        }catch(Exception bad){valid=false;intervals.clear();}
    }
    double observe(long at) {
        if(!valid||at<=0||at<last)return 0;
        intervals.removeIf(i->i[1]<=at-WINDOW);
        if(continuous&&last>0&&at>last&&at-last<=MAX_GAP) {
            if(!intervals.isEmpty()&&intervals.getLast()[1]==last)intervals.getLast()[1]=at;
            else intervals.add(new long[]{last,at});
        }
        last=at;continuous=true;
        if(intervals.size()>2000){valid=false;return 0;}
        if(!save())return 0;
        long covered=0;for(var i:intervals)covered+=Math.max(0,i[1]-Math.max(i[0],at-WINDOW));
        return Math.min(1,(double)covered/WINDOW);
    }
    void gap(){continuous=false;if(valid&&last>0)save();}
    private boolean save() {
        try {
            Files.createDirectories(file.getParent());
            var j=DashboardProtocol.JSON.createObjectNode().put("v",1).put("last",last).put("continuous",continuous);
            var list=j.putArray("intervals");for(var i:intervals)list.addArray().add(i[0]).add(i[1]);
            Path temp=Files.createTempFile(file.getParent(),"rank-coverage-",".tmp");
            try{Files.write(temp,DashboardProtocol.JSON.writeValueAsBytes(j));Files.move(temp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
            finally{Files.deleteIfExists(temp);}
        }catch(Exception failure){valid=false;return false;}
        return true;
    }
}
