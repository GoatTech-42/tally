package dev.doughbay.fabric;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.helpers.NOPLogger;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccountRecordsTest {
    @Test
    void aClientRunningAloneKeepsItsRecordsWhereTheyAlwaysWere(@TempDir Path shared) {
        assertEquals(shared, AccountRecords.directory(shared, false, "TraderOne", NOPLogger.NOP_LOGGER));
        // A hive whose account is not known yet has nowhere of its own to put them.
        assertEquals(shared, AccountRecords.directory(shared, true, "", NOPLogger.NOP_LOGGER));
        assertEquals(shared, AccountRecords.directory(shared, true, null, NOPLogger.NOP_LOGGER));
        assertFalse(Files.exists(shared.resolve("accounts")));
    }

    @Test
    void eachAccountOfAHiveHasItsOwnAndStartsFromWhatWasThere(@TempDir Path shared) throws Exception {
        Files.writeString(shared.resolve("audit-balance.txt"), "1000=751000000");
        Files.writeString(shared.resolve("orders-bids.txt"), "minecraft:diamond|100|64|1000\n");
        Files.writeString(shared.resolve("tuning.json"), "{}");

        Path one = AccountRecords.directory(shared, true, "TraderOne", NOPLogger.NOP_LOGGER);
        Path two = AccountRecords.directory(shared, true, ".Bedrock Two", NOPLogger.NOP_LOGGER);
        assertEquals(shared.resolve("accounts").resolve("TraderOne"), one);
        assertEquals(shared.resolve("accounts").resolve(".Bedrock_Two"), two);
        // The records there were come along the first time; the settings are everybody's and stay put.
        assertEquals("1000=751000000", Files.readString(one.resolve("audit-balance.txt")));
        assertTrue(Files.exists(two.resolve("orders-bids.txt")));
        assertFalse(Files.exists(one.resolve("tuning.json")));

        // From then on what one account writes is not what the other reads.
        Files.writeString(one.resolve("audit-balance.txt"), "2000=760000000");
        Files.writeString(two.resolve("audit-balance.txt"), "2000=40000000");
        assertEquals("2000=760000000", Files.readString(
                AccountRecords.directory(shared, true, "TraderOne", NOPLogger.NOP_LOGGER).resolve("audit-balance.txt")));
        // And the old shared file is not copied over an account's own a second time.
        Files.writeString(shared.resolve("audit-balance.txt"), "3000=1");
        assertEquals("2000=40000000", Files.readString(
                AccountRecords.directory(shared, true, ".Bedrock Two", NOPLogger.NOP_LOGGER).resolve("audit-balance.txt")));
    }

    @Test
    void aNameCannotPointOutOfTheFolder(@TempDir Path shared) {
        assertEquals(shared, AccountRecords.directory(shared, true, "..", NOPLogger.NOP_LOGGER));
        Path odd = AccountRecords.directory(shared, true, "../../elsewhere", NOPLogger.NOP_LOGGER);
        assertEquals(shared.resolve("accounts"), odd.getParent());
    }
}
