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
    static String activeId() { return active == null ? "" : active.id(); }
    public static Path sharedDirectoryOr(Path fallback) { return active == null ? fallback : Path.of(active.directory()); }

    /** The existing owner's notification settings, inside this validated Hive only. */
    static Path notificationConfiguration() {
        Membership member = active;
        if (member == null) return null;
        try { return HiveCombine.configuration(member, member.owner()); }
        catch (IOException ignored) { return null; }
    }

    static boolean joinedProfile(Path configuration) {
        return active != null && joinedProfile(Path.of(active.directory()), configuration);
    }

    static boolean joinedProfile(Path shared, Path configuration) {
        Path parent = configuration.toAbsolutePath().normalize().getParent();
        return shared.toAbsolutePath().normalize().resolve("joined-profiles").equals(parent);
    }

    /** A broken link must not quietly create another ledger and start trading against it. */
    static Path resolve(Path game, Path fallback) throws IOException {
        return resolve(game,fallback,null,false);
    }
    static Path resolve(Path game,Path fallback,String account) throws IOException {
        return resolve(game,fallback,account,true);
    }
    private static Path resolve(Path game,Path fallback,String account,boolean production) throws IOException {
        HiveCombine.completePending(game);
        Path pointer = game.resolve(POINTER);
        if (!Files.exists(pointer)) { active = null; return fallback; }
        Membership actual = validate(read(pointer));
        active = actual;
        if(production){HiveCombine.hold(Path.of(actual.directory()));register(actual);}
        return HiveCombine.configuration(actual,account==null?actual.owner():account);
    }

    static Membership read(Path file) throws IOException {
        if (!Files.isRegularFile(file) || Files.size(file) > 8192)
            throw new IOException("Hive setup file is missing or invalid. Restore the shared folder before starting GoNuts.");
        try {
            Membership m = JSON.readValue(Files.readString(file), Membership.class);
            if ((m.version() != 1 && m.version() != 2) || m.id() == null || m.directory() == null || m.owner() == null
                    || !m.owner().matches("[A-Za-z0-9_.-]{1,32}")) throw new IOException("Invalid hive setup file");
            UUID.fromString(m.id());
            if (!Path.of(m.directory()).isAbsolute()) throw new IOException("Hive folder must be absolute");
            return m;
        } catch (RuntimeException e) { throw new IOException("Invalid hive setup file", e); }
    }

    static Membership validate(Membership m) throws IOException {
        return validate(m,0);
    }
    private static Membership validate(Membership m,int depth) throws IOException {
        if(depth>8)throw new IOException("Invalid combined hive chain");
        Path directory = Path.of(m.directory()).toRealPath();
        Membership stored = read(directory.resolve(MANIFEST));
        if (!stored.id().equals(m.id()) || !stored.owner().equals(m.owner())
                || !Path.of(stored.directory()).toRealPath().equals(directory)) throw new IOException("Hive code does not match this folder");
        requireLocalLedger(directory);
        if (!Files.isRegularFile(directory.resolve("doughbay.db")))
            throw new IOException("Shared trading database is missing; no new database was created");
        Membership normalized=new Membership(m.version(), m.id(), directory.toString(), m.owner());
        Membership combined=HiveCombine.follow(normalized);
        return combined.id().equals(m.id())?normalized:validate(combined,depth+1);
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
            Membership existing = configured(game);
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

    /** Add a fresh account, keeping its own settings rather than adopting the hive owner's. */
    static Membership joinFresh(Path game, Path local, String code, String account) throws IOException {
        Membership m=decode(code);
        if(account==null||!account.matches("[A-Za-z0-9_]{1,16}"))
            throw new IOException("Sign into the Minecraft account you want to add first");
        if(Files.exists(game.resolve(POINTER))) {
            if(!configured(game).id().equals(m.id()))throw new IOException("This instance is already linked to another hive. Its link was kept.");
            return m;
        }
        if(Files.exists(game.resolve("gonuts-hive-combine.json")))
            throw new IOException("Cancel this instance's queued combination before joining another hive");
        requireLocalLedger(local);requireEmpty(local);
        Path shared=Path.of(m.directory());
        if(Files.isSameFile(local,shared))throw new IOException("This instance already uses that hive folder. Share its code from the existing account instead.");
        try(var lease=HiveCombine.joinLease(shared)) {
            if(!validate(m).id().equals(m.id()))throw new IOException("The hive changed during setup. Copy its current code and review again.");
            if(m.owner().equalsIgnoreCase(account)||hasAccountFolder(shared.resolve("accounts"),account)
                    ||hasAccountFolder(shared.resolve("member-profiles"),account)
                    ||hasAccountFolder(shared.resolve("joined-profiles"),account))
                throw new IOException("This account already has data in that hive. Open its existing instance; nothing was replaced.");
            try(var c=DriverManager.getConnection("jdbc:sqlite:"+shared.resolve("doughbay.db").toUri()+"?mode=ro");var s=c.prepareStatement("SELECT 1 FROM positions WHERE lower(client)=lower(?) LIMIT 1")) {
                s.setString(1,account);try(var r=s.executeQuery()){if(r.next())throw new IOException("This account already has trading history in that hive. Open its existing instance; nothing was replaced.");}
            }catch(java.sql.SQLException e){throw new IOException("Could not verify the destination account safely. Nothing was linked.",e);}
            Path profiles=shared.resolve("joined-profiles");Files.createDirectories(profiles);
            Path staging=Files.createTempDirectory(profiles,"preparing-");
            // Publication never replaces an existing profile. An interrupted preparation is kept for diagnosis.
            HiveCombine.profile(local,staging,account,shared);
            Files.move(staging.resolve("member-profiles").resolve(account.toLowerCase(java.util.Locale.ROOT)),
                    profiles.resolve(account.toLowerCase(java.util.Locale.ROOT)));
            writePointer(game,m);
        }catch(IOException e){throw e;}catch(Exception e){throw new IOException("Could not prepare this account's settings. Nothing was overwritten.",e);}
        return m;
    }

    private static boolean hasAccountFolder(Path parent,String account) throws IOException {
        if(!Files.isDirectory(parent))return false;
        try(var files=Files.list(parent)){return files.anyMatch(p->p.getFileName().toString().equalsIgnoreCase(account));}
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
    private static Path registry(){return Path.of(System.getProperty("user.home"),".gonuts","local-hives");}
    static void register(Membership m) {
        try {
            if(net.fabricmc.loader.api.FabricLoader.getInstance().isDevelopmentEnvironment()
                    && (Boolean.getBoolean("gonuts.hive.preview.discovery")||Boolean.getBoolean("gonuts.hive.preview.combine")))return;
            Files.createDirectories(registry());Path file=registry().resolve(m.id()+".json");
            if(!Files.exists(file))writeNew(file,m);
        }catch(Exception ignored){/* Optional discovery cannot affect a valid hive. */}
    }
    static java.util.List<Membership> localHives(String exceptId) {
        var result=new java.util.LinkedHashMap<String,Membership>();
        try(var files=Files.list(registry())) {
            for(Path file:files.filter(p->p.getFileName().toString().matches("[a-f0-9-]{36}\\.json")).limit(64).toList()) {
                try{if(Files.isSymbolicLink(file))continue;Membership m=validate(read(file));if(!m.id().equals(exceptId))result.put(m.id(),m);}catch(Exception ignored){}
            }
        }catch(Exception ignored){}
        return result.values().stream().sorted(java.util.Comparator.comparing(Membership::owner)).toList();
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
