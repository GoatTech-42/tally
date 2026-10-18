package dev.doughbay.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.sql.*;
import static org.junit.jupiter.api.Assertions.*;

class HiveLedgerImportTest {
    @TempDir Path root;
    private Path ledger(String owner,long spend) throws Exception {
        Path file=root.resolve(owner+".db");
        try(var db=new Database(file);var s=db.connection().createStatement()) {
            s.execute("INSERT INTO positions(position_id,mode,item_key,stack_bucket,quantity,purchase_price,target_price,status,client) VALUES(100,'LIVE','stone','X64',64,100,200,'PURCHASED','"+owner+"')");
            s.execute("INSERT INTO retail_positions VALUES(100,100,5)");
            s.execute("INSERT INTO sale_buyers(position_id,buyer_name,item_key,quantity,sale_price,sold_at) VALUES(100,'Buyer','stone',64,200,30)");
            s.execute("INSERT INTO automation_session_state(client,session_open,run_mode,controller_state,trades_started,committed_spend,session_started_at,updated_at,uncertain_active) VALUES('"+owner+"',1,'CONTINUOUS','PAUSED',4,"+spend+",10,20,1)");
            s.execute("INSERT INTO transactions VALUES('same-receipt',1,'seller','', 'stone','stone',64,100,1.5625,0,'{}')");
        }
        return file;
    }
    @Test void overlappingIdsKeepBothPositionsOwnersRetailReferencesAndCapsWithoutTouchingOriginals() throws Exception {
        Path a=ledger("Alice",1000),b=ledger("Bob",2000),out=root.resolve("combined.db");
        byte[] beforeA=Files.readAllBytes(a),beforeB=Files.readAllBytes(b);
        var result=HiveLedgerImport.combine(a,"Alice",b,"Bob",out);assertEquals(1,result.secondPositions());
        assertArrayEquals(beforeA,Files.readAllBytes(a));assertArrayEquals(beforeB,Files.readAllBytes(b));
        try(var c=DriverManager.getConnection("jdbc:sqlite:"+out);var s=c.createStatement()) {
            try(var r=s.executeQuery("SELECT COUNT(*),COUNT(DISTINCT position_id),SUM(purchase_price) FROM positions")){assertTrue(r.next());assertEquals(2,r.getInt(1));assertEquals(2,r.getInt(2));assertEquals(200,r.getLong(3));}
            try(var r=s.executeQuery("SELECT COUNT(*) FROM retail_positions r JOIN positions p ON p.position_id=r.position_id WHERE r.root_position_id=p.position_id")){assertTrue(r.next());assertEquals(2,r.getInt(1));}
            try(var r=s.executeQuery("SELECT COUNT(*) FROM sale_buyers b JOIN positions p ON p.position_id=b.position_id")){assertTrue(r.next());assertEquals(2,r.getInt(1));}
            try(var r=s.executeQuery("SELECT client,committed_spend,uncertain_active,trades_started FROM automation_session_state ORDER BY client")){
                assertTrue(r.next());assertEquals("Alice",r.getString(1));assertEquals(1000,r.getLong(2));assertEquals(1,r.getInt(3));assertEquals(4,r.getInt(4));
                assertTrue(r.next());assertEquals("Bob",r.getString(1));assertEquals(2000,r.getLong(2));assertEquals(1,r.getInt(3));assertFalse(r.next());
            }
            try(var r=s.executeQuery("SELECT COUNT(*) FROM transactions")){assertTrue(r.next());assertEquals(1,r.getInt(1));}
        }
        assertThrows(Exception.class,()->HiveLedgerImport.combine(a,"Alice",b,"Bob",out));
    }
    @Test void legacyFallbackSessionsRemainArchivedWithoutReplacingOwnedCheckpoints() throws Exception {
        Path a=ledger("Alice",1000),b=ledger("Bob",2000),out=root.resolve("legacy.db");
        for(Path file:java.util.List.of(a,b))try(var db=new Database(file);var s=db.connection().createStatement()) {
            s.execute("INSERT INTO automation_session_state SELECT '',session_open,run_mode,controller_state,0,0,session_started_at,updated_at,detail,bound_listing_key,uncertain_active,uncertain_phase,uncertain_listing_key,uncertain_item_key,uncertain_item_id,uncertain_item_count,uncertain_buy_price,uncertain_target_price,uncertain_seller,uncertain_observed_at,session_active_millis,lifetime_active_millis FROM automation_session_state LIMIT 1");
        }
        HiveLedgerImport.combine(a,"Alice",b,"Bob",out);
        try(var db=new Database(out);var s=db.connection().createStatement();var r=s.executeQuery("SELECT COUNT(*),SUM(committed_spend) FROM automation_session_state")) {
            assertTrue(r.next());assertEquals(4,r.getInt(1));assertEquals(3000,r.getLong(2));
        }
    }
    @Test void receiptConflictsAndOtherAccountOwnershipNeverBecomeSuccessfulImports() throws Exception {
        Path a=ledger("Alice",1),b=ledger("Bob",2);
        try(var db=new Database(b);var s=db.connection().createStatement()){s.execute("UPDATE transactions SET total_price=999");}
        assertThrows(SQLException.class,()->HiveLedgerImport.combine(a,"Alice",b,"Bob",root.resolve("conflict.db")));
        try(var db=new Database(b);var s=db.connection().createStatement()){s.execute("UPDATE positions SET client='Other'");}
        assertThrows(SQLException.class,()->HiveLedgerImport.inspect(b,"Bob"));
    }
    @Test void payoutRulesAndQueuedCommandsAreNotSilentlyActivatedForAnotherAccount() throws Exception {
        Path a=ledger("Alice",1);
        try(var db=new Database(a);var s=db.connection().createStatement()){s.execute("INSERT INTO hive_control(target,command,issued_at) VALUES('Alice','START',1)");}
        assertThrows(SQLException.class,()->HiveLedgerImport.inspect(a,"Alice"));
    }
}
