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
        byte[] first=Files.readAllBytes(Path.of(a.directory()).resolve("doughbay.db")),second=Files.readAllBytes(Path.of(b.directory()).resolve("doughbay.db"));
        HiveCombine.complete(p,root.resolve("no-presence"));
        assertEquals(p.combined(),HiveSetup.configured(root.resolve("Alice")));assertEquals(p.combined(),HiveSetup.configured(root.resolve("Bob")));
        assertArrayEquals(first,Files.readAllBytes(Path.of(a.directory()).resolve("doughbay.db")));assertArrayEquals(second,Files.readAllBytes(Path.of(b.directory()).resolve("doughbay.db")));
        Path alice=HiveCombine.configuration(p.combined(),"Alice"),bob=HiveCombine.configuration(p.combined(),"Bob");
        assertTrue(Files.readString(alice.resolve("config.json")).contains("111"));assertTrue(Files.readString(bob.resolve("config.json")).contains("222"));
        assertEquals("Alice-own-record",Files.readString(alice.resolve("orders-collected.txt")));assertEquals("Bob-own-record",Files.readString(bob.resolve("orders-collected.txt")));
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
}
