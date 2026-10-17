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
   s.execute("CREATE TABLE positions(mode TEXT,status TEXT,client TEXT,closed_at INTEGER,purchased_at INTEGER,realized_profit REAL)");
   s.execute("INSERT INTO positions VALUES ('REAL','SOLD','account',99999000,99999000,50),('REAL','SOLD','account',90000000,90000000,-20),('PAPER','SOLD','account',99999000,99999000,999),('REAL','SOLD','other',99999000,99999000,999),('REAL','LISTED','account',0,99999000,999),('REAL','SOLD','account',100000001,100000001,999)");
  }
  var result=DashboardMetrics.read(db,"account",now);assertEquals("booked",result.path("profit").path("source").asText());assertEquals(50,result.path("profit").path("h1").asLong());assertEquals(30,result.path("profit").path("h24").asLong());assertEquals(1,result.path("sales").path("h1").asInt());assertEquals(2,result.path("sales").path("h24").asInt());assertEquals(2,result.path("purchases").path("h1").asInt());assertFalse(result.has("listings"));assertFalse(result.has("reprices"));
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
}
