package dev.doughbay.storage;

import java.nio.file.*;
import java.sql.*;
import java.util.*;

/** Offline import into a NEW ledger only. Source connections are read-only; originals are never rewritten. */
public final class HiveLedgerImport {
    private static final Set<String> TABLES=Set.of("schema_version","transactions","listing_snapshots","items","market_stats",
            "opportunities","positions","raw_pages","balance_history","automation_session_state","automation_resolution_audit",
            "sale_buyers","component_listings","part_values","payroll_rules","payroll_payments","orders","population",
            "api_clients","hive_tips","hive_control","evasion_events","staff_watch_alerts","hive_loans","retail_positions","connection_alerts","position_id_allocator");
    private static final Set<String> CACHE=Set.of("items","market_stats","part_values","population");
    private HiveLedgerImport() {}
    public record Result(long firstPositions,long secondPositions,long importedPositions) {}
    private static Connection read(Path path) throws SQLException {
        Connection c=DriverManager.getConnection("jdbc:sqlite:"+path.toUri()+"?mode=ro");
        try(var st=c.createStatement()){st.execute("PRAGMA query_only=ON");st.execute("PRAGMA busy_timeout=1000");}
        return c;
    }
    public static void inspect(Path path,String owner) throws Exception {
        if(!owner.matches("[A-Za-z0-9_.-]{1,32}")||owner.equals(".")||owner.equals(".."))throw new SQLException("Invalid hive account");
        if(!Files.isRegularFile(path)||Files.isSymbolicLink(path))throw new SQLException("Hive ledger is unavailable");
        try(var c=read(path)) {
            if(number(c,"SELECT MAX(version) FROM schema_version")!=19)throw new SQLException("Update both instances before combining hives");
            try(var s=c.createStatement();var r=s.executeQuery("SELECT name,type FROM sqlite_master WHERE name NOT LIKE 'sqlite_%' AND type IN ('table','trigger','view')")) {
                while(r.next())if(!r.getString(2).equals("table")||!TABLES.contains(r.getString(1)))throw new SQLException("This ledger has an unsupported schema; originals were kept");
            }
            for(String table:List.of("positions","automation_session_state")) {
                try(var s=c.prepareStatement("SELECT 1 FROM "+table+" WHERE client<>'' AND lower(client)<>lower(?) LIMIT 1")) {
                    s.setString(1,owner);try(var r=s.executeQuery()){if(r.next())throw new SQLException("Combine currently supports two single-account hives; this hive already has other account history");}
                }
            }
            try(var s=c.prepareStatement("SELECT 1 FROM api_clients WHERE name<>'' AND lower(name)<>lower(?) LIMIT 1")) {
                s.setString(1,owner);try(var r=s.executeQuery()){if(r.next())throw new SQLException("This hive already records another account; a multi-member migration is required");}
            }
            if(number(c,"SELECT COUNT(*) FROM positions WHERE loan_id<>0")>0)throw new SQLException("Loan-linked positions require a separately reviewed migration");
            // These records are not account-scoped yet. Never activate another account's payout policy or queued command.
            for(String table:List.of("payroll_rules","payroll_payments","hive_loans"))
                if(number(c,"SELECT COUNT(*) FROM "+table)>0)throw new SQLException("This hive has payroll or loan records requiring a separate reviewed migration; originals were kept");
            if(number(c,"SELECT COUNT(*) FROM hive_control WHERE status='OPEN'")>0)throw new SQLException("Finish pending hive controls before combining");
            if(!scalar(c,"PRAGMA integrity_check").equals("ok"))throw new SQLException("Hive ledger integrity could not be verified");
        }
    }
    public static Result combine(Path first,String firstOwner,Path second,String secondOwner,Path output) throws Exception {
        if(firstOwner.equalsIgnoreCase(secondOwner)||Files.isSameFile(first,second))throw new SQLException("Choose two different account hives");
        if(Files.exists(output))throw new SQLException("Combined ledger destination already exists; it was not overwritten");
        inspect(first,firstOwner);inspect(second,secondOwner);
        try(var a=read(first);var b=read(second)) {
            for(String table:TABLES)if(!columns(a,table).equals(columns(b,table)))throw new SQLException("Hive schemas differ; update both instances first");
            // VACUUM INTO includes committed WAL contents without checkpointing or copying a live database file.
            // The connection's mode=ro still protects the source; query_only would also prohibit writing the NEW file.
            try(var s=a.createStatement()){s.execute("PRAGMA query_only=OFF");}
            try(var s=a.prepareStatement("VACUUM INTO ?")){s.setString(1,output.toAbsolutePath().toString());s.execute();}
            try(var out=DriverManager.getConnection("jdbc:sqlite:"+output)) {
                try(var s=out.createStatement()){s.execute("PRAGMA foreign_keys=OFF");}
                out.setAutoCommit(false);
                try {
                    assignOwner(out,firstOwner);
                    long countA=number(a,"SELECT COUNT(*) FROM positions"),countB=number(b,"SELECT COUNT(*) FROM positions");
                    long next=Math.max(number(out,"SELECT COALESCE(MAX(position_id),0) FROM positions"),
                            Math.max(number(a,"SELECT next_id FROM position_id_allocator"),number(b,"SELECT next_id FROM position_id_allocator")))+1;
                    Map<Long,Long> ids=new HashMap<>();
                    try(var s=b.createStatement();var r=s.executeQuery("SELECT position_id FROM positions ORDER BY position_id")) {
                        while(r.next()){if(next>=(1L<<53)-1)throw new SQLException("Combined position ID space exhausted");ids.put(r.getLong(1),next++);}
                    }
                    List<String> order=new ArrayList<>(TABLES);order.remove("positions");order.addFirst("positions");
                    for(String table:order) {
                        if(Set.of("schema_version","position_id_allocator","payroll_rules","payroll_payments","hive_loans").contains(table))continue;
                        List<Column> cols=columns(b,table);
                        boolean generated=cols.stream().filter(c->c.pk>0).count()==1 && cols.stream().anyMatch(c->c.pk>0&&c.type.equalsIgnoreCase("INTEGER"))
                                && !Set.of("positions","retail_positions","population").contains(table);
                        List<Column> insert=cols.stream().filter(c->!generated||c.pk==0).toList();
                        String names=String.join(",",insert.stream().map(c->quote(c.name)).toList());
                        String values=String.join(",",Collections.nCopies(insert.size(),"?"));
                        String sql="INSERT "+(CACHE.contains(table)?"OR IGNORE ":"")+"INTO "+quote(table)+" ("+names+") VALUES ("+values+")";
                        try(var rows=b.createStatement();var r=rows.executeQuery("SELECT * FROM "+quote(table));var put=out.prepareStatement(sql)) {
                            while(r.next()) {
                                if(table.equals("transactions")&&duplicateTransaction(out,r))continue;
                                for(int i=0;i<insert.size();i++) {
                                    String col=insert.get(i).name;Object value=r.getObject(col);
                                    if((table.equals("positions")||table.equals("retail_positions")||table.equals("automation_resolution_audit")||table.equals("sale_buyers"))
                                            && (col.equals("position_id")||col.equals("root_position_id")) && value instanceof Number n&&n.longValue()!=0) {
                                        value=ids.get(n.longValue());if(value==null)throw new SQLException("Unmatched imported position reference");
                                    }
                                    if(col.equals("client")&&value instanceof String v&&v.isEmpty())value=table.equals("automation_session_state")&&hasSession(b,secondOwner)?"legacy-import:"+secondOwner:secondOwner;
                                    put.setObject(i+1,value);
                                }
                                put.executeUpdate();
                            }
                        }
                    }
                    try(var s=out.prepareStatement("UPDATE position_id_allocator SET next_id=? WHERE singleton_id=1")){s.setLong(1,next);s.executeUpdate();}
                    if(number(out,"SELECT COUNT(*) FROM positions")!=countA+countB)throw new SQLException("Combined position count mismatch");
                    try(var s=out.createStatement();var r=s.executeQuery("PRAGMA foreign_key_check")){if(r.next())throw new SQLException("Combined position references failed validation");}
                    if(!scalar(out,"PRAGMA integrity_check").equals("ok"))throw new SQLException("Combined ledger integrity failed");
                    out.commit();return new Result(countA,countB,countB);
                } catch(Exception e){out.rollback();throw e;}
            }
        }
    }
    private static boolean duplicateTransaction(Connection out,ResultSet row) throws Exception {
        try(var s=out.prepareStatement("SELECT * FROM transactions WHERE transaction_hash=?")) {
            s.setString(1,row.getString("transaction_hash"));try(var r=s.executeQuery()) {
                if(!r.next())return false;
                for(String col:List.of("sold_at","seller_uuid","item_key","item_id","item_count","total_price"))
                    if(!Objects.equals(r.getObject(col),row.getObject(col)))throw new SQLException("Conflicting sale receipt; no source was overwritten");
                return true;
            }
        }
    }
    private static void assignOwner(Connection c,String owner) throws SQLException {
        for(String table:List.of("positions","automation_session_state"))try(var s=c.prepareStatement("UPDATE "+table+" SET client=? WHERE client=''")){
            s.setString(1,table.equals("automation_session_state")&&hasSession(c,owner)?"legacy-import:"+owner:owner);s.executeUpdate();
        }
    }
    private static boolean hasSession(Connection c,String owner) throws SQLException {
        try(var s=c.prepareStatement("SELECT 1 FROM automation_session_state WHERE lower(client)=lower(?)")){s.setString(1,owner);try(var r=s.executeQuery()){return r.next();}}
    }
    private record Column(String name,String type,int pk) {}
    private static List<Column> columns(Connection c,String table) throws SQLException {
        List<Column> result=new ArrayList<>();try(var s=c.createStatement();var r=s.executeQuery("PRAGMA table_info("+quote(table)+")")){while(r.next())result.add(new Column(r.getString("name"),r.getString("type"),r.getInt("pk")));}return result;
    }
    private static long number(Connection c,String sql) throws SQLException {return Long.parseLong(scalar(c,sql));}
    private static String scalar(Connection c,String sql) throws SQLException {try(var s=c.createStatement();var r=s.executeQuery(sql)){if(!r.next())throw new SQLException("Missing ledger metadata");return r.getString(1);}}
    private static String quote(String name){return "\""+name.replace("\"","\"\"")+"\"";}
}
