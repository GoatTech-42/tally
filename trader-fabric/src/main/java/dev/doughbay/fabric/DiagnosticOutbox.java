package dev.doughbay.fabric;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.doughbay.fabric.hosted.DashboardProtocol;
import dev.doughbay.fabric.hosted.DashboardStore;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.sql.*;
import java.util.*;

/** Private diagnostics-only SQLite spool. Caller owns a background thread, never the trading database. */
public final class DiagnosticOutbox implements AutoCloseable {
    public static final long TTL=86_400_000L;
    public static final int MAX_EVENTS=32_000,MAX_FRAME=65_536;
    private final Connection db;
    private String session;
    private final String build,secret;
    private final int capacity;
    public DiagnosticOutbox(Path directory,String build,long now)throws Exception{this(directory,build,now,MAX_EVENTS);}
    DiagnosticOutbox(Path directory,String build,long now,int capacity)throws Exception{
        if(!build.matches("[0-9A-Za-z.+_-]{1,64}")||capacity<1)throw new IllegalArgumentException("Invalid diagnostic configuration");
        if(Files.isSymbolicLink(directory))throw new java.io.IOException("Diagnostic storage unavailable");
        Files.createDirectories(directory);DashboardStore.restrict(directory);
        Path file=directory.resolve("outbox.db");if(Files.isSymbolicLink(file))throw new java.io.IOException("Diagnostic storage unavailable");
        // Fabric may have already cached DriverManager discovery with the system loader.
        db=new org.sqlite.JDBC().connect("jdbc:sqlite:"+file,new Properties());DashboardStore.restrict(file);
        try(var s=db.createStatement()){
            s.execute("PRAGMA journal_mode=WAL");s.execute("PRAGMA synchronous=FULL");s.execute("PRAGMA busy_timeout=5000");
            s.execute("CREATE TABLE IF NOT EXISTS metadata(k TEXT PRIMARY KEY,v TEXT NOT NULL)");
            s.execute("CREATE TABLE IF NOT EXISTS sessions(id TEXT PRIMARY KEY,build TEXT NOT NULL,next_sequence INTEGER NOT NULL,created_at INTEGER NOT NULL,sent_through INTEGER NOT NULL DEFAULT0,acked_through INTEGER NOT NULL DEFAULT0,last_settings TEXT NOT NULL DEFAULT'')".replace("DEFAULT0","DEFAULT 0"));
            s.execute("CREATE TABLE IF NOT EXISTS settings(id TEXT PRIMARY KEY,code TEXT NOT NULL)");
            s.execute("CREATE TABLE IF NOT EXISTS events(session TEXT NOT NULL,sequence INTEGER NOT NULL,observed_at INTEGER NOT NULL,kind TEXT NOT NULL,lines TEXT NOT NULL,settings_id TEXT NOT NULL,PRIMARY KEY(session,sequence))");
            s.execute("CREATE TABLE IF NOT EXISTS gaps(session TEXT NOT NULL,first_seq INTEGER NOT NULL,last_seq INTEGER NOT NULL,reason TEXT NOT NULL,PRIMARY KEY(session,first_seq))");
        }
        String key=null;try(var s=db.prepareStatement("SELECT v FROM metadata WHERE k='secret'");var r=s.executeQuery()){if(r.next())key=r.getString(1);}
        if(key==null){byte[] random=new byte[32];new SecureRandom().nextBytes(random);key="gnd1_"+Base64.getUrlEncoder().withoutPadding().encodeToString(random);try(var s=db.prepareStatement("INSERT OR IGNORE INTO metadata(k,v) VALUES('secret',?)")){s.setString(1,key);s.executeUpdate();}try(var s=db.createStatement();var r=s.executeQuery("SELECT v FROM metadata WHERE k='secret'")){key=r.getString(1);}}
        if(!key.matches("gnd1_[A-Za-z0-9_-]{43}"))throw new java.io.IOException("Diagnostic identity unavailable");
        secret=key;session=UUID.randomUUID().toString();this.build=build;this.capacity=capacity;
        try(var s=db.prepareStatement("INSERT INTO sessions(id,build,next_sequence,created_at) VALUES(?,?,1,?)")){s.setString(1,session);s.setString(2,build);s.setLong(3,now);s.executeUpdate();}
        prune(now);
    }
    public String credential(){return secret;}
    public String session(){return session;}
    private String metadata(String key)throws SQLException{try(var s=db.prepareStatement("SELECT v FROM metadata WHERE k=?")){s.setString(1,key);try(var r=s.executeQuery()){return r.next()?r.getString(1):"";}}}
    private void metadata(String key,String value)throws SQLException{try(var s=db.prepareStatement("INSERT OR REPLACE INTO metadata VALUES(?,?)")){s.setString(1,key);s.setString(2,value);s.executeUpdate();}}
    public static boolean valid(String kind,List<String> lines){
        if(lines==null||lines.isEmpty()||lines.size()>32||lines.stream().anyMatch(l->l==null||l.length()>500)||String.join("\n",lines).getBytes(StandardCharsets.UTF_8).length>8192)return false;
        boolean ctx=false,act=false,dec=false,cov=false,state=false,fault=false,life=false,allRefused=true;int frames=0;
        var seen=new HashSet<String>();
        for(String line:lines){
            if(line.startsWith("ctx ")&&ReportContextLine.valid(line,seen)){ctx=true;fault|=line.contains("fault_stage=")&&!line.contains("fault_stage=NONE");}
            else if(line.startsWith("act ")&&line.equals(ActionTrace.sanitizedLogLine("GoNuts action "+line))){act=true;}
            else if(line.startsWith("dec ")&&DecisionTrace.valid(line)){dec=true;allRefused&=line.contains(" event=REFUSED ");}
            else if(DecisionTrace.validCoverage(line)){cov=true;}
            else if(line.matches("automation state=(SCANNING|BID_DESK|READING_ORDERS|AUDITING_SLOTS|PREPARING_LIST|LISTING|MONITORING|COOLDOWN|PAUSED|STOPPED|IDLE|BUYING|REPRICING|RECOVERING|EVADING|BUY_INTENT)")){state=true;}
            else if(line.matches("life event=(START|STOP|RECONNECT) build=[0-9A-Za-z.+_-]{1,64} reason=[A-Z][A-Z0-9_]{0,31}")){life=true;}
            else if(kind.equals("FAULT")&&line.matches(" {0,8}at dev\\.doughbay\\.[A-Za-z0-9_.$]+\\([A-Za-z0-9_$]+\\.java:[0-9]{1,9}\\)")&&++frames<=8){}
            else return false;
        }
        return switch(kind){case "SNAPSHOT"->ctx&&!act&&!dec&&!cov&&!state&&!life;case "ACTION"->act&&!dec&&!cov&&!state&&!life;case "DECISION"->dec&&!act&&!state&&!life;case "GATE"->dec&&allRefused&&!act&&!ctx&&!state&&!life;case "STATE"->state&&!act&&!dec&&!cov&&!life;case "FAULT"->fault&&!dec&&!state&&!life;case "LIFECYCLE"->life&&lines.size()==1;default->false;};
    }
    public synchronized long append(String kind,List<String> lines,String settings,long at)throws Exception{
        if(!valid(kind,lines)||at<=0)throw new IllegalArgumentException("Invalid typed diagnostic event");
        long sequence;String settingsId;
        try(var s=db.prepareStatement("SELECT next_sequence,last_settings FROM sessions WHERE id=?")){s.setString(1,session);try(var r=s.executeQuery()){r.next();sequence=r.getLong(1);settingsId=r.getString(2);}}
        if(settings!=null&&!settings.isEmpty()){SettingsCode.validateReportCode(settings);settingsId=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(settings.getBytes(StandardCharsets.UTF_8)));try(var s=db.prepareStatement("INSERT OR IGNORE INTO settings(id,code) VALUES(?,?)")){s.setString(1,settingsId);s.setString(2,settings);s.executeUpdate();}}
        if(settingsId.isEmpty()||sequence>=9_007_199_254_740_991L)throw new IllegalArgumentException("Session requires numeric settings");
        db.setAutoCommit(false);try{
            try(var s=db.prepareStatement("INSERT INTO events VALUES(?,?,?,?,?,?)")){s.setString(1,session);s.setLong(2,sequence);s.setLong(3,at);s.setString(4,kind);s.setString(5,DashboardProtocol.JSON.writeValueAsString(lines));s.setString(6,settingsId);s.executeUpdate();}
            try(var s=db.prepareStatement("UPDATE sessions SET next_sequence=?,last_settings=? WHERE id=?")){s.setLong(1,sequence+1);s.setString(2,settingsId);s.setString(3,session);s.executeUpdate();}db.commit();
        }catch(Exception e){db.rollback();throw e;}finally{db.setAutoCommit(true);}
        prune(at);return sequence;
    }
    private void gap(String id,long from,long to,String reason)throws SQLException{
        long first=from,last=to;String mergedReason=reason;
        try(var s=db.prepareStatement("SELECT first_seq,last_seq,reason FROM gaps WHERE session=? AND first_seq<=? AND last_seq>=?")){s.setString(1,id);s.setLong(2,to+1);s.setLong(3,from-1);try(var r=s.executeQuery()){while(r.next()){first=Math.min(first,r.getLong(1));last=Math.max(last,r.getLong(2));if(!reason.equals(r.getString(3)))mergedReason="OUTBOX_FULL";}}}
        try(var s=db.prepareStatement("DELETE FROM gaps WHERE session=? AND first_seq<=? AND last_seq>=?")){s.setString(1,id);s.setLong(2,last);s.setLong(3,first);s.executeUpdate();}
        try(var s=db.prepareStatement("INSERT OR REPLACE INTO gaps VALUES(?,?,?,?)")){s.setString(1,id);s.setLong(2,first);s.setLong(3,last);s.setString(4,mergedReason);s.executeUpdate();}
    }
    private void removeRows(String predicate,String reason)throws SQLException{
        var rows=new ArrayList<String[]>();try(var s=db.createStatement();var r=s.executeQuery("SELECT session,sequence FROM events "+predicate)){while(r.next())rows.add(new String[]{r.getString(1),r.getString(2)});}
        for(var row:rows){gap(row[0],Long.parseLong(row[1]),Long.parseLong(row[1]),reason);try(var s=db.prepareStatement("DELETE FROM events WHERE session=? AND sequence=?")){s.setString(1,row[0]);s.setLong(2,Long.parseLong(row[1]));s.executeUpdate();}}
    }
    private void prune(long now)throws SQLException{
        db.setAutoCommit(false);try{
        removeRows("WHERE observed_at<"+Math.max(0,now-TTL),"OUTBOX_EXPIRED");
        long size;try(var s=db.createStatement();var r=s.executeQuery("SELECT count(*) FROM events")){r.next();size=r.getLong(1);}
        if(size>capacity)removeRows("ORDER BY CASE WHEN kind IN ('GATE','SNAPSHOT') THEN0 ELSE1 END,observed_at LIMIT ".replace("THEN0","THEN 0").replace("ELSE1","ELSE 1")+(size-capacity),"OUTBOX_FULL");
        try(var s=db.prepareStatement("DELETE FROM sessions WHERE id<>? AND created_at<? AND id NOT IN (SELECT session FROM events UNION SELECT session FROM gaps)")){s.setString(1,session);s.setLong(2,now-TTL);s.executeUpdate();}
        try(var s=db.createStatement()){
            s.executeUpdate("DELETE FROM metadata WHERE k LIKE 'settings_ack:%' AND substr(k,14) NOT IN (SELECT id FROM sessions)");
            s.executeUpdate("DELETE FROM settings WHERE id NOT IN (SELECT settings_id FROM events) AND id NOT IN (SELECT last_settings FROM sessions)");
        }
        db.commit();}catch(SQLException e){db.rollback();throw e;}finally{db.setAutoCommit(true);}
    }
    public synchronized ObjectNode batch(long now,int maxEvents)throws Exception{
        prune(now);String id=null,version=null;
        try(var s=db.createStatement();var r=s.executeQuery("SELECT id,build FROM sessions WHERE id IN (SELECT session FROM events UNION SELECT session FROM gaps) ORDER BY created_at LIMIT1".replace("LIMIT1","LIMIT 1"))){if(r.next()){id=r.getString(1);version=r.getString(2);}}
        if(id==null)return null;
        // A flood of routine observations must not put the next real failure hours behind them.
        // Keep a recent baseline and declare the omitted prefix instead of pretending it uploaded.
        long firstCritical=0,lowPrefix=0;
        try(var s=db.prepareStatement("SELECT min(sequence) FROM events WHERE session=? AND kind NOT IN ('GATE','SNAPSHOT')")){s.setString(1,id);try(var r=s.executeQuery()){if(r.next())firstCritical=r.getLong(1);}}
        if(firstCritical>0){
            try(var s=db.prepareStatement("SELECT count(*) FROM events WHERE session=? AND sequence<? AND kind IN ('GATE','SNAPSHOT')")){s.setString(1,id);s.setLong(2,firstCritical);try(var r=s.executeQuery()){r.next();lowPrefix=r.getLong(1);}}
            if(lowPrefix>16){
                db.setAutoCommit(false);try{
                    removeRows("WHERE session='"+id+"' AND sequence<"+firstCritical+" AND kind IN ('GATE','SNAPSHOT') ORDER BY sequence LIMIT "+(lowPrefix-16),"OUTBOX_FULL");
                    db.commit();
                }catch(SQLException failure){db.rollback();throw failure;}finally{db.setAutoCommit(true);}
            }
        }
        var out=DashboardProtocol.JSON.createObjectNode().put("version",1).put("sessionId",id).put("build",version).put("sentAt",now);var events=out.putArray("events");var gaps=out.putArray("gaps");
        // Send the earliest gaps first. Bounded ranges are ACKed before later data.
        try(var s=db.prepareStatement("SELECT first_seq,last_seq,reason FROM gaps WHERE session=? ORDER BY first_seq LIMIT16".replace("LIMIT16","LIMIT 16"))){s.setString(1,id);try(var r=s.executeQuery()){while(r.next())gaps.addObject().put("from",r.getLong(1)).put("to",r.getLong(2)).put("reason",r.getString(3));}}
        String previousSettings=metadata("settings_ack:"+id);
        try(var s=db.prepareStatement("SELECT sequence,observed_at,kind,lines,settings_id,code FROM events JOIN settings ON settings.id=events.settings_id WHERE session=? AND observed_at<=? ORDER BY sequence LIMIT ?")){s.setString(1,id);s.setLong(2,now);s.setInt(3,Math.max(1,Math.min(32,maxEvents)));try(var r=s.executeQuery()){while(r.next()){var e=events.addObject().put("sequence",r.getLong(1)).put("observedAt",r.getLong(2)).put("kind",r.getString(3));e.set("lines",DashboardProtocol.JSON.readTree(r.getString(4)));if(!r.getString(5).equals(previousSettings))e.put("settingsCode",r.getString(6));if(out.toString().getBytes(StandardCharsets.UTF_8).length>MAX_FRAME){events.remove(events.size()-1);break;}previousSettings=r.getString(5);}}}
        return events.isEmpty()&&gaps.isEmpty()?null:out;
    }
    private static long highest(JsonNode frame){long max=0;for(var e:frame.path("events"))max=Math.max(max,e.path("sequence").asLong());for(var g:frame.path("gaps"))max=Math.max(max,g.path("to").asLong());return max;}
    public synchronized void sent(ObjectNode frame)throws SQLException{try(var s=db.prepareStatement("UPDATE sessions SET sent_through=max(sent_through,?) WHERE id=?")){s.setLong(1,highest(frame));s.setString(2,frame.path("sessionId").asText());s.executeUpdate();}}
    public synchronized boolean acknowledge(JsonNode ack)throws SQLException{
        if(ack.path("version").asInt()!=1||!ack.path("throughSequence").isIntegralNumber())return false;
        String id=ack.path("sessionId").asText();long through=ack.path("throughSequence").asLong(),sent=0;
        try(var s=db.prepareStatement("SELECT sent_through FROM sessions WHERE id=?")){s.setString(1,id);try(var r=s.executeQuery()){if(!r.next())return false;sent=r.getLong(1);}}
        if(through<0||through>sent)return false;
        try(var s=db.prepareStatement("SELECT settings_id FROM events WHERE session=? AND sequence<=? ORDER BY sequence DESC LIMIT1".replace("LIMIT1","LIMIT 1"))){s.setString(1,id);s.setLong(2,through);try(var r=s.executeQuery()){if(r.next())metadata("settings_ack:"+id,r.getString(1));}}
        try(var s=db.prepareStatement("DELETE FROM events WHERE session=? AND sequence<=?")){s.setString(1,id);s.setLong(2,through);s.executeUpdate();}
        try(var s=db.prepareStatement("DELETE FROM gaps WHERE session=? AND last_seq<=?")){s.setString(1,id);s.setLong(2,through);s.executeUpdate();}
        try(var s=db.prepareStatement("UPDATE sessions SET acked_through=max(acked_through,?) WHERE id=?")){s.setLong(1,through);s.setString(2,id);s.executeUpdate();}return true;
    }
    /** A server TTL expiry is not permission to invent old events; declare its lost prefix explicitly. */
    public synchronized void reconcileStatus(JsonNode status)throws SQLException{
        if(!status.path("known").isBoolean()||status.path("known").asBoolean()&&!status.path("sessions").isArray())throw new IllegalArgumentException("Invalid diagnostic status");
        var known=new HashSet<String>();for(var item:status.path("sessions"))known.add(item.path("sessionId").asText());
        var missing=new LinkedHashMap<String,Long>();try(var s=db.createStatement();var r=s.executeQuery("SELECT id,acked_through FROM sessions WHERE acked_through>0 AND id IN (SELECT session FROM events UNION SELECT session FROM gaps)")){while(r.next())if(!known.contains(r.getString(1)))missing.put(r.getString(1),r.getLong(2));}
        for(var entry:missing.entrySet()){gap(entry.getKey(),1,entry.getValue(),"OUTBOX_EXPIRED");metadata("settings_ack:"+entry.getKey(),"");}
    }
    public synchronized void reject(ObjectNode frame)throws SQLException{String id=frame.path("sessionId").asText();for(var e:frame.path("events")){long seq=e.path("sequence").asLong();gap(id,seq,seq,"OUTBOX_CORRUPT");try(var s=db.prepareStatement("DELETE FROM events WHERE session=? AND sequence=?")){s.setString(1,id);s.setLong(2,seq);s.executeUpdate();}}}
    /** A UUID/build collision must not strand the queue or change any event's build or contents. */
    public synchronized void recoverConflict(ObjectNode frame)throws SQLException{
        String old=frame.path("sessionId").asText(),replacement=UUID.randomUUID().toString();
        long acknowledged;
        try(var s=db.prepareStatement("SELECT acked_through FROM sessions WHERE id=?")){s.setString(1,old);try(var r=s.executeQuery()){if(!r.next())return;acknowledged=r.getLong(1);}}
        db.setAutoCommit(false);try{
            for(String table:List.of("events","gaps"))try(var s=db.prepareStatement("UPDATE "+table+" SET session=? WHERE session=?")){s.setString(1,replacement);s.setString(2,old);s.executeUpdate();}
            try(var s=db.prepareStatement("UPDATE sessions SET id=?,sent_through=0,acked_through=0 WHERE id=?")){s.setString(1,replacement);s.setString(2,old);s.executeUpdate();}
            if(acknowledged>0)gap(replacement,1,acknowledged,"OUTBOX_CORRUPT");
            metadata("settings_ack:"+old,"");db.commit();
        }catch(SQLException failure){db.rollback();throw failure;}finally{db.setAutoCommit(true);}
        if(session.equals(old))session=replacement;
    }
    @Override public synchronized void close()throws SQLException{db.close();}
}
