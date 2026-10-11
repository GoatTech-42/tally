package dev.doughbay.fabric;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpdateCheckTest {
    private static final String MOD = "9c6cd3aea4fc839d6e1e9ef8240a312595c7ba56a08beb4b8cfbdde3db009d72";
    private static final String OTHER = "c70a3c7a0edb2b1e07845d9033cc2a70520df0c76ebcf60e782ce254e23534c9";
    private static final long UPLOADED = Instant.parse("2026-10-11T08:36:36Z").toEpochMilli();
    private static final String URL = "https://github.com/cody-raves/Gonuts/releases/download/v0.1.0/GoNuts-0.1.0-mc26.2.jar";

    /** The shape GitHub answers with, cut down to what is read. */
    private static final String RELEASE = """
            {"tag_name":"v0.1.0","html_url":"https://github.com/example/mod/releases/tag/v0.1.0","assets":[
             {"name":"gonuts-cli-0.1.0.jar","digest":"sha256:%s","updated_at":"2026-09-23T14:54:35Z"},
             {"name":"SHA256SUMS.txt","digest":"sha256:%s","updated_at":"2026-10-11T08:36:34Z"},
             {"name":"GoNuts-0.1.0-mc26.2.jar","digest":"sha256:%s","updated_at":"2026-10-11T08:36:36Z",
              "size":21277403,"browser_download_url":"%s"},
             {"name":"gonuts-server-0.1.0.jar","digest":"sha256:%s","updated_at":"2026-09-23T14:54:35Z"}]}
            """.formatted(OTHER, OTHER, MOD.toUpperCase(), URL, OTHER);

    private static UpdateCheck.Release release(String sha, long size) {
        return new UpdateCheck.Release(sha, UPLOADED, "page", URL, size, "");
    }

    @Test
    void theModsJarIsPickedOutOfTheReleaseAndNotTheCliOrTheServer() throws Exception {
        UpdateCheck.Release release = UpdateCheck.parse(RELEASE);
        assertNotNull(release);
        assertEquals(MOD, release.sha256());
        assertEquals(UPLOADED, release.uploadedAt());
        assertEquals("https://github.com/example/mod/releases/tag/v0.1.0", release.page());
        assertEquals(URL, release.url());
        assertEquals(21_277_403L, release.size());
    }

    @Test
    void aReleaseWithNoModJarOrNoHashForItIsNothingToCompare() throws Exception {
        assertNull(UpdateCheck.parse("{\"assets\":[{\"name\":\"gonuts-cli-0.1.0.jar\",\"digest\":\"sha256:" + OTHER
                + "\",\"updated_at\":\"2026-09-23T14:54:35Z\"}]}"));
        assertNull(UpdateCheck.parse("{\"assets\":[{\"name\":\"GoNuts-0.1.0-mc26.2.jar\","
                + "\"updated_at\":\"2026-09-23T14:54:35Z\"}]}"));
        assertNull(UpdateCheck.parse("{}"));
    }

    @Test
    void theSameFileIsCurrentWheneverItWasBuilt() {
        // The released jar is always built a few minutes before it is put up.
        assertEquals(UpdateCheck.State.CURRENT, UpdateCheck.compare(MOD, UPLOADED - 300_000, release(MOD, 0)));
        assertEquals(UpdateCheck.State.CURRENT, UpdateCheck.compare(MOD.toUpperCase(), UPLOADED + 1, release(MOD, 0)));
    }

    @Test
    void aDifferentFilePutUpAfterThisOneWasBuiltIsAnUpdate() {
        assertEquals(UpdateCheck.State.NEWER, UpdateCheck.compare(OTHER, UPLOADED - 86_400_000L, release(MOD, 0)));
    }

    @Test
    void aBuildMadeSinceTheReleaseIsAheadOfItAndNotToldToUpdate() {
        assertEquals(UpdateCheck.State.AHEAD, UpdateCheck.compare(OTHER, UPLOADED + 3_600_000L, release(MOD, 0)));
    }

    @Test
    void anAgeReadsInTheLargestUnitThatFits() {
        assertEquals("0 min", UpdateCheck.age(-5));
        assertEquals("59 min", UpdateCheck.age(59 * 60_000L));
        assertEquals("3 h", UpdateCheck.age(3 * 3_600_000L));
        assertEquals("2 days", UpdateCheck.age(50 * 3_600_000L));
    }

    @Test
    void theBannerCarriesTheFirstFewChangesAndSaysHowManyMore() {
        var entries = Changelog.parse(String.join("\n",
                "## 2026-10-11 12:40 UTC", "- Changed: one", "",
                "## 2026-10-11 12:25 UTC", "- Fixed: two", "- Changed: three", "- New: four", ""));
        String dot = UpdateCheck.CHANGE;
        assertEquals(java.util.List.of(dot + "Changed: one", dot + "Fixed: two", dot + "Changed: three", "and 1 more"),
                UpdateCheck.headlines(entries, 3));
        assertEquals(java.util.List.of(dot + "Changed: one"), UpdateCheck.headlines(entries.subList(0, 1), 3));
        assertTrue(UpdateCheck.headlines(java.util.List.of(), 3).isEmpty());
    }

    @Test
    void theChecksumFileNamesTheModsJarAndNotTheOthers() {
        String sums = MOD.toUpperCase() + "  GoNuts-0.1.0-mc26.2.jar\n"
                + OTHER.toUpperCase() + "  gonuts-cli-0.1.0.jar\r\n"
                + OTHER.toUpperCase() + "  gonuts-server-0.1.0.jar\n";
        assertEquals(MOD, UpdateCheck.listed(sums));
        // The star some tools put before a name is not part of it.
        assertEquals(MOD, UpdateCheck.listed(MOD + " *GoNuts-0.1.0-mc26.2.jar\n"));
        assertNull(UpdateCheck.listed(OTHER + "  gonuts-cli-0.1.0.jar\n"));
        assertNull(UpdateCheck.listed("not a hash  GoNuts-0.1.0-mc26.2.jar\n"));
        assertNull(UpdateCheck.listed("<html>rate limited</html>"));
        assertNull(UpdateCheck.listed(""));
        assertNull(UpdateCheck.listed(null));
    }

    @Test
    void theNoticeSaysWhenABuildThatInstallsItselfWillDoSo() {
        String when = "installs when you close the game and is ready when you open it again";
        UpdateCheck.Alert ready = UpdateCheck.explain(UpdateCheck.State.READY, true, 0);
        assertTrue(ready.title().contains("downloaded"));
        assertTrue(String.join(" ", ready.paragraphs()).contains(when));
        UpdateCheck.Alert downloading = UpdateCheck.explain(UpdateCheck.State.NEWER, true, 3 * 3_600_000L);
        assertTrue(String.join(" ", downloading.paragraphs()).contains(when));
        assertTrue(downloading.paragraphs().get(0).contains("3 h ago"));
        // Not yet switched on: the offer says what switching it on will do, and how to do it by hand.
        UpdateCheck.Alert offer = UpdateCheck.explain(UpdateCheck.State.NEWER, false, 20 * 60_000L);
        String text = String.join(" ", offer.paragraphs());
        assertTrue(text.contains("Turn on auto-update"));
        assertTrue(text.contains(when));
        assertTrue(text.contains("close the game, download the jar"));
        assertEquals("GoNuts is up to date", UpdateCheck.explain(UpdateCheck.State.CURRENT, true, 0).title());
    }

    @Test
    void aJarIsOnlyEverFetchedFromTheProjectsOwnReleaseDownloads() {
        assertTrue(UpdateCheck.allowedDownload(URL));
        assertTrue(UpdateCheck.allowedDownload(URL.replace("Gonuts", "GoNuts")));
        assertFalse(UpdateCheck.allowedDownload(null));
        assertFalse(UpdateCheck.allowedDownload(""));
        assertFalse(UpdateCheck.allowedDownload("http://github.com/cody-raves/Gonuts/releases/download/v0.1.0/x.jar"));
        assertFalse(UpdateCheck.allowedDownload("https://github.com/someone-else/Gonuts/releases/download/v0.1.0/x.jar"));
        assertFalse(UpdateCheck.allowedDownload("https://github.com.example.net/cody-raves/gonuts/releases/download/x.jar"));
        assertFalse(UpdateCheck.allowedDownload("https://example.net/GoNuts-0.1.0-mc26.2.jar"));
        assertFalse(UpdateCheck.allowedDownload(
                "https://github.com/cody-raves/gonuts/releases/download/../../../someone-else/x.jar"));
        assertFalse(UpdateCheck.allowedDownload("https://github.com/cody-raves/gonuts/releases/download/v0.1.0/x.exe"));
    }

    private static Path modJar(Path file, String text) throws IOException {
        try (OutputStream out = Files.newOutputStream(file); JarOutputStream jar = new JarOutputStream(out)) {
            jar.putNextEntry(new JarEntry("fabric.mod.json"));
            jar.write(text.getBytes(StandardCharsets.UTF_8));
            jar.closeEntry();
        }
        return file;
    }

    @Test
    void aDownloadIsKeptOnlyWhenItIsTheFileTheReleaseDescribes(@TempDir Path dir) throws Exception {
        Path jar = modJar(dir.resolve("new.jar"), "{\"id\":\"doughbay\"}");
        String hash = UpdateCheck.sha256(jar);
        long size = Files.size(jar);
        UpdateCheck.accept(jar, release(hash, size));
        UpdateCheck.accept(jar, release(hash.toUpperCase(), 0));
        assertThrows(IOException.class, () -> UpdateCheck.accept(jar, release(OTHER, size)));
        assertThrows(IOException.class, () -> UpdateCheck.accept(jar, release(hash, size + 1)));
        // The right hash for a file that is not a mod is still not something to install.
        Path text = Files.writeString(dir.resolve("page.jar"), "<html>rate limited</html>");
        assertThrows(IOException.class, () -> UpdateCheck.accept(text, release(UpdateCheck.sha256(text), 0)));
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void theJarIsReplacedOnlyAfterTheGameHasGone(@TempDir Path dir) throws Exception {
        // A folder with a space and an apostrophe in it, as a player's own may have.
        Path mods = Files.createDirectories(dir.resolve("it's a mods folder"));
        Path running = modJar(mods.resolve("GoNuts-0.1.0-mc26.2.jar"), "{\"id\":\"doughbay\",\"build\":\"old\"}");
        Path fresh = modJar(dir.resolve("GoNuts-abcdef123456.jar"), "{\"id\":\"doughbay\",\"build\":\"new\"}");
        String oldHash = UpdateCheck.sha256(running);
        String newHash = UpdateCheck.sha256(fresh);
        Path log = dir.resolve("last-swap.txt");
        // Something standing in for the game: alive for a few seconds, then gone.
        Process game = new ProcessBuilder("powershell.exe", "-NoProfile", "-Command", "Start-Sleep -Seconds 6")
                .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
        Process helper = UpdateCheck.launchSwap(game.pid(), fresh, running, log);
        assertNotNull(helper);
        Thread.sleep(2_500);
        assertTrue(game.isAlive());
        assertEquals(oldHash, UpdateCheck.sha256(running), "nothing is touched while the game runs");
        assertTrue(helper.waitFor(60, TimeUnit.SECONDS), "the helper finishes once the game has gone");
        assertFalse(game.isAlive());
        assertEquals(0, helper.exitValue(), Files.exists(log) ? Files.readString(log) : "no log written");
        assertEquals(newHash, UpdateCheck.sha256(running));
        assertFalse(Files.exists(mods.resolve("GoNuts-0.1.0-mc26.2.jar.new")));
        assertTrue(Files.readString(log).contains("replaced"));
        // The download itself is left where it was: a second client sharing the folder still needs it.
        assertTrue(Files.exists(fresh));
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void aJarSomethingStillHasOpenIsLeftAsItIs(@TempDir Path dir) throws Exception {
        Path running = modJar(dir.resolve("GoNuts-0.1.0-mc26.2.jar"), "{\"id\":\"doughbay\",\"build\":\"old\"}");
        Path fresh = modJar(dir.resolve("GoNuts-abcdef123456.jar"), "{\"id\":\"doughbay\",\"build\":\"new\"}");
        String oldHash = UpdateCheck.sha256(running);
        Path log = dir.resolve("last-swap.txt");
        Process gone = new ProcessBuilder("powershell.exe", "-NoProfile", "-Command", "exit 0").start();
        gone.waitFor(30, TimeUnit.SECONDS);
        // The game closed and was opened again at once: the jar is in use by the new one.
        try (java.io.RandomAccessFile held = new java.io.RandomAccessFile(running.toFile(), "r")) {
            Process helper = UpdateCheck.launchSwap(gone.pid(), fresh, running, log);
            assertTrue(helper.waitFor(90, TimeUnit.SECONDS));
            assertEquals(2, helper.exitValue());
            assertEquals(1, held.read(new byte[1]));
        }
        assertEquals(oldHash, UpdateCheck.sha256(running));
        assertTrue(Files.readString(log).contains("could not replace"));
        assertFalse(Files.exists(dir.resolve("GoNuts-0.1.0-mc26.2.jar.new")));
    }
}
