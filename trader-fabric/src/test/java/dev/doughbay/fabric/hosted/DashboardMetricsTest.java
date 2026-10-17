package dev.doughbay.fabric.hosted;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.sql.*;
import java.util.Properties;
import static org.junit.jupiter.api.Assertions.*;
class DashboardMetricsTest {
 @TempDir Path root;
 @Test void profitWindowsUseRealCompletedSalesForExactlyOneAccount() throws Exception {
  Path db=root.resolve("metrics.db");long now=100_000_000;
  try(var c=new org.sqlite.JDBC().connect("jdbc:sqlite:"+db,new Properties());var s=c.createStatement()){
   s.execute("CREATE TABLE positions(mode TEXT,status TEXT,client TEXT,closed_at INTEGER,purchased_at INTEGER,realized_profit REAL,sale_price INTEGER)");
   s.execute("INSERT INTO positions VALUES ('REAL','SOLD','account',99999000,99999000,50,200),('REAL','SOLD','account',90000000,90000000,-20,100),('PAPER','SOLD','account',99999000,99999000,999,999),('REAL','SOLD','other',99999000,99999000,999,999),('REAL','LISTED','account',0,99999000,999,999),('REAL','SOLD','account',100000001,100000001,999,999)");
  }
  var result=DashboardMetrics.read(db,"account",now);assertEquals("booked",result.path("profit").path("source").asText());assertEquals(50,result.path("profit").path("h1").asLong());assertEquals(30,result.path("profit").path("h24").asLong());assertEquals(1,result.path("sales").path("h1").asInt());assertEquals(2,result.path("sales").path("h24").asInt());assertEquals(2,result.path("purchases").path("h1").asInt());assertFalse(result.has("listings"));assertFalse(result.has("reprices"));assertEquals("booked_sales",result.path("revenue").path("source").asText());assertEquals(200,result.path("revenue").path("h1").asLong());assertEquals(300,result.path("revenue").path("h24").asLong());
 }
 @Test void readOnlyMetricsNeverCreateAnAbsentDatabase(){Path absent=root.resolve("not-created.db");assertThrows(Exception.class,()->DashboardMetrics.read(absent,"account",10000000));assertFalse(Files.exists(absent));}
 @Test void missingRealizedProfitIsUnknownRatherThanZero() throws Exception {
  Path db=root.resolve("missing.db");
  try(var c=new org.sqlite.JDBC().connect("jdbc:sqlite:"+db,new Properties());var s=c.createStatement()){
   s.execute("CREATE TABLE positions(mode TEXT,status TEXT,client TEXT,closed_at INTEGER,purchased_at INTEGER,realized_profit REAL)");
   s.execute("INSERT INTO positions VALUES ('REAL','SOLD','account',99999000,99999000,NULL)");
  }
  assertThrows(Exception.class,()->DashboardMetrics.read(db,"account",100_000_000));
 }
 @Test void incompleteRevenueDoesNotRemoveValidProfitOrInventRevenue() throws Exception {
  Path db=root.resolve("revenue.db");long now=100_000_000;
  try(var c=new org.sqlite.JDBC().connect("jdbc:sqlite:"+db,new Properties());var st=c.createStatement()){
   st.execute("CREATE TABLE positions(mode TEXT,status TEXT,client TEXT,closed_at INTEGER,purchased_at INTEGER,realized_profit REAL,sale_price INTEGER)");
   st.execute("INSERT INTO positions VALUES ('REAL','SOLD','account',99999000,99999000,20,NULL),('REAL','SOLD','account',90000000,90000000,10,100)");
  }
  var result=DashboardMetrics.read(db,"account",now);assertEquals(30,result.path("profit").path("h24").asLong());assertFalse(result.has("revenue"));
  try(var c=new org.sqlite.JDBC().connect("jdbc:sqlite:"+db,new Properties());var st=c.createStatement()){st.execute("UPDATE positions SET sale_price=-1 WHERE sale_price IS NULL");}
  assertFalse(DashboardMetrics.read(db,"account",now).has("revenue"));
 }
 @Test void absentRevenueColumnIsUnknownAndEmptyKnownSalesAreZero() throws Exception {
  Path db=root.resolve("legacy.db");
  try(var c=new org.sqlite.JDBC().connect("jdbc:sqlite:"+db,new Properties());var st=c.createStatement()){
   st.execute("CREATE TABLE positions(mode TEXT,status TEXT,client TEXT,closed_at INTEGER,purchased_at INTEGER,realized_profit REAL)");
  }
  assertFalse(DashboardMetrics.read(db,"account",100_000_000).has("revenue"));
  try(var c=new org.sqlite.JDBC().connect("jdbc:sqlite:"+db,new Properties());var st=c.createStatement()){st.execute("ALTER TABLE positions ADD COLUMN sale_price INTEGER");}
  var result=DashboardMetrics.read(db,"account",100_000_000);assertEquals(0,result.path("revenue").path("h1").asLong(-1));assertEquals(0,result.path("revenue").path("h24").asLong(-1));
 }

}
