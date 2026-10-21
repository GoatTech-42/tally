package dev.doughbay.storage;

import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.security.MessageDigest;

/** Offline, evidence-checked correction of one legacy row misattributed by old recovery.
 * The affected client must be closed. This is never a normal resume or automatic migration.
 * Application requires explicit operator approval of the exact reviewed fingerprint.
 */
public final class LegacyRecoveryOwnerRepair {
    /** Stable, lossless text for SQLite scalar rows, without a web-layer dependency. */
    private static String encode(Object value) {
        if(value==null)return "null";
        if(value instanceof String text){
            var out=new StringBuilder("\"");
            for(char c:text.toCharArray()){
                if(c=='"'||c=='\\')out.append('\\').append(c);
                else if(c<32)out.append(String.format(Locale.ROOT,"\\u%04x",(int)c));
                else out.append(c);
            }
            return out.append('"').toString();
        }
        if(value instanceof Number)return value.toString();
        if(value instanceof Map<?,?> map){
            var entries=new StringJoiner(",","{","}");
            for(var entry:map.entrySet())entries.add(encode(entry.getKey())+":"+encode(entry.getValue()));
            return entries.toString();
        }
        if(value instanceof List<?> list){
            var entries=new StringJoiner(",","[","]");
            for(var item:list)entries.add(encode(item));return entries.toString();
        }
        throw new IllegalArgumentException("Unsupported database value type");
    }
    public record Plan(String fingerprint,Map<String,Object> position,Map<String,Object> original,
                       Map<String,Object> checkpoint) {}
    private LegacyRecoveryOwnerRepair() {}
    public static Plan inspect(Path live,Path backup,String owner)throws Exception {
        validatePaths(live,backup,owner);
        try(var current=read(live);var source=read(backup)){return inspect(current,source,owner);}
    }
    private static void validatePaths(Path live,Path backup,String owner)throws Exception {
        if(owner==null||!owner.matches("[A-Za-z0-9_]{1,16}"))throw new IllegalArgumentException("A named account is required");
        if(!Files.isRegularFile(live)||!Files.isRegularFile(backup)||Files.isSameFile(live,backup))
            throw new IllegalArgumentException("A separate preserved pre-join database is required");
    }
    private static Connection read(Path path)throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:"+path.toAbsolutePath().toUri()+"?mode=ro");
    }
    private static Plan inspect(Connection current,Connection source,String owner)throws Exception {
        if(!rows(source,"SELECT * FROM positions WHERE client=?",owner).isEmpty()
                ||!rows(source,"SELECT * FROM automation_session_state WHERE client=?",owner).isEmpty())
            throw new IllegalStateException("The preserved database already contains this account's history");
        var owned=rows(current,"SELECT * FROM positions WHERE client=?",owner);
        var states=rows(current,"SELECT * FROM automation_session_state WHERE client=?",owner);
        if(owned.size()!=1||states.size()!=1)throw new IllegalStateException("Expected exactly one misattributed position and one paused checkpoint");
        var position=owned.getFirst();var checkpoint=states.getFirst();
        if(!"REAL".equals(position.get("mode"))||!"PURCHASED".equals(position.get("status"))
                ||!"PAUSED".equals(checkpoint.get("controller_state"))
                ||((Number)checkpoint.get("uncertain_active")).longValue()!=0)
            throw new IllegalStateException("The current state is not the supported paused legacy-import case");
        var originals=rows(source,"SELECT * FROM positions WHERE position_id=?",position.get("position_id"));
        var legacy=rows(source,"SELECT * FROM automation_session_state WHERE client=?","");
        if(originals.size()!=1||legacy.size()!=1||!"".equals(originals.getFirst().get("client")))
            throw new IllegalStateException("The original unowned record and legacy checkpoint must be preserved");
        var original=originals.getFirst();var compare=new TreeMap<>(position);compare.put("client","");
        if(!compare.equals(original))throw new IllegalStateException("The position changed beyond its incorrect owner; no correction is safe");
        for(String key:List.of("run_mode","session_started_at","bound_listing_key"))
            if(!Objects.equals(checkpoint.get(key),legacy.getFirst().get(key)))
                throw new IllegalStateException("The checkpoint does not match the preserved legacy session");
        String fingerprint=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(encode(List.of(owner,position,original,checkpoint)).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        return new Plan(fingerprint,position,original,checkpoint);
    }
    private static List<Map<String,Object>> rows(Connection db,String sql,Object parameter)throws SQLException {
        var out=new ArrayList<Map<String,Object>>();
        try(var query=db.prepareStatement(sql)){query.setObject(1,parameter);try(var r=query.executeQuery()){
            while(r.next()){var row=new TreeMap<String,Object>();var meta=r.getMetaData();
                for(int i=1;i<=meta.getColumnCount();i++)row.put(meta.getColumnName(i),r.getObject(i));out.add(row);
            }
        }}return out;
    }
    public static void apply(Path live,Path backup,String owner,String approvedFingerprint)throws Exception {
        validatePaths(live,backup,owner);
        if(approvedFingerprint==null||!approvedFingerprint.matches("[a-f0-9]{64}"))
            throw new IllegalArgumentException("Explicit approval of the reviewed repair is required");
        try(var db=DriverManager.getConnection("jdbc:sqlite:"+live.toAbsolutePath());var source=read(backup);var st=db.createStatement()){
            st.execute("PRAGMA busy_timeout=10000");st.execute("BEGIN IMMEDIATE");
            try{
                Plan plan=inspect(db,source,owner);
                if(!MessageDigest.isEqual(plan.fingerprint().getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                        approvedFingerprint.getBytes(java.nio.charset.StandardCharsets.US_ASCII)))
                    throw new IllegalStateException("The reviewed state changed; inspect and approve again");
                st.execute("CREATE TABLE IF NOT EXISTS legacy_recovery_owner_repairs(id TEXT PRIMARY KEY,client TEXT NOT NULL,at INTEGER NOT NULL,fingerprint TEXT NOT NULL,previous_position TEXT NOT NULL,original_position TEXT NOT NULL,previous_checkpoint TEXT NOT NULL,note TEXT NOT NULL)");
                try(var audit=db.prepareStatement("INSERT INTO legacy_recovery_owner_repairs VALUES(?,?,?,?,?,?,?,?)")){
                    audit.setString(1,UUID.randomUUID().toString());audit.setString(2,owner);audit.setLong(3,System.currentTimeMillis());
                    audit.setString(4,plan.fingerprint());audit.setString(5,encode(plan.position()));
                    audit.setString(6,encode(plan.original()));audit.setString(7,encode(plan.checkpoint()));
                    audit.setString(8,"Operator-approved correction of proven legacy recovery misattribution; original trade preserved, false checkpoint archived, no trade outcome acknowledged");audit.executeUpdate();
                }
                try(var update=db.prepareStatement("UPDATE positions SET client='' WHERE position_id=? AND client=?")){
                    update.setObject(1,plan.position().get("position_id"));update.setString(2,owner);
                    if(update.executeUpdate()!=1)throw new SQLException("Position ownership changed");
                }
                try(var remove=db.prepareStatement("DELETE FROM automation_session_state WHERE client=?")){
                    remove.setString(1,owner);if(remove.executeUpdate()!=1)throw new SQLException("Checkpoint changed");
                }
                st.execute("COMMIT");
            }catch(Exception e){try{st.execute("ROLLBACK");}catch(Exception rollback){e.addSuppressed(rollback);}throw e;}
        }
    }
    public static void main(String[] args)throws Exception {
        if(args.length<4)throw new IllegalArgumentException("Use inspect or explicitly approved apply with live DB, preserved DB and account");
        Path live=Path.of(args[1]),source=Path.of(args[2]);String owner=args[3];
        if(args[0].equals("inspect")){
            var plan=inspect(live,source,owner);
            System.out.println("{\"eligible\":true,\"positionsToRestoreToOriginalUnownedScope\":1,\"falseCheckpointsToArchive\":1,\"fingerprint\":"+encode(plan.fingerprint())+"}");
        }else if(args[0].equals("apply")&&args.length==5){apply(live,source,owner,args[4]);System.out.println("Approved correction committed; original trade retained and prior state archived");}
        else throw new IllegalArgumentException("Unknown repair action");
    }
}
