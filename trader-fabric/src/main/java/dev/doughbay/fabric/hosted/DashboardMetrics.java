package dev.doughbay.fabric.hosted;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Path;
import java.sql.*;
import java.util.Properties;

/** Read-only rolling REAL profits scoped to exactly this account. No database work on the game thread. */
public final class DashboardMetrics {
    private DashboardMetrics(){}
    public static ObjectNode read(Path database,String account,long at)throws Exception {
        Properties settings=new Properties();settings.setProperty("open_mode","1");settings.setProperty("busy_timeout","1000");
        try(Connection c=new org.sqlite.JDBC().connect("jdbc:sqlite:"+database,settings)) {
            try(Statement s=c.createStatement()){s.execute("PRAGMA query_only=ON");s.execute("PRAGMA busy_timeout=1000");}
            var root=DashboardProtocol.JSON.createObjectNode();root.putObject("profit").put("source","booked");
            root.putObject("sales");root.putObject("purchases");
            for(int hours:new int[]{1,24}) {
                String key=hours==1?"h1":"h24";long from=at-hours*3_600_000L;
                try(PreparedStatement p=c.prepareStatement("SELECT COUNT(*),COALESCE(SUM(realized_profit),0) FROM positions WHERE mode='REAL' AND status='SOLD' AND client=? AND closed_at>? AND closed_at<=?")) {
                    p.setString(1,account);p.setLong(2,from);p.setLong(3,at);
                    try(ResultSet r=p.executeQuery()){r.next();((ObjectNode)root.get("sales")).put(key,DashboardProtocol.count(r.getLong(1)));((ObjectNode)root.get("profit")).put(key,DashboardProtocol.money(Math.round(r.getDouble(2))));}
                }
                try(PreparedStatement p=c.prepareStatement("SELECT COUNT(*) FROM positions WHERE mode='REAL' AND client=? AND purchased_at>? AND purchased_at<=?")) {
                    p.setString(1,account);p.setLong(2,from);p.setLong(3,at);
                    try(ResultSet r=p.executeQuery()){r.next();((ObjectNode)root.get("purchases")).put(key,DashboardProtocol.count(r.getLong(1)));}
                }
            }
            return root;
        }
    }
}
