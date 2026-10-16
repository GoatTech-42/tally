package dev.doughbay.fabric;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.sql.*;

/** Bounded, read-only view of the existing hive event ledger. Never migrates or creates a database. */
final class EvasionHistory {
    private static final ObjectMapper JSON=new ObjectMapper();
    static ObjectNode read(Path ledger,long now){
        var result=JSON.createObjectNode().put("available",false).put("scope","hive").put("limit",100);
        result.putArray("events");
        if(ledger==null||!Files.isRegularFile(ledger))return result;
        var config=new org.sqlite.SQLiteConfig();config.setReadOnly(true);config.setBusyTimeout(150);
        try(var connection=new org.sqlite.JDBC().connect("jdbc:sqlite:"+ledger,config.toProperties())){
            if(connection==null)return result;
            try(var statement=connection.createStatement()){statement.execute("PRAGMA query_only=ON");}
            connection.setAutoCommit(false);
            try(var query=connection.prepareStatement("SELECT COUNT(*) FROM evasion_events WHERE at > ? AND action <> 'sighted' AND action NOT LIKE 'move-on%'")){
                query.setLong(1,now-24*3_600_000L);
                try(var rows=query.executeQuery()){if(rows.next())result.put("triggered24h",rows.getLong(1));}
            }
            try(var query=connection.prepareStatement("SELECT who,COUNT(*) FROM evasion_events WHERE at > ? AND who <> '' GROUP BY who ORDER BY COUNT(*) DESC,who LIMIT 1")){
                query.setLong(1,now-7*24*3_600_000L);
                try(var rows=query.executeQuery()){if(rows.next())result.putObject("mostSeen7d").put("who",text(rows.getString(1),64)).put("count",rows.getLong(2));}
            }
            try(var query=connection.prepareStatement("SELECT at,client,who,distance,action FROM evasion_events ORDER BY at DESC,rowid DESC LIMIT 100")){
                try(var rows=query.executeQuery()){
                    var events=result.withArray("events");
                    while(rows.next()){
                        double distance=rows.getDouble(4);
                        events.addObject().put("at",rows.getLong(1)).put("client",text(rows.getString(2),64)).put("who",text(rows.getString(3),64))
                                .put("distance",Double.isFinite(distance)&&distance>0?distance:0).put("action",text(rows.getString(5),400));
                    }
                }
            }
            return result.put("available",true);
        }catch(SQLException failure){
            // A busy/unavailable ledger must not erase the settings or expose paths/errors.
            result.remove("triggered24h");result.remove("mostSeen7d");result.putArray("events");return result;
        }
    }
    private static String text(String value,int limit){
        if(value==null)return "";
        return value.codePointCount(0,value.length())<=limit?value:value.substring(0,value.offsetByCodePoints(0,limit))+"…";
    }
    private EvasionHistory(){}
}
