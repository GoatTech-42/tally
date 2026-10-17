package dev.doughbay.fabric;

import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class BugReporterKeyTest {
    @TempDir Path root;
    @Test void anonymousKeySurvivesRetriesAndRestartWithoutSendingItsSecret() throws Exception {
        Path file=root.resolve("one/key.bin");
        String key=BugReporterKey.load(file);
        assertTrue(key.matches("[0-9a-f]{64}"));
        byte[] secret=Files.readAllBytes(file);
        assertEquals(32,secret.length);
        assertNotEquals(java.util.HexFormat.of().formatHex(secret),key);
        assertEquals(key,BugReporterKey.load(file));
        assertArrayEquals(secret,Files.readAllBytes(file));
        assertNotEquals(key,BugReporterKey.load(root.resolve("two/key.bin")));
    }
    @Test void corruptOrOversizedSecretIsNotRotatedOrUploaded() throws Exception {
        Path file=root.resolve("bad.bin");byte[] bytes=new byte[100];Files.write(file,bytes);
        assertThrows(java.io.IOException.class,()->BugReporterKey.load(file));
        assertArrayEquals(bytes,Files.readAllBytes(file));
    }
}
