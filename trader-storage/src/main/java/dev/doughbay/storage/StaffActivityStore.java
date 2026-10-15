package dev.doughbay.storage;

import java.nio.file.*;
import java.sql.*;
import java.util.*;

/** Separate hive-sidecar database: observation history never changes trading or its ledger. */
public final class StaffActivityStore implements AutoCloseable {
    public record Person(String name,String presence,boolean sameRegion,boolean nearby) {
        public Person {
            if(name==null || !name.matches("[A-Za-z0-9_]{1,16}") || !Set.of("ONLINE","SPECTATOR","VANISHED").contains(presence))
                throw new IllegalArgumentException("Invalid staff observation");
        }
    }
    public record Sample(long at,String observer,boolean enabled,boolean connected,String scope,List<Person> people) {
        public Sample {
            if(at<=0 || observer==null || !observer.matches("[A-Za-z0-9_]{1,16}") || scope==null || scope.length()>80)
                throw new IllegalArgumentException("Invalid observer");
            people=List.copyOf(people);
            if(people.size()>100) throw new IllegalArgumentException("Too many staff rows");
        }
        public boolean watching() {return enabled&&connected;}
    }
    public record Event(long at,String name,String event,boolean sameRegion,String scope) {}
    public record Interval(long from,long to) {}
    public record Session(String name,long from,long to,String presence,String observer,String scope) {}
    public record Current(String name,String presence,String observer,String scope,boolean sameRegion,boolean nearby) {}
    public record Sighting(long at,String name,String event,boolean sameRegion,String scope,String observer) {}
    public record Report(List<Current> current,List<Session> sessions,List<Interval> coverage,List<Sighting> history,int watching) {
        public Report {current=List.copyOf(current);sessions=List.copyOf(sessions);coverage=List.copyOf(coverage);history=List.copyOf(history);}
    }
    private static final long RETENTION=30L*86_400_000;
    private final Connection db;
    private final String run=UUID.randomUUID().toString();
    private Sample previous;
    private long windowId;
    private final Map<String,Long> open=new HashMap<>();
    public StaffActivityStore(Path path) throws SQLException,java.io.IOException {
        Files.createDirectories(path.toAbsolutePath().getParent());
        db=DriverManager.getConnection("jdbc:sqlite:"+path.toAbsolutePath());
        try(var statement=db.createStatement()) {
            statement.execute("PRAGMA busy_timeout=1000");statement.execute("PRAGMA journal_mode=WAL");
            statement.execute("CREATE TABLE IF NOT EXISTS observers(observer TEXT PRIMARY KEY,run TEXT NOT NULL,at INTEGER NOT NULL,watching INTEGER NOT NULL)");
            statement.execute("CREATE TABLE IF NOT EXISTS coverage(id INTEGER PRIMARY KEY,run TEXT NOT NULL,observer TEXT NOT NULL,scope TEXT NOT NULL,first INTEGER NOT NULL,last INTEGER NOT NULL)");
            statement.execute("CREATE TABLE IF NOT EXISTS sessions(id INTEGER PRIMARY KEY,run TEXT NOT NULL,observer TEXT NOT NULL,name TEXT NOT NULL,presence TEXT NOT NULL,scope TEXT NOT NULL,same_region INTEGER NOT NULL,nearby INTEGER NOT NULL,first INTEGER NOT NULL,last INTEGER NOT NULL)");
            statement.execute("CREATE TABLE IF NOT EXISTS events(id INTEGER PRIMARY KEY,run TEXT NOT NULL,observer TEXT NOT NULL,at INTEGER NOT NULL,name TEXT NOT NULL,event TEXT NOT NULL,same_region INTEGER NOT NULL,scope TEXT NOT NULL,UNIQUE(run,at,name,event,scope))");
            statement.execute("CREATE INDEX IF NOT EXISTS sessions_last ON sessions(last)");
            statement.execute("CREATE INDEX IF NOT EXISTS coverage_last ON coverage(last)");
            statement.execute("CREATE INDEX IF NOT EXISTS events_at ON events(at)");
        } catch(SQLException failure) {db.close();throw failure;}
    }
    /** Caller samples existing detections. Gaps, disconnects and scope changes begin new intervals. */
    public void accept(Sample sample,List<Event> events) throws SQLException {
        boolean continuous=previous!=null && previous.watching() && sample.watching()
            && sample.at()>=previous.at() && sample.at()-previous.at()<=15_000
            && sample.observer().equalsIgnoreCase(previous.observer()) && sample.scope().equals(previous.scope());
        Map<String,Long> next=new HashMap<>();long nextWindow=0;
        db.setAutoCommit(false);
        try {
            try(var ps=db.prepareStatement("INSERT INTO observers VALUES(?,?,?,?) ON CONFLICT(observer) DO UPDATE SET run=excluded.run,at=excluded.at,watching=excluded.watching")) {
                ps.setString(1,sample.observer());ps.setString(2,run);ps.setLong(3,sample.at());ps.setInt(4,sample.watching()?1:0);ps.executeUpdate();
            }
            if(sample.watching()) {
                nextWindow=continuous?windowId:insertCoverage(sample);
                updateEnd("coverage",nextWindow,sample.at());
                for(Person person:sample.people()) {
                    String key=person.name().toLowerCase(Locale.ROOT)+"|"+person.presence()+"|"+person.sameRegion()+"|"+person.nearby();
                    Long id=continuous?open.get(key):null;
                    if(id==null) id=insertSession(sample,person);
                    updateEnd("sessions",id,sample.at());next.put(key,id);
                }
            }
            for(Event event:events) {
                if(event.at()<=0 || event.at()>sample.at()+60_000 || event.name()==null || !event.name().matches("[A-Za-z0-9_]{1,16}")
                    || !Set.of("OBSERVED","SPECTATOR","HIDDEN_FROM_TAB","NO_LONGER_OBSERVED","NEARBY","MODE_CHANGED").contains(event.event())) continue;
                try(var ps=db.prepareStatement("INSERT INTO events(run,observer,at,name,event,same_region,scope) VALUES(?,?,?,?,?,?,?) ON CONFLICT(run,at,name,event,scope) DO UPDATE SET same_region=MAX(events.same_region,excluded.same_region)")) {
                    ps.setString(1,run);ps.setString(2,sample.observer());ps.setLong(3,event.at());ps.setString(4,event.name());
                    ps.setString(5,event.event());ps.setInt(6,event.sameRegion()?1:0);ps.setString(7,event.scope());ps.executeUpdate();
                }
            }
            for(String table:List.of("coverage","sessions","events")) {
                try(var ps=db.prepareStatement("DELETE FROM "+table+" WHERE "+(table.equals("events")?"at":"last")+"<?")) {
                    ps.setLong(1,sample.at()-RETENTION);ps.executeUpdate();
                }
            }
            db.commit();previous=sample;windowId=nextWindow;open.clear();open.putAll(next);
        } catch(SQLException failure) {db.rollback();throw failure;}
        finally {db.setAutoCommit(true);}
    }
    private long insertCoverage(Sample sample) throws SQLException {
        try(var ps=db.prepareStatement("INSERT INTO coverage(run,observer,scope,first,last) VALUES(?,?,?,?,?)")) {
            ps.setString(1,run);ps.setString(2,sample.observer());ps.setString(3,sample.scope());ps.setLong(4,sample.at());ps.setLong(5,sample.at());ps.executeUpdate();
        }
        return lastId();
    }
    private long insertSession(Sample sample,Person person) throws SQLException {
        try(var ps=db.prepareStatement("INSERT INTO sessions(run,observer,name,presence,scope,same_region,nearby,first,last) VALUES(?,?,?,?,?,?,?,?,?)")) {
            ps.setString(1,run);ps.setString(2,sample.observer());ps.setString(3,person.name());ps.setString(4,person.presence());ps.setString(5,sample.scope());
            ps.setInt(6,person.sameRegion()?1:0);ps.setInt(7,person.nearby()?1:0);ps.setLong(8,sample.at());ps.setLong(9,sample.at());ps.executeUpdate();
        }
        return lastId();
    }
    private long lastId() throws SQLException {try(var st=db.createStatement();var rs=st.executeQuery("SELECT last_insert_rowid()")){rs.next();return rs.getLong(1);}}
    private void updateEnd(String table,long id,long at) throws SQLException {
        try(var ps=db.prepareStatement("UPDATE "+table+" SET last=? WHERE id=?")){ps.setLong(1,at);ps.setLong(2,id);ps.executeUpdate();}
    }
    public Report report(long now) throws SQLException {
        var current=new ArrayList<Current>();var sessions=new ArrayList<Session>();var coverage=new ArrayList<Interval>();var history=new ArrayList<Sighting>();
        int watching=0;
        db.setAutoCommit(false);
        try {
            try(var ps=db.prepareStatement("SELECT COUNT(*) FROM observers WHERE watching=1 AND at>=? AND at<=?")) {
                ps.setLong(1,now-15_000);ps.setLong(2,now);try(var rs=ps.executeQuery()){rs.next();watching=rs.getInt(1);}
            }
            try(var ps=db.prepareStatement("SELECT s.name,s.presence,s.observer,s.scope,s.same_region,s.nearby FROM sessions s JOIN observers o ON s.observer=o.observer AND s.run=o.run AND s.last=o.at WHERE o.watching=1 AND o.at>=? AND o.at<=? ORDER BY s.name,s.observer")) {
                ps.setLong(1,now-15_000);ps.setLong(2,now);
                try(var rs=ps.executeQuery()){while(rs.next())current.add(new Current(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getInt(5)!=0,rs.getInt(6)!=0));}
            }
            try(var ps=db.prepareStatement("SELECT name,first,last,presence,observer,scope FROM sessions WHERE last>=? AND first<=? ORDER BY first LIMIT 50000")) {
                ps.setLong(1,now-RETENTION);ps.setLong(2,now);
                try(var rs=ps.executeQuery()){while(rs.next())sessions.add(new Session(rs.getString(1),Math.max(now-RETENTION,rs.getLong(2)),Math.min(now,rs.getLong(3)),rs.getString(4),rs.getString(5),rs.getString(6)));}
            }
            try(var ps=db.prepareStatement("SELECT first,last FROM coverage WHERE last>=? AND first<=? ORDER BY first LIMIT 50000")) {
                ps.setLong(1,now-RETENTION);ps.setLong(2,now);
                try(var rs=ps.executeQuery()){while(rs.next())coverage.add(new Interval(Math.max(now-RETENTION,rs.getLong(1)),Math.min(now,rs.getLong(2))));}
            }
            try(var ps=db.prepareStatement("SELECT at,name,event,same_region,scope,observer FROM events WHERE at>=? AND at<=? ORDER BY at DESC LIMIT 100")) {
                ps.setLong(1,now-RETENTION);ps.setLong(2,now);
                try(var rs=ps.executeQuery()){while(rs.next())history.add(new Sighting(rs.getLong(1),rs.getString(2),rs.getString(3),rs.getInt(4)!=0,rs.getString(5),rs.getString(6)));}
            }
            if(sessions.size()>=50000 || coverage.size()>=50000) throw new SQLException("Staff observation history exceeds query limit");
            db.commit();return new Report(current,sessions,coverage,history,watching);
        } catch(SQLException failure){db.rollback();throw failure;}
        finally {db.setAutoCommit(true);}
    }
    @Override public void close() throws SQLException {db.close();}
}
