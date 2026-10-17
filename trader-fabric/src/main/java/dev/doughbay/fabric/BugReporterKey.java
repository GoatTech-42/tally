package dev.doughbay.fabric;

import java.io.IOException;
import java.nio.file.*;
import java.security.*;
import java.util.HexFormat;

/** Anonymous installation grouping only. Never derived from an account, credential or machine identifier. */
public final class BugReporterKey {
    public static final String NOTICE = "An anonymous installation key may group repeat reports; it contains no account name.";
    private BugReporterKey() {}
    static synchronized String load(Path file) throws IOException {
        Files.createDirectories(file.toAbsolutePath().getParent());
        if (Files.isSymbolicLink(file)) throw new IOException("Report grouping key unavailable");
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
            byte[] random = new byte[32]; new SecureRandom().nextBytes(random);
            try { Files.write(file, random, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE); }
            catch (FileAlreadyExistsException race) { /* Another client created it; do not rotate it. */ }
        }
        byte[] secret;
        try (var input = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) { secret = input.readNBytes(33); }
        if (secret.length != 32) throw new IOException("Report grouping key unavailable");
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(secret)); }
        catch (NoSuchAlgorithmException e) { throw new IOException("Report grouping key unavailable"); }
    }
    static String current() {
        try { return load(net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("gonuts-report-grouping.bin")); }
        catch (IOException | RuntimeException e) { return ""; }
    }
}
