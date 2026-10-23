package dev.doughbay.fabric;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.doughbay.fabric.hosted.DashboardProtocol;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Supplier;

/** Bounded typed diagnostic outbox on the existing dashboard transport. Never reads a raw log. */
public final class DiagnosticHistory {
    public static final long RETENTION_MS=86_400_000L;
    public static final int MAX_EVENTS=256, MAX_CONTEXT_BYTES=4096, MAX_BATCH_BYTES=24*1024;
    private record Entry(long sequence,long at,String context,String settings) {}
    private final ArrayDeque<Entry> pending=new ArrayDeque<>();
    private String account="",session=UUID.randomUUID().toString(),clientId="",ticket="",fingerprint="",lastSettings="",lastProblem="";
    private long sequence,lastCheck,lastCapture,expiresAt,deliveredThrough,dropped;
    private boolean supported;

    public synchronized void welcome(JsonNode frame,long now) {
        supported=frame.path("diagnosticHistorySchema").isIntegralNumber()
                &&frame.path("diagnosticHistorySchema").asInt()==1
                &&frame.path("swarmClientId").asText().matches("sc_[a-f0-9]{32}");
        clientId=supported?frame.path("swarmClientId").asText():"";
        ticket="";expiresAt=0;
    }
    public synchronized void token(JsonNode frame,long now){
        if(!supported||!clientId.equals(frame.path("swarmClientId").asText()))return;
        String candidate=frame.path("token").asText();long expiry=frame.path("expiresAt").asLong();
        long issued=frame.path("issuedAt").asLong();
        long remaining=issued>0?expiry-issued:expiry-now;
        if(candidate.matches("drt_[A-Za-z0-9_-]{32,128}")&&remaining>0&&remaining<=RETENTION_MS+600_000){ticket=candidate;expiresAt=now+Math.min(RETENTION_MS,remaining);}
    }
    /** Disconnect drops the report capability; retained unsent events can retry after a compatible welcome. */
    public synchronized void disconnected(){supported=false;ticket="";expiresAt=0;}
    private void clearData(){pending.clear();account="";session=UUID.randomUUID().toString();sequence=lastCheck=lastCapture=deliveredThrough=dropped=0;fingerprint="";lastSettings="";lastProblem="";}
    public synchronized void clear(){clearData();clientId="";disconnected();}
    public synchronized boolean accountChanged(String current){return !account.isEmpty()&&!account.equals(current);}
    public synchronized boolean due(String current,boolean sharing,long now){
        if(!sharing||current==null||current.isBlank()){clearData();return false;}
        if(!account.isEmpty()&&!account.equals(current)){clear();account=current;return false;}
        if(account.isEmpty())account=current;
        if(!supported||now<=0||lastCheck>0&&now-lastCheck<5000)return false;
        lastCheck=now;return true;
    }
    public synchronized void capture(String current,boolean sharing,long now,Supplier<String> context,Supplier<String> settings){
        if(!due(current,sharing,now))return;
        try {
            String safe=sanitizeContext(context.get());
            String code=settings.get();SettingsCode.validateReportCode(code);
            if(safe.isEmpty()||code==null||code.isEmpty())return;
            String shape=shape(safe)+code;
            String problem=shape(safe.lines().filter(line->line.contains("fault_")||line.matches(".*event=(REFUSED|FAULT|ABORTED|EMERGENCY_STOPPED).*"))
                    .collect(java.util.stream.Collectors.joining("\n")));
            boolean urgent=!code.equals(lastSettings)||!problem.equals(lastProblem);
            if(!urgent&&lastCapture>0&&now-lastCapture<30_000)return;
            if(shape.equals(fingerprint)&&lastCapture>0&&now-lastCapture<60_000)return;
            fingerprint=shape;lastSettings=code;lastProblem=problem;lastCapture=now;prune(now);
            while(pending.size()>=MAX_EVENTS){pending.removeFirst();dropped++;}
            pending.addLast(new Entry(++sequence,now,safe,code));
        }catch(RuntimeException ignored){/* Evidence is optional; never disturb the native trading lifecycle. */}
    }
    /** Only closed typed context fields survive. No chat, paths, raw exceptions or arbitrary strings. */
    public static String sanitizeContext(String source){
        if(source==null||source.length()>32768)return "";
        Set<String> seen=new HashSet<>();StringBuilder out=new StringBuilder();
        int lines=0;
        for(String line:source.split("\\R")){
            if(!ReportContextLine.valid(line,seen)&&!(line.startsWith("act ")&&line.equals(ActionTrace.sanitizedLogLine("GoNuts action "+line))))continue;
            if(++lines>16)break;
            if((out.toString()+line+"\n").getBytes(StandardCharsets.UTF_8).length>MAX_CONTEXT_BYTES)break;
            out.append(line).append('\n');
        }
        return out.toString();
    }
    private static String shape(String context){
        StringBuilder out=new StringBuilder();
        for(String line:context.lines().toList())for(String pair:line.substring(4).split(" ")){
            String key=pair.substring(0,pair.indexOf('='));
            if(!key.endsWith("_age_ms")&&!Set.of("captured_at_ms","session_elapsed_ms","accepted_generation","durable_generation","age_ms","duration_ms").contains(key))out.append(pair).append(' ');
        }
        return out.toString();
    }
    private void prune(long now){while(!pending.isEmpty()&&(pending.peekFirst().at<now-RETENTION_MS||pending.peekFirst().at>now)){pending.removeFirst();dropped++;}}
    public synchronized ObjectNode batch(String current,boolean sharing,long now){
        if(!sharing||!account.equals(current)){clearData();return null;}
        if(!supported)return null;prune(now);if(pending.isEmpty())return null;
        ObjectNode out=DashboardProtocol.JSON.createObjectNode().put("type","diagnostic_history").put("version",1).put("sessionId",session);
        var events=out.putArray("events");
        for(Entry e:pending){
            events.addObject().put("sequence",e.sequence).put("observedAt",e.at).put("context",e.context).put("settingsCode",e.settings);
            if(out.toString().getBytes(StandardCharsets.UTF_8).length>MAX_BATCH_BYTES){events.remove(events.size()-1);break;}
            if(events.size()>=8)break;
        }
        return events.isEmpty()?null:out;
    }
    public synchronized void sent(ObjectNode batch){
        if(!session.equals(batch.path("sessionId").asText()))return;
        for(JsonNode e:batch.path("events"))deliveredThrough=Math.max(deliveredThrough,e.path("sequence").asLong());
    }
    public synchronized void acknowledge(JsonNode ack){
        if(!supported||!session.equals(ack.path("sessionId").asText())||!ack.path("throughSequence").isIntegralNumber())return;
        long through=ack.path("throughSequence").asLong();if(through<=0||through>deliveredThrough)return;
        while(!pending.isEmpty()&&pending.peekFirst().sequence<=through)pending.removeFirst();
    }
    public synchronized String reportToken(String current,boolean sharing,long now){return supported&&sharing&&account.equals(current)&&now<expiresAt?ticket:"";}
    public synchronized long droppedEvents(){return dropped;}
    public synchronized String clientId(){return supported?clientId:"";}
}
