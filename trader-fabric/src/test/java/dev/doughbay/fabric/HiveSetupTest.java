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

    @Test void freshThirdAndFourthAccountsJoinWithoutChangingExistingMembersOrSettings() throws Exception {
        Path main=instance("many-members",true),third=instance("third",false),fourth=instance("fourth",false);
        try(var c=DriverManager.getConnection("jdbc:sqlite:"+data(main).resolve("doughbay.db"));var s=c.createStatement()) {
            s.execute("INSERT INTO positions VALUES(2,'SecondPlayer')");
        }
        Files.writeString(data(main).resolve("orders-bids.txt"),"owners bids");
        Path second=Files.createDirectories(data(main).resolve("accounts/SecondPlayer"));
        Files.writeString(second.resolve("audit-balance.txt"),"second balance");
        Files.writeString(data(third).resolve("config.json"),"{\"continuousMaxSessionSpend\":123,\"authorizedExecutionEnabled\":false}");
        Files.writeString(data(third).resolve("secret.dat"),"third-own-key");
        Path link=third.resolve("gonuts-dashboard/installation.json");Files.createDirectories(link.getParent());Files.writeString(link,"third-own-link");
        var m=HiveSetup.create(main,data(main),"SourcePlayer");
        byte[] db=Files.readAllBytes(data(main).resolve("doughbay.db")),local=Files.readAllBytes(data(third).resolve("config.json"));
        HiveSetup.joinFresh(third,data(third),HiveSetup.code(m),"ThirdPlayer");
        HiveSetup.joinFresh(fourth,data(fourth),HiveSetup.code(m),"FourthPlayer");
        Path profile=HiveCombine.configuration(m,"ThirdPlayer");
        var config=new com.fasterxml.jackson.databind.ObjectMapper().readTree(Files.readAllBytes(profile.resolve("config.json")));
        assertEquals(123,config.path("continuousMaxSessionSpend").asInt());
        assertFalse(config.path("authorizedExecutionEnabled").asBoolean());
        assertEquals(data(main).resolve("doughbay.db").toString(),config.path("databasePath").asText());
        assertEquals("third-own-key",Files.readString(profile.resolve("secret.dat")));
        assertEquals("third-own-link",Files.readString(link));
        assertFalse(Files.exists(AccountRecords.directory(profile,true,"ThirdPlayer",NOPLogger.NOP_LOGGER).resolve("orders-bids.txt")));
        assertEquals(data(main),HiveCombine.configuration(m,"SecondPlayer"));
        assertEquals("second balance",Files.readString(second.resolve("audit-balance.txt")));
        assertArrayEquals(db,Files.readAllBytes(data(main).resolve("doughbay.db")));
        assertArrayEquals(local,Files.readAllBytes(data(third).resolve("config.json")));
        assertEquals(m,HiveSetup.configured(fourth));
        assertEquals(m,HiveSetup.joinFresh(third,data(third),HiveSetup.code(m),"ThirdPlayer"));
    }

    @Test void freshJoinRefusesLocalHistoryDestinationOwnershipAndPendingCombinations() throws Exception {
        Path main=instance("source",true),used=instance("used",true),fresh=instance("fresh",false);
        var m=HiveSetup.create(main,data(main),"SourcePlayer");String code=HiveSetup.code(m);
        assertThrows(java.io.IOException.class,()->HiveSetup.joinFresh(used,data(used),code,"NewPlayer"));
        assertThrows(java.io.IOException.class,()->HiveSetup.joinFresh(fresh,data(fresh),code,"SourcePlayer"));
        Files.createDirectories(data(main).resolve("accounts/Existing"));
        assertThrows(java.io.IOException.class,()->HiveSetup.joinFresh(fresh,data(fresh),code,"existing"));
        Files.writeString(fresh.resolve("gonuts-hive-combine.json"),"queued");
        assertThrows(java.io.IOException.class,()->HiveSetup.joinFresh(fresh,data(fresh),code,"NewPlayer"));
        assertFalse(Files.exists(fresh.resolve("gonuts-hive.json")));
        assertFalse(Files.exists(used.resolve("gonuts-hive.json")));
        assertFalse(Files.exists(data(main).resolve("joined-profiles")));
    }

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
    @Test void joinedDiscordHostReadsAndUpdatesTheSharedPanelWithoutCopyingCredentials() throws Exception {
        Path main=instance("discord-owner",true),third=instance("discord-joined",false);
        HiveSetup.resolve(main,data(main));
        var original=new dev.doughbay.fabric.discord.DiscordConfig("test-token","test-guild","test-channel","test-operator","old-panel");
        original.save(data(main));
        var membership=HiveSetup.create(main,data(main),"SourcePlayer");
        HiveSetup.joinFresh(third,data(third),HiveSetup.code(membership),"ThirdPlayer");
        try {
            Path profile=HiveSetup.resolve(third,data(third),"ThirdPlayer");
            assertNotEquals(data(main),profile);
            var loaded=dev.doughbay.fabric.discord.DiscordConfig.load(profile);
            assertTrue(loaded.complete(),"an elected joined host must find the existing Hive bot");
            assertEquals("old-panel",loaded.messageId());
            loaded.withMessageId("replacement-panel").save(profile);
            assertEquals("replacement-panel",dev.doughbay.fabric.discord.DiscordConfig.load(data(main)).messageId());
            assertFalse(Files.exists(profile.resolve("discord.json")),"credentials must not be copied to joined profiles");
            assertTrue(Files.exists(profile.resolve("config.json")),"per-account trading configuration stays separate");
        } finally { HiveSetup.resolve(root.resolve("unlinked"),root); }
    }

    @Test void soloDiscordConfigurationStaysLocal() throws Exception {
        Path game=instance("discord-solo",false);
        HiveSetup.resolve(game,data(game));
        try {
            var original=new dev.doughbay.fabric.discord.DiscordConfig("solo-token","guild","channel","operator","panel");
            original.save(data(game));
            assertEquals(original,dev.doughbay.fabric.discord.DiscordConfig.load(data(game)));
            assertEquals(data(game).resolve("discord.json"),dev.doughbay.fabric.discord.DiscordConfig.file(data(game)));
        } finally { HiveSetup.resolve(root.resolve("unlinked"),root); }
    }
    @Test void replacementInstanceRestoresSavedAccountWithoutChangingAnyOwnedRecords() throws Exception {
        Path main=instance("restore-owner",true),old=instance("deleted-instance",false),replacement=instance("replacement",false);
        var m=HiveSetup.create(main,data(main),"SourcePlayer");
        HiveSetup.joinFresh(old,data(old),HiveSetup.code(m),"ThirdPlayer");
        Path profile=HiveCombine.configuration(m,"ThirdPlayer");
        Files.writeString(profile.resolve("orders-bids.txt"),"owned pending orders");
        try(var c=DriverManager.getConnection("jdbc:sqlite:"+data(main).resolve("doughbay.db"));var q=c.createStatement()) {
            q.execute("INSERT INTO positions VALUES(2,'ThirdPlayer')");
        }
        byte[] ledger=Files.readAllBytes(data(main).resolve("doughbay.db")),settings=Files.readAllBytes(profile.resolve("config.json"));
        assertThrows(java.io.IOException.class,()->HiveSetup.joinFresh(replacement,data(replacement),HiveSetup.code(m),"ThirdPlayer"));
        assertEquals(m,HiveSetup.restoreExisting(replacement,data(replacement),HiveSetup.code(m),"ThirdPlayer",()->false));
        assertEquals(m,HiveSetup.configured(replacement));
        assertArrayEquals(ledger,Files.readAllBytes(data(main).resolve("doughbay.db")));
        assertArrayEquals(settings,Files.readAllBytes(profile.resolve("config.json")));
        assertEquals("owned pending orders",Files.readString(profile.resolve("orders-bids.txt")));
        assertTrue(Files.exists(old.resolve("gonuts-hive.json")),"the original instance is not deleted or rewritten");
    }

    @Test void restoreRefusesOtherRunningInstanceUnknownAccountAndLocalHistory() throws Exception {
        Path main=instance("restore-source",true),fresh=instance("restore-fresh",false),used=instance("restore-used",true);
        var m=HiveSetup.create(main,data(main),"SourcePlayer");String code=HiveSetup.code(m);
        assertThrows(java.io.IOException.class,()->HiveSetup.restoreExisting(fresh,data(fresh),code,"SourcePlayer",()->true));
        assertThrows(java.io.IOException.class,()->HiveSetup.restoreExisting(fresh,data(fresh),code,"UnknownPlayer",()->false));
        assertThrows(java.io.IOException.class,()->HiveSetup.restoreExisting(used,data(used),code,"SourcePlayer",()->false));
        assertFalse(Files.exists(fresh.resolve("gonuts-hive.json")));
        assertFalse(Files.exists(used.resolve("gonuts-hive.json")));
    }

    @Test void restoreRefusesMissingPreservedProfileAndKeepsExistingLink() throws Exception {
        Path main=instance("restore-missing",true),old=instance("restore-old",false),fresh=instance("restore-new",false),other=instance("restore-other",false);
        var m=HiveSetup.create(main,data(main),"SourcePlayer");String code=HiveSetup.code(m);
        HiveSetup.joinFresh(old,data(old),code,"ThirdPlayer");
        Path profile=HiveCombine.configuration(m,"ThirdPlayer");
        Files.delete(profile.resolve("config.json"));
        assertThrows(java.io.IOException.class,()->HiveSetup.restoreExisting(fresh,data(fresh),code,"ThirdPlayer",()->false));
        assertFalse(Files.exists(fresh.resolve("gonuts-hive.json")));
        var different=HiveSetup.create(other,data(other),"OtherPlayer");
        String before=Files.readString(other.resolve("gonuts-hive.json"));
        assertThrows(java.io.IOException.class,()->HiveSetup.restoreExisting(other,data(other),code,"SourcePlayer",()->false));
        assertEquals(before,Files.readString(other.resolve("gonuts-hive.json")));
        assertEquals(different,HiveSetup.configured(other));
    }
}
