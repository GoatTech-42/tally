package dev.doughbay.fabric;

import dev.doughbay.storage.Database;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class EvasionHistoryTest {
    @TempDir Path root;
    @Test void includesBothHiveAccountsAndKeepsTheExistingSummaryMeanings()throws Exception{
        Path ledger=root.resolve("ledger.db");long now=1_000_000_000L;
        try(var db=new Database(ledger);var insert=db.connection().prepareStatement("INSERT INTO evasion_events(client,at,who,distance,action) VALUES(?,?,?,?,?)")){
            Object[][] events={{"main",now-10_000,"Alex",20,"sighted"},{"alt",now-9000,"Alex",10,"paused"},
                    {"main",now-8000,"",0,"move-on teleport"},{"alt",now-7000,"Mod",0,"Staff Watch: disconnected"},
                    {"main",now-25*3_600_000L,"Alex",10,"paused"},{"main",now-8*24*3_600_000L,"Old",1,"paused"}};
            for(var event:events){for(int i=0;i<5;i++)insert.setObject(i+1,event[i]);insert.executeUpdate();}
        }
        byte[] before=Files.readAllBytes(ledger);var report=EvasionHistory.read(ledger,now);
        assertTrue(report.path("available").asBoolean());assertEquals(2,report.path("triggered24h").asInt());
        assertEquals("Alex",report.path("mostSeen7d").path("who").asText());assertEquals(3,report.path("mostSeen7d").path("count").asInt());
        assertEquals(6,report.path("events").size());assertEquals("alt",report.path("events").get(0).path("client").asText());
        assertEquals("main",report.path("events").get(1).path("client").asText());
        assertArrayEquals(before,Files.readAllBytes(ledger));
    }
    @Test void historyIsBoundedAndNewestFirst()throws Exception{
        Path ledger=root.resolve("ledger.db");
        try(var db=new Database(ledger);var query=db.connection().prepareStatement("INSERT INTO evasion_events(client,at,who,distance,action) VALUES('main',?,'Alex',2,'paused')")){
            for(int i=1;i<=110;i++){query.setLong(1,i);query.executeUpdate();}
        }
        var report=EvasionHistory.read(ledger,1000);assertEquals(100,report.path("events").size());
        assertEquals(110,report.path("events").get(0).path("at").asLong());assertEquals(11,report.path("events").get(99).path("at").asLong());
    }
    @Test void absentOrBrokenLedgerIsExplicitlyUnavailableAndNeverCreated()throws Exception{
        Path absent=root.resolve("absent.db");assertFalse(EvasionHistory.read(absent,1000).path("available").asBoolean());
        assertFalse(Files.exists(absent));
        Path broken=root.resolve("broken.db");Files.writeString(broken,"not a database");var report=EvasionHistory.read(broken,1000);
        assertFalse(report.path("available").asBoolean());assertTrue(report.path("events").isEmpty());
        assertFalse(report.toString().contains(broken.toString()));assertEquals("not a database",Files.readString(broken));
    }
}
