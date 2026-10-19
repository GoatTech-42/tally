package dev.doughbay.fabric;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import dev.doughbay.storage.Database;
import java.nio.file.*;
import java.nio.channels.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class HiveCombineTest {
    @TempDir Path root;
    private HiveSetup.Membership hive(String owner,int cap) throws Exception {
        Path game=Files.createDirectories(root.resolve(owner)),dir=Files.createDirectories(game.resolve("config/doughbay"));
        Files.writeString(dir.resolve("config.json"),"{\"continuousMaxSessionSpend\":"+cap+"}");
        Files.writeString(dir.resolve("tuning.json"),"{\"evasion.radius_blocks\":"+cap+",\"prices.own\":\"stone 1 2\"}");
        Files.writeString(dir.resolve("orders-collected.txt"),owner+"-own-record");
        Files.writeString(dir.resolve("saved-presets.json"),"{\"fixture\":"+cap+"}");
        Files.writeString(dir.resolve("evasion.txt"),owner.equals("Alice")?"on":"off");
        Files.writeString(dir.resolve("market-rests.txt"),owner+"-rest");
        Files.writeString(dir.resolve("follow-state.tsv"),owner+"-follow");
        try(var db=new Database(dir.resolve("doughbay.db"));var s=db.connection().createStatement()) {
            s.execute("INSERT INTO positions(position_id,mode,item_key,stack_bucket,quantity,purchase_price,target_price,status,client) VALUES(100,'LIVE','stone','X64',64,100,200,'PURCHASED','"+owner+"')");
        }
        return HiveSetup.create(game,dir,owner);
    }
    private HiveCombine.Plan plan(HiveSetup.Membership a,HiveSetup.Membership b) {
        String id=UUID.randomUUID().toString();
        return new HiveCombine.Plan(1,a,b,new HiveSetup.Membership(1,id,Path.of(b.directory()).resolveSibling("gonuts-combined-"+id).toString(),a.owner()));
    }
    @Test void twoExistingHivesSwitchTogetherWithSeparateProfilesAndUntouchedLedgers() throws Exception {
        var a=hive("Alice",111);var b=hive("Bob",222);var p=plan(a,b);
        var json=new com.fasterxml.jackson.databind.ObjectMapper();
        Path originalConfig=Path.of(a.directory()).resolve("config.json");
        var originalSettings=(com.fasterxml.jackson.databind.node.ObjectNode)json.readTree(Files.readAllBytes(originalConfig));
        originalSettings.put("databasePath",Path.of(a.directory()).resolve("doughbay.db").toString());
        Files.write(originalConfig,json.writeValueAsBytes(originalSettings));
        byte[] first=Files.readAllBytes(Path.of(a.directory()).resolve("doughbay.db")),second=Files.readAllBytes(Path.of(b.directory()).resolve("doughbay.db"));
        HiveCombine.complete(p,root.resolve("no-presence"));
        assertEquals(p.combined(),HiveSetup.configured(root.resolve("Alice")));assertEquals(p.combined(),HiveSetup.configured(root.resolve("Bob")));
        assertArrayEquals(first,Files.readAllBytes(Path.of(a.directory()).resolve("doughbay.db")));assertArrayEquals(second,Files.readAllBytes(Path.of(b.directory()).resolve("doughbay.db")));
        Path alice=HiveCombine.configuration(p.combined(),"Alice"),bob=HiveCombine.configuration(p.combined(),"Bob");
        assertTrue(Files.readString(alice.resolve("config.json")).contains("111"));assertTrue(Files.readString(bob.resolve("config.json")).contains("222"));
        assertEquals(Path.of(p.combined().directory()).resolve("doughbay.db").toString(),json.readTree(Files.readAllBytes(alice.resolve("config.json"))).path("databasePath").asText());
        assertEquals(Path.of(a.directory()).resolve("doughbay.db").toString(),json.readTree(Files.readAllBytes(originalConfig)).path("databasePath").asText());
        assertEquals("Alice-own-record",Files.readString(alice.resolve("orders-collected.txt")));assertEquals("Bob-own-record",Files.readString(bob.resolve("orders-collected.txt")));
        assertTrue(Files.readString(alice.resolve("saved-presets.json")).contains("111"));assertTrue(Files.readString(bob.resolve("saved-presets.json")).contains("222"));
        assertEquals("on",Files.readString(alice.resolve("evasion.txt")));assertEquals("off",Files.readString(bob.resolve("evasion.txt")));
        assertEquals("Bob-rest",Files.readString(bob.resolve("market-rests.txt")));
        assertEquals("Bob-follow",Files.readString(bob.resolve("accounts/Bob/follow-state.tsv")));
        assertEquals("Bob-own-record",Files.readString(bob.resolve("accounts/Bob/orders-collected.txt")));
        assertEquals(2,HiveSetup.read(Path.of(a.directory()).resolve("hive.json")).version());
        assertEquals(1,HiveSetup.read(Path.of(a.directory()).resolve("hive-before-combine.json")).version());
        HiveCombine.complete(p,root.resolve("no-presence")); // committed retry is idempotent
        assertThrows(Exception.class,()->HiveCombine.configuration(p.combined(),"Other"));
    }
    @Test void runningInstanceLeaseBlocksAllPublicationAndPreservesSourceLinks() throws Exception {
        var a=hive("Alice",111);var b=hive("Bob",222);var p=plan(a,b);
        try(var channel=FileChannel.open(Path.of(a.directory()).resolve("hive-use.lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE);var lock=channel.lock()) {
            assertThrows(Exception.class,()->HiveCombine.complete(p,root.resolve("no-presence")));
            assertFalse(Files.exists(Path.of(a.directory()).resolve("hive-combined-into.json")));
            assertEquals(a,HiveSetup.configured(root.resolve("Alice")));
        }
    }
    @Test void incompleteStagingIsArchivedRatherThanOverwritten() throws Exception {
        var a=hive("Alice",111);var b=hive("Bob",222);var p=plan(a,b);
        Path staged=Files.createDirectory(Path.of(p.combined().directory()));Files.writeString(staged.resolve("receipt.txt"),"keep me");
        HiveCombine.complete(p,root.resolve("no-presence"));
        try(var files=Files.list(staged.getParent())) {
            var archived=files.filter(f->f.getFileName().toString().startsWith(staged.getFileName()+".incomplete-")).findFirst().orElseThrow();
            assertEquals("keep me",Files.readString(archived.resolve("receipt.txt")));
        }
    }
    @Test void interruptedLinkPublicationBlocksDivergenceAndResumesWithoutReimporting() throws Exception {
        var a=hive("Alice",111);var b=hive("Bob",222);var p=plan(a,b);
        HiveCombine.complete(p,root.resolve("no-presence"));
        Path out=Path.of(p.combined().directory());
        Files.delete(out.resolve("hive-combine-ready.json"));Files.delete(Path.of(b.directory()).resolve("hive-combined-into.json"));
        assertThrows(Exception.class,()->HiveSetup.configured(root.resolve("Alice")));
        HiveCombine.complete(p,root.resolve("no-presence"));
        assertEquals(p.combined(),HiveSetup.configured(root.resolve("Alice")));assertEquals(p.combined(),HiveSetup.configured(root.resolve("Bob")));
        try(var db=new Database(out.resolve("doughbay.db"));var s=db.connection().createStatement();var r=s.executeQuery("SELECT COUNT(*) FROM positions")){
            assertTrue(r.next());assertEquals(2,r.getInt(1));
        }
    }
    @Test void platformAllowsTwoSharedProcessLeasesAndRefusesExclusiveImportLease() throws Exception {
        Path source=root.resolve("LeasePeer.java"),lock=root.resolve("shared.lock");
        Files.writeString(source,"""
                import java.nio.channels.*;import java.nio.file.*;
                class LeasePeer { public static void main(String[] args)throws Exception {
                  try(var c=FileChannel.open(Path.of(args[0]),StandardOpenOption.CREATE,StandardOpenOption.READ,StandardOpenOption.WRITE);var l=c.lock(0,Long.MAX_VALUE,true)) {
                    System.out.println("READY");System.out.flush();System.in.read();
                  }
                }}
                """);
        Process peer=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java").toString(),source.toString(),lock.toString()).redirectErrorStream(true).start();
        try(var reader=new java.io.BufferedReader(new java.io.InputStreamReader(peer.getInputStream()))) {
            assertEquals("READY",reader.readLine());
            try(var c=FileChannel.open(lock,StandardOpenOption.READ,StandardOpenOption.WRITE);var shared=c.tryLock(0,Long.MAX_VALUE,true)){assertNotNull(shared);}
            try(var c=FileChannel.open(lock,StandardOpenOption.READ,StandardOpenOption.WRITE)){assertNull(c.tryLock());}
            peer.getOutputStream().close();assertTrue(peer.waitFor(10,java.util.concurrent.TimeUnit.SECONDS));assertEquals(0,peer.exitValue());
        }finally{peer.destroyForcibly();}
    }
    @Test void interruptedCombinationRejectsChangedStagedLedger() throws Exception {
        var a=hive("Alice",111);var b=hive("Bob",222);var p=plan(a,b);
        HiveCombine.complete(p,root.resolve("no-presence"));
        Path out=Path.of(p.combined().directory());
        Files.delete(out.resolve("hive-combine-ready.json"));
        try(var db=new Database(out.resolve("doughbay.db"));var s=db.connection().createStatement()) {
            s.execute("UPDATE positions SET quantity=1");
        }
        assertThrows(Exception.class,()->HiveCombine.complete(p,root.resolve("no-presence")));
        assertFalse(Files.exists(out.resolve("hive-combine-ready.json")));
        assertThrows(Exception.class,()->HiveSetup.configured(root.resolve("Alice")));
        try(var db=new Database(Path.of(a.directory()).resolve("doughbay.db"));var s=db.connection().createStatement();var r=s.executeQuery("SELECT quantity FROM positions")) {
            assertTrue(r.next());assertEquals(64,r.getInt(1));
        }
    }
    @Test void queuedCombinationCanBeCancelledWithoutChangingEitherHive() throws Exception {
        var a=hive("Alice",111);var b=hive("Bob",222);
        HiveCombine.queue(root.resolve("Bob"),HiveSetup.code(a));
        assertEquals(a,HiveSetup.configured(root.resolve("Alice")));assertEquals(b,HiveSetup.configured(root.resolve("Bob")));
        HiveCombine.cancelPending(root.resolve("Bob"));
        assertFalse(Files.exists(root.resolve("Bob/gonuts-hive-combine.json")));
        HiveCombine.completePending(root.resolve("Bob"));
        assertEquals(a,HiveSetup.configured(root.resolve("Alice")));assertEquals(b,HiveSetup.configured(root.resolve("Bob")));
    }

    @Test void malformedPendingRequestKeepsOriginalsAndOffersRecoveryInsteadOfThrowingAgain() throws Exception {
        var a=hive("Alice",111);var b=hive("Bob",222);
        Path request=root.resolve("Bob/gonuts-hive-combine.json");Files.writeString(request,"{broken");
        assertDoesNotThrow(()->HiveCombine.completePending(root.resolve("Bob")));
        assertTrue(Files.exists(request));
        assertTrue(HiveCombine.startupMessage.contains("queued setup request is invalid"));
        assertFalse(HiveCombine.startupMessage.contains("start this one first"));
        assertEquals(a,HiveSetup.configured(root.resolve("Alice")));
        assertEquals(b,HiveSetup.configured(root.resolve("Bob")));
        Files.writeString(request,"{\"version\":1}");
        assertDoesNotThrow(()->HiveCombine.completePending(root.resolve("Bob")));
        assertTrue(HiveCombine.startupMessage.contains("queued setup request is invalid"));
    }
    @Test void failedLedgerValidationDoesNotPrescribeRestartOrderOrLeakPaths() {
        String message=HiveCombine.failureMessage(new java.sql.SQLException("Conflicting sale receipt; private-folder/account"));
        assertTrue(message.contains("safe-merge checks"));
        assertTrue(message.contains("Restarting alone will not resolve"));
        assertFalse(message.contains("private-folder"));
        assertFalse(message.contains("start the instance"));
        assertTrue(HiveCombine.failureMessage(new java.io.IOException("Close both Minecraft instances")).contains("Close BOTH"));
        assertTrue(HiveCombine.failureMessage(new java.sql.SQLException("Hive schemas differ")).contains("same build"));
        assertTrue(HiveCombine.failureMessage(new java.sql.SQLException("payroll or loan records")).contains("cannot merge safely"));
        assertTrue(HiveCombine.failureMessage(new java.nio.file.AccessDeniedException("private-folder")).contains("inaccessible"));
    }

    @Test void malformedRequestCannotBypassAnIncompletePublishedRedirect() throws Exception {
        var a=hive("Alice",111);var b=hive("Bob",222);var p=plan(a,b);
        HiveCombine.complete(p,root.resolve("no-presence"));
        Files.delete(Path.of(p.combined().directory()).resolve("hive-combine-ready.json"));
        Files.writeString(root.resolve("Bob/gonuts-hive-combine.json"),"{broken");
        assertThrows(java.io.IOException.class,()->HiveSetup.resolve(root.resolve("Bob"),Path.of(b.directory())));
        assertTrue(Files.exists(Path.of(a.directory()).resolve("doughbay.db")));
        assertTrue(Files.exists(Path.of(b.directory()).resolve("hive-combined-into.json")));
    }
}
