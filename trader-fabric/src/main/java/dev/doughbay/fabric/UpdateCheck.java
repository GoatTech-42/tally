package dev.doughbay.fabric;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.jar.JarFile;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Says when the jar on the GitHub release is newer than the one running, and,
 * where the player has asked for it, fetches that jar and puts it in.
 *
 * <p>The release keeps one version number and has its jar replaced, so a
 * version cannot be compared. What is compared is the file: a release jar
 * that is not this one, put up after this one was built, is an update. A
 * build made since the release (somebody's own) is ahead of it and is left
 * alone.
 *
 * <p>By default it only looks: the player is told, with the link. With
 * {@code update.auto} on, the new jar is downloaded beside the config, checked
 * against the hash GitHub gives for it, and swapped in after the game has
 * closed. Never while it runs: the game has the jar open, and writing over it
 * then takes the client down.
 */
public final class UpdateCheck {
    static final String API = "https://api.github.com/repos/GoatTech-42/tally/releases/latest";
    static final String PAGE = "https://github.com/GoatTech-42/tally/releases";
    /** The only place a jar is ever fetched from. */
    static final String DOWNLOADS = "https://github.com/goattech-42/tally/releases/download/";
    /**
     * The release's checksum file: a few hundred bytes, fetched like any
     * download. GitHub's API allows an address sixty questions an hour, which
     * two clients in one house asking every two minutes would use up; this
     * file can be read as often as that, and the API is asked only when the
     * file names a jar not yet looked at.
     */
    static final String SUMS = "https://github.com/GoatTech-42/tally/releases/latest/download/SHA256SUMS.txt";
    /** How often the checksum file is read. A fix put up is on a running client's screen within this. */
    private static final long EVERY_MILLIS = 2 * 60_000L;
    /** The API is asked this often whatever the checksum file says, in case that file cannot be read. */
    private static final long FULL_MILLIS = 3 * 3_600_000L;
    private static final long FIRST_MILLIS = 20_000L;
    /** In the world this long before the notice, so it is not lost among the lines a join prints. */
    private static final long SETTLE_MILLIS = 10_000L;
    /** The mod's jar among the release's files: "Tally-0.2.0-mc26.2.jar" (an upstream-named "GoNuts-..." jar also matches), not the CLI or the server. */
    private static final Pattern MOD_JAR = Pattern.compile("(?i)^(gonuts|tally)-[0-9][^/]*-mc[^/]*\\.jar$");
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final boolean WINDOWS = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");

    /**
     * Run once the game is closing, by a process that outlives it. Waits for
     * the game to be gone, then replaces the jar in one step; a jar something
     * still has open is left as it is and the new one waits for next time.
     * Paths arrive in the environment, so nothing in them is ever read as code.
     */
    static final String SWAP_SCRIPT = String.join(" ",
            "$id=[int]$env:GONUTS_PID; $new=$env:GONUTS_NEW; $jar=$env:GONUTS_JAR; $log=$env:GONUTS_LOG;",
            "function Note($m) { try { Add-Content -LiteralPath $log -Value ((Get-Date -Format s) + ' ' + $m) } catch { } }",
            // Whether the game has gone is asked of the process itself. Looking it up in
            // the list of running processes straight after it exits can still find it there
            // for a moment, and that once read as "still running" and left the jar alone.
            "$gone = $false;",
            "try { $game = [Diagnostics.Process]::GetProcessById($id); $gone = $game.WaitForExit(300000) } catch { $gone = $true }",
            "if (-not $gone) { Note 'the game was still running after 5 minutes; jar left alone'; exit 1 }",
            "$tmp = $jar + '.new';",
            "for ($i = 0; $i -lt 30; $i++) {",
            "try { [IO.File]::Copy($new, $tmp, $true); [IO.File]::Replace($tmp, $jar, [NullString]::Value);",
            "Note ('replaced ' + $jar + ' with ' + $new); exit 0 }",
            "catch { $why = $_.Exception.Message; Start-Sleep -Milliseconds 500 } }",
            "try { [IO.File]::Delete($tmp) } catch { }",
            "Note ('could not replace ' + $jar + ': ' + $why); exit 2");

    public enum State { OFF, UNCHECKED, CURRENT, NEWER, READY, AHEAD, FAILED, NO_RELEASE }

    /** The mod's jar as the release has it. */
    record Release(String sha256, long uploadedAt, String page, String url, long size, String notes) { }

    /**
     * @param uploadedAt when the release's jar was put up, 0 when not known
     * @param detail     the release jar's hash when there is one, or why the check failed
     */
    public record Status(State state, long checkedAt, long uploadedAt, String page, String detail) { }

    private static volatile Status status = new Status(State.UNCHECKED, 0, 0, PAGE, "");
    private static volatile boolean started;
    /** A newer jar, downloaded and checked, waiting for the game to close. */
    private static volatile Path staged;
    private static volatile Path ownJar;
    private static long inWorldSince;
    private static String announced = "";
    /** The builds, by hash, whose notice the player has answered this session. */
    private static final java.util.Set<String> ANSWERED = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static final Object WAKE = new Object();
    private static boolean wake;
    /** Long enough to be read by someone who looked away. */
    private static final long BANNER_MILLIS = 20_000L;
    private static final int BANNER_TOP = 10;
    private static final int BANNER_WIDTH = 430;
    private static volatile long bannerSince;
    private static volatile String bannerTitle = "";
    private static volatile List<String> bannerLines = List.of();
    private static volatile boolean samplePending;
    /** What the release has that this build does not: its notes, the newest first. */
    private static volatile List<Changelog.Entry> news = List.of();
    /** What this build has that the one run before it did not; told once after an update. */
    private static volatile List<Changelog.Entry> changed = List.of();
    private static volatile boolean changedPending;
    private static boolean changedAnnounced;
    private static volatile List<Changelog.Entry> history;

    private UpdateCheck() { }

    public static Status status() {
        return status;
    }

    static void start() {
        if (started) return;
        started = true;
        Thread thread = new Thread(UpdateCheck::loop, "doughbay-update-check");
        thread.setDaemon(true);
        thread.start();
        Runtime.getRuntime().addShutdownHook(new Thread(UpdateCheck::swapOnExit, "doughbay-update-swap"));
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("doughbay", "update_banner"), UpdateCheck::hud);
    }

    /** The notes of the build on GitHub that this one does not have yet. */
    static List<Changelog.Entry> news() {
        return news;
    }

    /** The notes of what went in since the build this player last ran. */
    static List<Changelog.Entry> changed() {
        return changed;
    }

    /** Every note this build was made with, the newest first. */
    static List<Changelog.Entry> history() {
        List<Changelog.Entry> known = history;
        if (known != null) return known;
        List<Changelog.Entry> read = List.of();
        try (InputStream in = UpdateCheck.class.getResourceAsStream("/doughbay-changelog.md")) {
            if (in != null) read = List.copyOf(Changelog.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8)));
        } catch (IOException | RuntimeException e) {
            // A build without its notes still runs.
        }
        history = read;
        return read;
    }

    /**
     * Works out what is new to this player: the notes written since the build
     * they last ran. The build is remembered per jar, since two clients can
     * share one config folder. With no record yet the latest notes are this
     * build's own, and those are shown.
     */
    private static void noteInstalled(long built) {
        try {
            if (built <= 0) return;
            Path jar = ownJar();
            String which = Integer.toHexString((jar == null ? "" : jar.toAbsolutePath().toString()).hashCode());
            Path record = updateDirectory().resolve("seen-" + which + ".txt");
            long seen = -1;
            if (Files.isRegularFile(record)) {
                try {
                    seen = Long.parseLong(Files.readString(record).strip());
                } catch (NumberFormatException e) {
                    seen = -1;
                }
            }
            if (seen == built) return;
            List<Changelog.Entry> all = history();
            List<Changelog.Entry> fresh = seen < 0 ? all.subList(0, Math.min(1, all.size()))
                    : seen < built ? Changelog.after(all, seen) : List.<Changelog.Entry>of();
            Files.createDirectories(record.getParent());
            Files.writeString(record, Long.toString(built));
            if (!fresh.isEmpty()) {
                changed = List.copyOf(fresh);
                changedPending = true;
            }
        } catch (Exception e) {
            DoughBayClient.LOGGER.info("DoughBay update: what changed could not be worked out ({})", e.toString());
        }
    }

    private static void loop() {
        String own = null;
        long built = builtAt();
        // The first look is a full one; so is one the player asked for.
        boolean asked = true;
        noteInstalled(built);
        try {
            Thread.sleep(FIRST_MILLIS);
            while (true) {
                if (Tuning.get("update.check") < 0.5 && Tuning.get("update.auto") < 0.5) {
                    status = new Status(State.OFF, 0, 0, PAGE, "");
                } else {
                    if (own == null) {
                        ownJar = ownJar();
                        try {
                            own = ownJar == null ? null : sha256(ownJar);
                        } catch (IOException e) {
                            DoughBayClient.LOGGER.info("DoughBay update check: this jar could not be read ({})", e.toString());
                        }
                    }
                    look(own, built, asked);
                }
                synchronized (WAKE) {
                    if (!wake) WAKE.wait(EVERY_MILLIS);
                    asked = wake;
                    wake = false;
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** The hash the release gave for its jar at the last full look, and when that was. */
    private static String lookedAt = "";
    private static Release knownRelease;
    private static long lookedAtMillis;

    /**
     * One turn of the watch. Reads the checksum file, and asks the API for
     * the whole of it only when that file names a jar that has not been
     * looked at, when asked to, or when it has been a long while.
     */
    private static void look(String own, long built, boolean asked) {
        long now = System.currentTimeMillis();
        // No release published yet is a fact already known: the API is not asked again until the long wait has passed.
        if (!asked && status.state() == State.NO_RELEASE && now - lookedAtMillis < FULL_MILLIS) return;
        if (!asked && !lookedAt.isEmpty() && now - lookedAtMillis < FULL_MILLIS) {
            String listed = null;
            try {
                HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15))
                        .followRedirects(HttpClient.Redirect.NORMAL).build();
                HttpResponse<String> response = http.send(HttpRequest.newBuilder(URI.create(SUMS))
                        .timeout(Duration.ofSeconds(20)).header("User-Agent", "Tally-update-check")
                        .GET().build(), HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) listed = listed(response.body());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                // Offline for a moment: what is known stands, and the next turn asks again.
            }
            if (listed == null) return;
            if (listed.equalsIgnoreCase(lookedAt)) {
                // An unchanged release may still need downloading: auto-update
                // can have been enabled since the last look, or its fetch failed.
                Status s = status;
                try {
                    Path ready = fetchCachedIfNeeded(s, knownRelease, listed, Tuning.get("update.auto") >= 0.5,
                            release -> fetch(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15))
                                    .followRedirects(HttpClient.Redirect.NORMAL).build(), release, updateDirectory(), ownJar));
                    if (ready != null) {
                        staged = ready;
                        s = new Status(State.READY, now, s.uploadedAt(), s.page(), s.detail());
                        DoughBayClient.LOGGER.info("DoughBay update: published build downloaded; installs when the game closes");
                    }
                } catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
                catch (Exception e) { DoughBayClient.LOGGER.warn("DoughBay update: download will be retried at the next check"); }
                if (s.checkedAt() > 0) status = new Status(s.state(), now, s.uploadedAt(), s.page(), s.detail());
                return;
            }
        }
        check(own, built);
    }

    @FunctionalInterface interface CachedFetcher { Path fetch(Release release) throws Exception; }
    static Path fetchCachedIfNeeded(Status current, Release known, String listed, boolean automatic,
                                    CachedFetcher fetcher) throws Exception {
        if (!automatic || current.state() != State.NEWER || known == null
                || !known.sha256().equalsIgnoreCase(listed) || !known.sha256().equalsIgnoreCase(current.detail())) return null;
        return fetcher.fetch(known);
    }

    static void settingChanged(String key, double before, double after) {
        if ((key.equals("update.auto") || key.equals("update.check")) && before < 0.5 && after >= 0.5) checkNow();
    }

    /** The hash a checksum file gives for the mod's jar, or null when it names none. */
    static String listed(String sums) {
        if (sums == null) return null;
        for (String line : sums.split("\\r?\\n")) {
            String[] parts = line.strip().split("\\s+");
            if (parts.length != 2) continue;
            String name = parts[1].startsWith("*") ? parts[1].substring(1) : parts[1];
            if (MOD_JAR.matcher(name).matches() && parts[0].matches("[0-9A-Fa-f]{64}")) {
                return parts[0].toLowerCase(Locale.ROOT);
            }
        }
        return null;
    }

    private static void check(String own, long built) {
        long now = System.currentTimeMillis();
        try {
            if (own == null) throw new IOException("this jar could not be read");
            if (built <= 0) throw new IOException("this jar does not say when it was built");
            HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15))
                    .followRedirects(HttpClient.Redirect.NORMAL).build();
            HttpResponse<String> response = http.send(HttpRequest.newBuilder(URI.create(API))
                    .timeout(Duration.ofSeconds(20))
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "Tally-update-check")
                    .GET().build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 404) {
                // Tally has not published a release: that is the answer, not a failure.
                Status before = status;
                knownRelease = null;
                lookedAt = "";
                lookedAtMillis = now;
                staged = null;
                status = new Status(State.NO_RELEASE, now, 0, PAGE, "");
                if (before.state() != State.NO_RELEASE) {
                    DoughBayClient.LOGGER.info("Tally update check: no Tally release published yet");
                }
                return;
            }
            if (response.statusCode() != 200) throw new IOException("GitHub answered " + response.statusCode());
            Release release = parse(response.body());
            if (release == null) throw new IOException("the release lists no mod jar");
            knownRelease = release;
            lookedAt = release.sha256();
            lookedAtMillis = now;
            State state = compare(own, built, release);
            news = state == State.CURRENT ? List.of()
                    : List.copyOf(Changelog.after(Changelog.parse(release.notes()), built));
            if (state == State.NEWER && Tuning.get("update.auto") >= 0.5) {
                try {
                    staged = fetch(http, release, updateDirectory(), ownJar);
                    state = State.READY;
                } catch (InterruptedException e) {
                    throw e;
                } catch (Exception e) {
                    // Told about it instead; the download is tried again at the next look.
                    DoughBayClient.LOGGER.warn("DoughBay update: the new jar could not be fetched ({})", e.toString());
                }
            }
            if (state != State.READY) staged = null;
            Status before = status;
            status = new Status(state, now, release.uploadedAt(), release.page(), release.sha256());
            if (before.state() != state) {
                DoughBayClient.LOGGER.info("DoughBay update check: {}", switch (state) {
                    case NEWER -> "a newer build is on GitHub, at " + release.page();
                    case READY -> "a newer build is downloaded and goes in when the game closes";
                    case AHEAD -> "this build is newer than the one on GitHub";
                    default -> "this is the build on GitHub";
                });
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            // Offline, rate limited, GitHub down: say so on the About tab and try again later.
            Status before = status;
            if (before.state() != State.NEWER && before.state() != State.READY) {
                status = new Status(State.FAILED, now, 0, PAGE, e.getMessage());
            }
            if (before.state() != State.FAILED) {
                DoughBayClient.LOGGER.info("DoughBay update check: could not look ({})", e.toString());
            }
        }
    }

    /** The mod's jar out of GitHub's description of a release, or null when it lists none. */
    static Release parse(String json) throws IOException {
        JsonNode root = MAPPER.readTree(json);
        String page = root.path("html_url").asText(PAGE);
        for (JsonNode asset : root.path("assets")) {
            if (!MOD_JAR.matcher(asset.path("name").asText("")).matches()) continue;
            String digest = asset.path("digest").asText("");
            if (!digest.toLowerCase(Locale.ROOT).startsWith("sha256:")) continue;
            long uploaded;
            try {
                uploaded = Instant.parse(asset.path("updated_at").asText("")).toEpochMilli();
            } catch (RuntimeException e) {
                continue;
            }
            return new Release(digest.substring("sha256:".length()).toLowerCase(Locale.ROOT), uploaded,
                    page.isBlank() ? PAGE : page, asset.path("browser_download_url").asText(""),
                    asset.path("size").asLong(0), root.path("body").asText(""));
        }
        return null;
    }

    /**
     * Where this jar stands against the release's. A different file is an
     * update only when it was put up after this one was built.
     */
    static State compare(String ownSha256, long builtAt, Release release) {
        if (release.sha256().equalsIgnoreCase(ownSha256)) return State.CURRENT;
        return release.uploadedAt() > builtAt ? State.NEWER : State.AHEAD;
    }

    /** A jar is fetched from this project's own release downloads and nowhere else, whatever the answer names. */
    static boolean allowedDownload(String url) {
        return url != null && url.toLowerCase(Locale.ROOT).startsWith(DOWNLOADS) && !url.contains("..")
                && url.toLowerCase(Locale.ROOT).endsWith(".jar");
    }

    /**
     * The release's jar, downloaded into {@code directory} and proven to be
     * the file GitHub describes. One already there from an earlier look is
     * used as it is. The jar now running is copied beside it first, so there
     * is always the last one to go back to.
     */
    static Path fetch(HttpClient http, Release release, Path directory, Path running)
            throws IOException, InterruptedException {
        if (!allowedDownload(release.url())) throw new IOException("the release names a download that is not its own");
        Files.createDirectories(directory);
        Path target = directory.resolve("Tally-" + release.sha256().substring(0, 12) + ".jar");
        if (Files.isRegularFile(target) && release.sha256().equalsIgnoreCase(sha256(target))) return target;
        // Two clients can share this folder: each downloads under its own name.
        Path part = directory.resolve(target.getFileName() + "." + ProcessHandle.current().pid() + ".part");
        try {
            HttpResponse<Path> response = http.send(HttpRequest.newBuilder(URI.create(release.url()))
                    .timeout(Duration.ofMinutes(10)).header("User-Agent", "Tally-update-check").GET().build(),
                    HttpResponse.BodyHandlers.ofFile(part));
            if (response.statusCode() != 200) throw new IOException("the download answered " + response.statusCode());
            accept(part, release);
            Files.move(part, target, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(part);
        }
        // Older downloads are of no more use; the jar being replaced is kept.
        try (Stream<Path> files = Files.list(directory)) {
            for (Path old : files.toList()) {
                String name = old.getFileName().toString();
                if ((name.startsWith("Tally-") || name.startsWith("GoNuts-")) && name.endsWith(".jar") && !old.equals(target)) Files.deleteIfExists(old);
            }
        }
        if (running != null && Files.isRegularFile(running)) {
            Files.copy(running, directory.resolve("previous.jar"), StandardCopyOption.REPLACE_EXISTING);
        }
        DoughBayClient.LOGGER.info("DoughBay update: downloaded {} ({} bytes), hash matches the release",
                target.getFileName(), Files.size(target));
        return target;
    }

    /** Refuses a download that is not the release's jar: wrong size, wrong hash, or not this mod. */
    static void accept(Path file, Release release) throws IOException {
        long size = Files.size(file);
        if (release.size() > 0 && size != release.size()) {
            throw new IOException("the download is " + size + " bytes and the release says " + release.size());
        }
        String hash = sha256(file);
        if (!release.sha256().equalsIgnoreCase(hash)) {
            throw new IOException("the download's hash is not the release's");
        }
        try (JarFile jar = new JarFile(file.toFile())) {
            if (jar.getEntry("fabric.mod.json") == null) throw new IOException("the download is not a mod jar");
        }
    }

    private static Path updateDirectory() {
        return FabricLoader.getInstance().getConfigDir().resolve("doughbay").resolve("update");
    }

    /** The game is closing: hand the swap to something that will still be here once it has. */
    private static void swapOnExit() {
        Path from = staged;
        Path jar = ownJar;
        try {
            if (from == null || jar == null || !Files.isRegularFile(from)) return;
            if (Tuning.get("update.auto") < 0.5) return;
            launchSwap(ProcessHandle.current().pid(), from, jar, from.resolveSibling("last-swap.txt"));
        } catch (Throwable t) {
            // Closing must never fail on this; the download waits for the next close.
            DoughBayClient.LOGGER.warn("DoughBay update: the new jar could not be put in ({})", t.toString());
        }
    }

    /**
     * Replaces {@code jar} with {@code from} once process {@code pid} is gone.
     * Where a running program's file can be renamed over (not Windows) it is
     * done here and now.
     */
    static Process launchSwap(long pid, Path from, Path jar, Path log) throws IOException {
        if (!WINDOWS) {
            Path tmp = jar.resolveSibling(jar.getFileName() + ".new");
            Files.copy(from, tmp, StandardCopyOption.REPLACE_EXISTING);
            Files.move(tmp, jar, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            return null;
        }
        ProcessBuilder builder = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive",
                "-WindowStyle", "Hidden", "-Command", SWAP_SCRIPT);
        Map<String, String> environment = builder.environment();
        environment.put("GONUTS_PID", Long.toString(pid));
        environment.put("GONUTS_NEW", from.toAbsolutePath().toString());
        environment.put("GONUTS_JAR", jar.toAbsolutePath().toString());
        environment.put("GONUTS_LOG", log.toAbsolutePath().toString());
        builder.redirectErrorStream(true);
        builder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        return builder.start();
    }

    /**
     * Raises the notice once, a little after the player is in the world: a
     * toast in the corner, which takes nothing over. The notice with buttons
     * waits for the player to open the mod's screen.
     */
    static void tick(Minecraft client) {
        if (client == null || client.player == null) {
            inWorldSince = 0;
            return;
        }
        long now = System.currentTimeMillis();
        if (inWorldSince == 0) inWorldSince = now;
        if (now - inWorldSince < SETTLE_MILLIS) return;
        if (changedPending && !changedAnnounced) {
            // An update went in since the last run: say so before anything about the next one.
            changedAnnounced = true;
            String open = DoughBayClient.openKeyName();
            bannerTitle = "Tally has something new";
            List<String> lines = new ArrayList<>(headlines(changed, BANNER_CHANGES));
            lines.add(open.isEmpty() ? "Open Tally to see what changed" : "Press " + open + " to see what changed");
            bannerLines = List.copyOf(lines);
            bannerSince = now;
            return;
        }
        Status s = status;
        if (s.state() != State.NEWER && s.state() != State.READY) return;
        String key = s.state() + s.detail();
        // One banner at a time: the next waits for the one that is up.
        if (key.equals(announced) || now - bannerSince < BANNER_MILLIS) return;
        announced = key;
        raise(s.state(), now);
    }

    /** Puts the banner up for {@code state}: what there is, and which key shows the rest. */
    private static void raise(State state, long now) {
        String open = DoughBayClient.openKeyName();
        String more = open.isEmpty() ? "Open Tally for details" : "Press " + open + " for details";
        bannerTitle = state == State.READY ? "Tally update downloaded" : "Tally update available";
        List<String> lines = new ArrayList<>();
        if (state == State.READY) lines.add("Installs when you close the game, ready when you reopen it");
        lines.addAll(headlines(news, BANNER_CHANGES));
        lines.add(more);
        bannerLines = List.copyOf(lines);
        bannerSince = now;
    }

    /** A bullet in front of a line marks it as one of the changes, which the banner draws quieter. */
    static final String CHANGE = "• ";
    private static final int BANNER_CHANGES = 3;

    /**
     * The first few changes of {@code entries}, newest first, one to a line,
     * for a banner that has no room for all of them. When there are more it
     * says how many.
     */
    static List<String> headlines(List<Changelog.Entry> entries, int most) {
        List<String> out = new ArrayList<>();
        int total = 0;
        for (Changelog.Entry entry : entries) {
            for (String line : entry.lines()) {
                if (out.size() < most) out.add(CHANGE + line);
                total++;
            }
        }
        if (total > most) out.add("and " + (total - most) + " more");
        return out;
    }

    /**
     * Shows the notice with nothing newer out, so it can be looked at: the
     * banner now, and the notice with buttons the next time the mod's screen
     * is opened. Both say they are a preview.
     */
    static void preview() {
        raise(Tuning.get("update.auto") >= 0.5 ? State.READY : State.NEWER, System.currentTimeMillis());
        bannerTitle = bannerTitle + "  (preview)";
        samplePending = true;
    }

    /** Which notice, if any, belongs in front of the mod's screen as it opens. */
    enum Notice { NONE, REAL, CHANGED, SAMPLE }

    static Notice takeNotice() {
        boolean sample = samplePending;
        samplePending = false;
        if (alertPending()) return Notice.REAL;
        if (changedPending) {
            changedPending = false;
            return Notice.CHANGED;
        }
        return sample ? Notice.SAMPLE : Notice.NONE;
    }

    /**
     * The banner: top centre, where neither the slot panel nor the alerts
     * are, for long enough to be read. It slides in, waits, and slides out.
     */
    private static void hud(GuiGraphicsExtractor graphics, net.minecraft.client.DeltaTracker ignored) {
        long since = bannerSince;
        long age = System.currentTimeMillis() - since;
        if (since == 0 || age < 0 || age >= BANNER_MILLIS) return;
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null) return;
        // The notice itself is on screen, or about to be: no need for both.
        if (client.gui.screen() instanceof DoughBayScreen || client.gui.screen() instanceof UpdateScreen) return;
        Font font = client.font;
        String title = bannerTitle;
        List<String> lines = bannerLines;
        int width = font.width(title);
        for (String line : lines) width = Math.max(width, font.width(line));
        // A change can run long; the banner stays a banner and the line is cut short.
        width = Math.min(width + 28, Math.min(BANNER_WIDTH, graphics.guiWidth() - 16));
        int height = 22 + lines.size() * 11 + 5;
        int x = (graphics.guiWidth() - width) / 2;
        double in = UiTheme.reducedMotion() ? 1 : UiMotion.visibility(age, BANNER_MILLIS, 350, 350);
        int y = (int) Math.round(-height + (height + BANNER_TOP) * in);
        UiTheme.panel(graphics, x, y, width, height, UiTheme.SURFACE);
        graphics.centeredText(font, UiTheme.fit(font, title, width - 16), x + width / 2, y + 7, UiTheme.GOLD);
        int yy = y + 21;
        for (String line : lines) {
            if (line.startsWith(CHANGE)) {
                graphics.text(font, UiTheme.fit(font, line, width - 24), x + 12, yy, UiTheme.SECONDARY);
            } else {
                graphics.centeredText(font, UiTheme.fit(font, line, width - 16), x + width / 2, yy, UiTheme.TEXT);
            }
            yy += 11;
        }
    }

    /** What the notice says: a heading, and paragraphs under it. */
    public record Alert(String title, List<String> paragraphs) { }

    /**
     * The words for the notice. Wherever the build is going to install itself
     * it says when: at the close of the game, ready at the next start.
     */
    static Alert explain(State state, boolean auto, long uploadedAgoMillis) {
        String when = "It installs when you close the game and is ready when you open it again.";
        if (state == State.READY) {
            return new Alert("A new Tally build is downloaded", List.of(
                    when,
                    "Nothing changes while the game is running, so there is no need to stop what you are doing."));
        }
        if (state == State.NO_RELEASE) {
            return new Alert("No Tally release yet", List.of(
                    "Tally has not published a release. When one is out, this notice says so here."));
        }
        if (state != State.NEWER) {
            return new Alert("Tally is up to date", List.of("There is nothing newer to install."));
        }
        String out = "It was put up " + age(uploadedAgoMillis) + " ago. You are running an older one.";
        if (auto) {
            return new Alert("A new Tally build is out", List.of(
                    out,
                    "Auto-update is on and the new build is downloading now. " + when,
                    "If the download does not finish, the download page has the same file."));
        }
        return new Alert("A new Tally build is out", List.of(
                out,
                "Turn on auto-update and it downloads now. " + when + " Every later build then arrives the same way.",
                "Or do it by hand: close the game, download the jar, and replace the one in your mods folder."));
    }

    /** There is a notice the player has not answered yet. */
    static boolean alertPending() {
        Status s = status;
        return (s.state() == State.NEWER || s.state() == State.READY) && !ANSWERED.contains(s.detail());
    }

    /**
     * The player has seen the notice for this build; it is not raised again
     * this session. Its download finishing afterwards is the same news, not a
     * second notice.
     */
    static void acknowledge() {
        ANSWERED.add(status.detail());
    }

    /** Looks again now instead of at the next three-hour mark: auto-update was just switched on. */
    static void checkNow() {
        synchronized (WAKE) {
            wake = true;
            WAKE.notifyAll();
        }
    }

    static String age(long millis) {
        long minutes = Math.max(0, millis) / 60_000L;
        if (minutes < 60) return minutes + " min";
        long hours = minutes / 60;
        if (hours < 48) return hours + " h";
        return hours / 24 + " days";
    }

    private static long builtAt() {
        try (InputStream in = UpdateCheck.class.getResourceAsStream("/doughbay-build.properties")) {
            if (in == null) return 0;
            Properties properties = new Properties();
            properties.load(in);
            return Long.parseLong(properties.getProperty("built_at", "0").strip());
        } catch (IOException | RuntimeException e) {
            return 0;
        }
    }

    /** The jar this mod was loaded from, or null when it was not loaded from one. */
    private static Path ownJar() {
        try {
            List<Path> paths = FabricLoader.getInstance().getModContainer("doughbay").orElseThrow()
                    .getOrigin().getPaths();
            for (Path path : paths) {
                if (Files.isRegularFile(path) && path.getFileName().toString().endsWith(".jar")) return path;
            }
        } catch (Exception e) {
            DoughBayClient.LOGGER.info("DoughBay update check: this jar could not be found ({})", e.toString());
        }
        return null;
    }

    static String sha256(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream in = new DigestInputStream(Files.newInputStream(file), digest)) {
                in.transferTo(java.io.OutputStream.nullOutputStream());
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
    }
}
