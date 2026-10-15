package dev.doughbay.fabric;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.DriverManager;
import java.util.Base64;
import java.util.UUID;

/** Restart-only local routing. No database copies, history merges or launcher changes. */
public final class HiveSetup {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String POINTER = "gonuts-hive.json", MANIFEST = "hive.json", PREFIX = "GoNutsHive1:";
    private static volatile Membership active;
    public record Membership(int version, String id, String directory, String owner) {}
    private HiveSetup() {}

    public static boolean active() { return active != null; }
    public static Path sharedDirectoryOr(Path fallback) { return active == null ? fallback : Path.of(active.directory()); }

    /** A broken link must not quietly create another ledger and start trading against it. */
    static Path resolve(Path game, Path fallback) throws IOException {
        Path pointer = game.resolve(POINTER);
        if (!Files.exists(pointer)) { active = null; return fallback; }
        Membership actual = validate(read(pointer));
        active = actual;
        return Path.of(actual.directory());
    }

    static Membership read(Path file) throws IOException {
        if (!Files.isRegularFile(file) || Files.size(file) > 8192)
            throw new IOException("Hive setup file is missing or invalid. Restore the shared folder before starting GoNuts.");
        try {
            Membership m = JSON.readValue(Files.readString(file), Membership.class);
            if (m.version() != 1 || m.id() == null || m.directory() == null || m.owner() == null
                    || !m.owner().matches("[A-Za-z0-9_.-]{1,32}")) throw new IOException("Invalid hive setup file");
            UUID.fromString(m.id());
            if (!Path.of(m.directory()).isAbsolute()) throw new IOException("Hive folder must be absolute");
            return m;
        } catch (RuntimeException e) { throw new IOException("Invalid hive setup file", e); }
    }

    static Membership validate(Membership m) throws IOException {
        Path directory = Path.of(m.directory()).toRealPath();
        Membership stored = read(directory.resolve(MANIFEST));
        if (!stored.id().equals(m.id()) || !stored.owner().equals(m.owner())
                || !Path.of(stored.directory()).toRealPath().equals(directory)) throw new IOException("Hive code does not match this folder");
        requireLocalLedger(directory);
        if (!Files.isRegularFile(directory.resolve("doughbay.db")))
            throw new IOException("Shared trading database is missing; no new database was created");
        return new Membership(1, m.id(), directory.toString(), m.owner());
    }

    static void requireLocalLedger(Path directory) throws IOException {
        Path config = directory.resolve("config.json");
        if (!Files.isRegularFile(config)) throw new IOException("Open GoNuts once in the source instance first");
        var root = JSON.readTree(Files.readString(config));
        if (root == null || !root.isObject()) throw new IOException("Invalid GoNuts configuration");
        String db = root.path("databasePath").asText("");
        if (!db.isBlank() && !directory.resolve(db).toAbsolutePath().normalize().equals(directory.resolve("doughbay.db").toAbsolutePath().normalize()))
            throw new IOException("This instance uses a custom database path. Keep its existing setup; the wizard cannot migrate it automatically.");
    }

    static Membership create(Path game, Path directory, String owner) throws IOException {
        if (owner == null || !owner.matches("[A-Za-z0-9_.-]{1,32}"))
            throw new IOException("Sign into the Minecraft account whose data you want to keep first");
        Path dir = directory.toRealPath();
        requireLocalLedger(dir);
        if (!Files.isRegularFile(dir.resolve("doughbay.db"))) throw new IOException("Trading database is not ready yet");
        Membership m = Files.exists(dir.resolve(MANIFEST)) ? read(dir.resolve(MANIFEST))
                : new Membership(1, UUID.randomUUID().toString(), dir.toString(), owner);
        if (!Files.exists(dir.resolve(MANIFEST))) writeNew(dir.resolve(MANIFEST), m);
        m = validate(m);
        writePointer(game, m);
        return m;
    }

    static String code(Membership m) throws IOException {
        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(JSON.writeValueAsBytes(m));
    }

    static Membership decode(String code) throws IOException {
        if (code == null || !code.strip().startsWith(PREFIX) || code.length() > 12000)
            throw new IOException("Paste a code copied with Create hive on this same computer");
        try {
            var m = JSON.readValue(Base64.getUrlDecoder().decode(code.strip().substring(PREFIX.length())), Membership.class);
            if (m.version() != 1 || m.id() == null || m.owner() == null || m.directory() == null) throw new IOException("Invalid hive code");
            UUID.fromString(m.id());
            if (!Path.of(m.directory()).isAbsolute()) throw new IOException("Invalid hive folder");
            return validate(m);
        } catch (RuntimeException e) { throw new IOException("Invalid hive code", e); }
    }

    static Membership join(Path game, Path local, String code) throws IOException {
        Membership m = decode(code);
        Path destination = Path.of(m.directory());
        if (Files.exists(game.resolve(POINTER))) {
            Membership existing = read(game.resolve(POINTER));
            if (!existing.id().equals(m.id())) throw new IOException("This instance is already linked to another hive. Its link was kept.");
            validate(existing);
            return m;
        }
        if (!Files.isSameFile(local, destination)) {
            requireLocalLedger(local);
            requireEmpty(local);
        }
        writePointer(game, m);
        return m;
    }

    /** Read-only inspection never creates a DB, migrates it or checkpoints a writer. */
    static void requireEmpty(Path local) throws IOException {
        Path db = local.resolve("doughbay.db");
        if (Files.exists(db)) {
            try (var c = DriverManager.getConnection("jdbc:sqlite:" + db.toUri() + "?mode=ro")) {
                try (var s = c.createStatement()) {
                    s.execute("PRAGMA busy_timeout=1000"); s.execute("PRAGMA query_only=ON");
                    try (var r = s.executeQuery("SELECT 1 FROM positions LIMIT 1")) {
                        if (r.next()) throw new IOException("This instance already has trading history. Nothing was changed. Create the hive from the instance whose history you want to keep; combining existing histories needs a separate migration.");
                    }
                }
            } catch (java.sql.SQLException e) { throw new IOException("Could not verify this instance's history safely. Nothing was changed.", e); }
        }
        for (String file : AccountRecords.FILES) {
            Path record = local.resolve(file);
            if (Files.isRegularFile(record) && Files.size(record) > 0)
                throw new IOException("This instance has saved order or balance records. Nothing was changed; use a fresh instance to join.");
        }
        Path accounts = local.resolve("accounts");
        if (Files.isDirectory(accounts)) try (var files = Files.walk(accounts)) {
            if (files.anyMatch(Files::isRegularFile)) throw new IOException("This instance has saved account records. Use a fresh instance to join.");
        }
    }

    static Membership configured(Path game) throws IOException {
        return Files.exists(game.resolve(POINTER)) ? validate(read(game.resolve(POINTER))) : null;
    }

    private static void writePointer(Path game, Membership m) throws IOException {
        Path file = game.resolve(POINTER);
        if (Files.exists(file)) {
            Membership old = read(file);
            if (!old.id().equals(m.id())) throw new IOException("An existing hive link was kept. No settings were overwritten.");
            return;
        }
        writeNew(file, m);
    }

    private static void writeNew(Path file, Membership m) throws IOException {
        // CREATE_NEW refuses races and preserves existing files. Original data stays in place.
        Files.writeString(file, JSON.writeValueAsString(m), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
    }

    static boolean inheritsRootRecords(Path shared, String account) throws IOException {
        if (!Files.exists(shared.resolve(MANIFEST))) return true; // legacy junctions
        return read(shared.resolve(MANIFEST)).owner().equalsIgnoreCase(account);
    }
}
