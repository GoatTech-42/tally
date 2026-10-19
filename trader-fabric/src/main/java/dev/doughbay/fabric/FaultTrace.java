package dev.doughbay.fabric;

import java.util.*;

/** Bounded process-local fault evidence. Never retains exception messages, identities or raw throwables. */
public final class FaultTrace {
    public enum Stage {NONE,MARKET_KEYLESS,MARKET_API,CONTROLLER_TICK,DRIVER_COMMAND,DRIVER_CLICK,PREPARE_INVENTORY,COMMUNITY_HISTORY,HIVE_SETUP,DASHBOARD_LINK,COMMUNITY_UPLOAD,EVASION_ROUTE,RESTART_ROUTE}
    public static final FaultTrace INSTANCE=new FaultTrace();
    private static final Set<String> TYPES=Set.of("NullPointerException","IllegalStateException","IllegalArgumentException",
            "IOException","SocketException","TimeoutException","ConnectException","SQLException","SQLiteException");
    private Stage stage=Stage.NONE;
    private String type="NONE",frames="";
    private long at,count;
    public synchronized void record(Stage where,Throwable error,long now){
        if(error==null||where==null||where==Stage.NONE)return;
        var scope=switch(where){
            case CONTROLLER_TICK -> ActionTrace.Scope.CONTROLLER;
            case DRIVER_COMMAND,DRIVER_CLICK,PREPARE_INVENTORY -> ActionTrace.Scope.DRIVER;
            case COMMUNITY_HISTORY,COMMUNITY_UPLOAD -> ActionTrace.Scope.COMMUNITY;
            case HIVE_SETUP -> ActionTrace.Scope.HIVE;
            case DASHBOARD_LINK -> ActionTrace.Scope.DASHBOARD;
            case EVASION_ROUTE -> ActionTrace.Scope.EVASION;
            case RESTART_ROUTE -> ActionTrace.Scope.RESTART;
            default -> ActionTrace.Scope.MARKET;
        };
        ActionTrace.INSTANCE.record(scope,ActionTrace.Event.FAULT,where,"UNCLASSIFIED",-1,-1,-1);
        stage=where;at=now;count++;type="OTHER_EXCEPTION";
        var verified=new LinkedHashSet<String>();var seen=Collections.newSetFromMap(new IdentityHashMap<Throwable,Boolean>());
        for(Throwable cause=error;cause!=null&&seen.size()<8&&seen.add(cause);cause=cause.getCause()){
            String name=cause.getClass().getSimpleName();type=TYPES.contains(name)?name:"OTHER_EXCEPTION";
            int inspected=0;
            for(var frame:cause.getStackTrace()){
                if(++inspected>128||verified.size()>=4)break;
                if(!frame.getClassName().startsWith("dev.doughbay."))continue;
                String safe=BugReportLog.sanitize("  at "+frame);
                safe.lines().filter(line->line.startsWith("  at dev.doughbay.")).forEach(verified::add);
            }
        }
        frames=String.join("\n",verified);
    }
    public synchronized String report(long now){
        return "ctx fault_stage="+stage.name()+" fault_type="+type+" fault_age_ms="+(at<=0||now<at?-1:now-at)
                +" fault_count="+count+"\n"+(frames.isEmpty()?"":frames+"\n");
    }
}
