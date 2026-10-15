package dev.doughbay.fabric;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.helpers.NOPLogger;
import java.nio.file.*;
import java.sql.DriverManager;
import static org.junit.jupiter.api.Assertions.*;

class HiveSetupTest {
    @TempDir Path root;
    private Path instance(String name, boolean history) throws Exception {
        Path game=Files.createDirectories(root.resolve(name));
        Path dir=Files.createDirectories(game.resolve("config/doughbay"));
        Files.writeString(dir.resolve("config.json"),"{}");
        try(var c=DriverManager.getConnection("jdbc:sqlite:"+dir.resolve("doughbay.db"));var s=c.createStatement()) {
            s.execute("CREATE TABLE positions(id INTEGER PRIMARY KEY, client TEXT)");
            if(history)s.execute("INSERT INTO positions VALUES(1,'SourcePlayer')");
        }
        return game;
    }
    private Path data(Path game){return game.resolve("config/doughbay");}

    @Test void createAndJoinPreserveBothDataFoldersAndApplyOnlyOnRestart() throws Exception {
        Path main=instance("old launcher with spaces",true),alt=instance("Prism alt",false);
        byte[] original=Files.readAllBytes(data(main).resolve("doughbay.db"));
        byte[] local=Files.readAllBytes(data(alt).resolve("doughbay.db"));
        Files.writeString(data(main).resolve("secret.dat"),"private-source-value");
        HiveSetup.resolve(main,data(main));
        var m=HiveSetup.create(main,data(main),"SourcePlayer");
        assertFalse(HiveSetup.active(),"creating a link must not hot-switch a live controller");
        String code=HiveSetup.code(m);
        assertFalse(code.contains("private-source-value"));
        assertEquals(m,HiveSetup.join(alt,data(alt),code));
        assertArrayEquals(original,Files.readAllBytes(data(main).resolve("doughbay.db")));
        assertArrayEquals(local,Files.readAllBytes(data(alt).resolve("doughbay.db")));
        assertFalse(Files.exists(data(alt).resolve("secret.dat")));
        assertEquals(data(main).toRealPath(),HiveSetup.resolve(alt,data(alt)));
        assertTrue(HiveSetup.active());
        assertEquals(m,HiveSetup.create(main,data(main),"SourcePlayer"));
        assertEquals(m,HiveSetup.join(alt,data(alt),code));
        HiveSetup.resolve(root,root);
    }

    @Test void joiningAnInstanceWithHistoryNeverOverwritesOrMergesIt() throws Exception {
        Path main=instance("main",true),alt=instance("alt",true);
        var m=HiveSetup.create(main,data(main),"SourcePlayer");
        byte[] before=Files.readAllBytes(data(alt).resolve("doughbay.db"));
        var error=assertThrows(java.io.IOException.class,()->HiveSetup.join(alt,data(alt),HiveSetup.code(m)));
        assertTrue(error.getMessage().contains("trading history"));
        assertFalse(Files.exists(alt.resolve("gonuts-hive.json")));
        assertArrayEquals(before,Files.readAllBytes(data(alt).resolve("doughbay.db")));
    }

    @Test void malformedCodesMissingFoldersAndIdentityMismatchesAreRefused() throws Exception {
        Path main=instance("main",true),alt=instance("alt",false);
        var m=HiveSetup.create(main,data(main),"SourcePlayer");
        assertThrows(java.io.IOException.class,()->HiveSetup.decode("not a hive code"));
        assertThrows(java.io.IOException.class,()->HiveSetup.decode("GoNutsHive1:!!!"));
        assertThrows(java.io.IOException.class,()->HiveSetup.decode(HiveSetup.code(new HiveSetup.Membership(1,java.util.UUID.randomUUID().toString(),m.directory(),m.owner()))));
        Files.delete(data(main).resolve("doughbay.db"));
        assertThrows(java.io.IOException.class,()->HiveSetup.join(alt,data(alt),HiveSetup.code(m)));
        assertFalse(Files.exists(data(main).resolve("doughbay.db")));
        assertThrows(java.io.IOException.class,()->HiveSetup.resolve(main,data(main)));
    }

    @Test void existingHiveLinkCannotBeReplacedByAnotherCode() throws Exception {
        Path one=instance("one",false),two=instance("two",false),alt=instance("alt",false);
        var a=HiveSetup.create(one,data(one),"SourcePlayer");
        var b=HiveSetup.create(two,data(two),"OtherPlayer");
        HiveSetup.join(alt,data(alt),HiveSetup.code(a));
        String before=Files.readString(alt.resolve("gonuts-hive.json"));
        assertThrows(java.io.IOException.class,()->HiveSetup.join(alt,data(alt),HiveSetup.code(b)));
        assertEquals(before,Files.readString(alt.resolve("gonuts-hive.json")));
    }

    @Test void corruptOrCustomLedgersAndLocalOrdersAreNeverSilentlyAbandoned() throws Exception {
        Path main=instance("main",false),alt=instance("alt",false);
        var m=HiveSetup.create(main,data(main),"SourcePlayer");
        Files.writeString(data(alt).resolve("orders-bids.txt"),"pending order");
        assertThrows(java.io.IOException.class,()->HiveSetup.join(alt,data(alt),HiveSetup.code(m)));
        Files.delete(data(alt).resolve("orders-bids.txt"));
        Files.writeString(data(alt).resolve("doughbay.db"),"not sqlite");
        assertThrows(java.io.IOException.class,()->HiveSetup.join(alt,data(alt),HiveSetup.code(m)));
        Files.writeString(data(main).resolve("config.json"),"{\"databasePath\":\"other.db\"}");
        assertThrows(java.io.IOException.class,()->HiveSetup.decode(HiveSetup.code(m)));
    }

    @Test void onlyTheCreatingAccountInheritsSingleAccountOrderRecords() throws Exception {
        Path main=instance("main",true);
        Files.writeString(data(main).resolve("orders-bids.txt"),"source orders");
        HiveSetup.create(main,data(main),"SourcePlayer");
        Path alt=AccountRecords.directory(data(main),true,"OtherPlayer",NOPLogger.NOP_LOGGER);
        assertFalse(Files.exists(alt.resolve("orders-bids.txt")),"the alt must never inherit the owner's bids");
        Path owner=AccountRecords.directory(data(main),true,"SourcePlayer",NOPLogger.NOP_LOGGER);
        assertEquals("source orders",Files.readString(owner.resolve("orders-bids.txt")));
        assertEquals("source orders",Files.readString(data(main).resolve("orders-bids.txt")));
    }
}
