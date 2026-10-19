package dev.doughbay.fabric.discord;

import dev.doughbay.storage.Database;
import dev.doughbay.storage.StaffWatchAlerts;
import dev.doughbay.storage.ConnectionAlerts;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DiscordAlertRoutingTest {
    private DiscordConfig config(String feed) {
        return new DiscordConfig("test-token", "", "panel", "operator", "panel-message", feed);
    }
    private void seed(Database db) throws Exception {
        StaffWatchAlerts.record(db.connection(),1000,"test-client","test-staff",StaffWatchAlerts.REGION_ESCAPE_ACTION);
        ConnectionAlerts.record(db.connection(),2000,"test-client","refused","connection lost");
    }
    @Test void panelOnlyConfigurationNeverPostsAlertsOrAcknowledgesPendingRows() throws Exception {
        try(var db=Database.inMemory()) {
            seed(db); var sent=new ArrayList<String>();
            assertFalse(DiscordBridge.deliverAlerts(db.connection(),config(""),(channel,payload)->{
                sent.add(channel);return "message";
            },3000));
            assertTrue(sent.isEmpty(),"blank activity destination must not fall back to the panel");
            assertEquals(1,StaffWatchAlerts.pending(db.connection()).size());
            assertEquals(1,ConnectionAlerts.pending(db.connection()).size());
        }
    }
    @Test void settingActivityToPanelCannotBuryThePanelEither() throws Exception {
        assertFalse(config("panel").hasFeed());
        try(var db=Database.inMemory()) {
            seed(db);
            assertFalse(DiscordBridge.deliverAlerts(db.connection(),config("panel"),(channel,payload)->{
                fail("no alert may post to the panel channel");return "";
            },3000));
            assertEquals(1,StaffWatchAlerts.pending(db.connection()).size());
            assertEquals(1,ConnectionAlerts.pending(db.connection()).size());
        }
    }
    @Test void explicitSeparateActivityChannelDeliversBothAlertsAndAcknowledgesOnlySuccess() throws Exception {
        try(var db=Database.inMemory()) {
            seed(db);var sent=new ArrayList<String>();var payloads=new ArrayList<String>();
            assertTrue(DiscordBridge.deliverAlerts(db.connection(),config("activity"),(channel,payload)->{
                sent.add(channel);payloads.add(payload);return "message";
            },3000));
            assertEquals(java.util.List.of("activity","activity"),sent);
            assertTrue(payloads.get(0).contains("STAFF WATCH"));
            assertTrue(payloads.get(1).contains("CONNECTION LOST"));
            assertTrue(StaffWatchAlerts.pending(db.connection()).isEmpty());
            assertTrue(ConnectionAlerts.pending(db.connection()).isEmpty());
        }
    }
    @Test void failedSendLeavesBothRecordsPending() throws Exception {
        try(var db=Database.inMemory()) {
            seed(db);
            assertFalse(DiscordBridge.deliverAlerts(db.connection(),config("activity"),(channel,payload)->"",3000));
            assertEquals(1,StaffWatchAlerts.pending(db.connection()).size());
            assertEquals(1,ConnectionAlerts.pending(db.connection()).size());
        }
    }
    @Test void interruptionNeverAcknowledgesAnUnsentRecord() throws Exception {
        try(var db=Database.inMemory()) {
            seed(db);
            assertThrows(InterruptedException.class,()->DiscordBridge.deliverAlerts(db.connection(),config("activity"),
                    (channel,payload)->{throw new InterruptedException();},3000));
            assertEquals(1,StaffWatchAlerts.pending(db.connection()).size());
            assertEquals(1,ConnectionAlerts.pending(db.connection()).size());
        }
    }
}
