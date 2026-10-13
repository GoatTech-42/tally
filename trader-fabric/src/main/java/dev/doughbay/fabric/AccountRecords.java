package dev.doughbay.fabric;

import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Where one account's own records are kept: what it has ordered and
 * collected, and the balance at its last slot check.
 *
 * <p>These were single files in the config folder, which is right for one
 * client. Two clients that share the folder, so as to share the ledger, then
 * shared them too: each start read whatever the other had last written, one
 * account's open orders and bank balance taken for the other's. A hive keeps
 * them in a folder per account. A client running alone keeps them where they
 * always were.
 */
final class AccountRecords {
    /** The files that are an account's own; everything else in the folder is the market's and is shared. */
    static final String[] FILES = {"audit-balance.txt", "orders-collected.txt", "orders-bids.txt",
            "orders-fills.txt", "orders-retail.txt"};

    private AccountRecords() {
    }

    /**
     * The folder this account's records live in. The first time an account is
     * given its own, the records there were until now come along, so the
     * account that wrote them carries on from them; they are checked against
     * the server's own pages before anything is done on their strength.
     */
    static Path directory(Path shared, boolean hive, String account, Logger log) {
        if (!hive || account == null || account.isBlank()) return shared;
        String name = account.strip().replaceAll("[^A-Za-z0-9_.\\-]", "_");
        if (name.equals(".") || name.equals("..")) return shared;
        Path own = shared.resolve("accounts").resolve(name);
        try {
            boolean fresh = !Files.isDirectory(own);
            Files.createDirectories(own);
            if (fresh) {
                for (String file : FILES) {
                    Path old = shared.resolve(file);
                    if (Files.isRegularFile(old) && !Files.exists(own.resolve(file))) Files.copy(old, own.resolve(file));
                }
                log.info("DoughBay: {} keeps its own order and balance records in {}", account, own);
            }
            return own;
        } catch (IOException | RuntimeException e) {
            log.warn("DoughBay could not make a records folder for {}: {}; using the shared one", account, e.toString());
            return shared;
        }
    }
}
