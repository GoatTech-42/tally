package dev.doughbay.fabric;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.util.*;

/** Reporting only. Never changes an order, ownership, collection counter or trade gate. */
public final class OrderMetrics {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final long HOUR = 3_600_000L;
    private static volatile OrderMetrics active;
    public record Counts(int total, int full) {}
    public record Report(String account, long at, long readAt, long trackingSince,
                         List<AutomatedExecutionDriver.OwnOrderRow> rows,
                         Long reserved, Long awaitingCollection, int auctionSlots, int orderSlots, long completed,
                         List<Long> completions, Map<String, Counts> previous, String note) {}
    public record Account(String name, Report report) {}
    private final Path file;
    private final String account;
    private long since, completed, lastRead;
    private List<Long> completions = new ArrayList<>();
    private Map<String, Counts> previous = new HashMap<>();
    private boolean writable = true;
    private String note = "Completions are observed partial-to-full transitions; history starts with this update.";

    OrderMetrics(Path root, String account, long now) {
        this.file = path(root, account); this.account = account; since = now;
        if (Files.exists(file)) {
            Report saved = read(root, account);
            if (saved == null) { writable = false; note = "Saved order metrics unreadable; existing file preserved."; }
            else { since = saved.trackingSince(); completed = saved.completed(); lastRead = saved.readAt();
                completions = new ArrayList<>(saved.completions()); previous = new HashMap<>(saved.previous()); }
        }
    }
    public static void start(Path root, String account) {
        if (account != null && !account.isBlank()) active = new OrderMetrics(root, account, System.currentTimeMillis());
    }
    private static String key(AutomatedExecutionDriver.OwnOrderRow row) {
        return row.itemId() + "|" + row.unitPrice() + "|" + row.requested();
    }
    public static void observe(long at, List<AutomatedExecutionDriver.OwnOrderRow> rows) {
        var metrics = active; if (metrics != null) metrics.accept(at, rows);
    }
    /** A receipted placement establishes a new known, initially unfilled order. */
    public static void placed(String item, long price, int count) {
        var metrics = active; if (metrics != null) metrics.placement(item, price, count);
    }
    synchronized void placement(String item, long price, int count) {
        String key = item + "|" + price + "|" + count;
        Counts old = previous.getOrDefault(key, new Counts(0, 0));
        previous.put(key, new Counts(old.total() + 1, old.full()));
    }
    synchronized void accept(long at, List<AutomatedExecutionDriver.OwnOrderRow> rows) {
        if (!writable || at <= lastRead) return;
        Map<String, Counts> next = new HashMap<>();
        for (var row : rows) {
            if (row.requested() <= 0 || row.unitPrice() <= 0 || row.delivered() < 0 || row.delivered() > row.requested()) return;
            if (row.alreadyCancelled()) continue;
            String key = key(row); Counts old = next.getOrDefault(key, new Counts(0, 0));
            next.put(key, new Counts(old.total() + 1, old.full() + (row.remaining() == 0 ? 1 : 0)));
        }
        for (var e : next.entrySet()) {
            Counts old = previous.get(e.getKey());
            if (old == null) continue; // An already-full order on first sight has no known completion time.
            int count = Math.min(Math.max(0, e.getValue().full() - old.full()), Math.max(0, old.total() - old.full()));
            completed += count;
            for (int i = 0; i < count; i++) completions.add(at);
        }
        completions.removeIf(time -> time < at - 24 * HOUR);
        previous = next; lastRead = at;
    }
    synchronized Report capture(long now, long readAt, List<AutomatedExecutionDriver.OwnOrderRow> rows, CapitalBreakdown.Orders capital) {
        return new Report(account, now, readAt, since, List.copyOf(rows), capital.reserved(), capital.awaitingCollection(),
                (int) Tuning.get("slots.max"), (int) Tuning.get("orders.bid_slots"),
                completed, List.copyOf(completions), Map.copyOf(previous), note);
    }
    public static void publish(dev.doughbay.fabric.automation.AutomationSessionController controller) throws Exception {
        var metrics = active; if (metrics == null || !metrics.writable) return;
        Report report = controller.orderMetricsReport(metrics);
        Files.createDirectories(metrics.file.getParent());
        Path temp = Files.createTempFile(metrics.file.getParent(), "orders-", ".tmp");
        try { Files.write(temp, JSON.writeValueAsBytes(report));
            Files.move(temp, metrics.file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temp); }
    }
    public Report snapshot(long now, long readAt, List<AutomatedExecutionDriver.OwnOrderRow> rows, CapitalBreakdown.Orders capital) {
        return capture(now, readAt, rows, capital);
    }
    static Path path(Path root, String account) {
        try { String hash = HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest(account.toLowerCase(Locale.ROOT).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            return root.resolve("order-reports").resolve(hash + ".json");
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    static Report read(Path root, String account) {
        try { Path file = path(root, account); if (!Files.isRegularFile(file) || Files.size(file) > 4_000_000) return null;
            Report report = JSON.readValue(Files.readAllBytes(file), Report.class);
            if (!report.account().equalsIgnoreCase(account) || report.at() <= 0 || report.trackingSince() <= 0
                    || report.completed() < 0 || report.rows().size() > 1000 || report.completions().size() > 100000
                    || report.previous().size() > 2000 || report.note() == null) return null;
            if (report.reserved() != null && report.reserved() < 0 || report.awaitingCollection() != null && report.awaitingCollection() < 0) return null;
            for (var row : report.rows()) if (row.requested() <= 0 || row.unitPrice() <= 0 || row.delivered() < 0 || row.delivered() > row.requested()) return null;
            return report;
        } catch (Exception unavailable) { return null; }
    }
    public static List<Account> select(Path root, String selected, List<String> members) {
        var names = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
        if (selected != null && !selected.isBlank()) names.add(selected); else names.addAll(members);
        return names.stream().map(name -> new Account(name, read(root, name))).toList();
    }
    public static boolean fresh(Report report, long now) {
        return report != null && report.at() <= now && now - report.at() <= 90_000
                && report.readAt() > 0 && report.readAt() <= now && now - report.readAt() <= 300_000;
    }
    /** Capacity needs a running-client heartbeat, but does not require an own-orders menu read. */
    public static boolean live(Report report, long now) {
        return report != null && report.at() <= now && now - report.at() <= 90_000;
    }
    public record Capacity(int known, int total, int accounts) {}
    public static Capacity capacity(List<Account> accounts, long now) {
        int known = 0, total = 0;
        for (var account : accounts) if (live(account.report(), now) && account.report().auctionSlots() > 0) {
            known++; total = Math.addExact(total, account.report().auctionSlots());
        }
        return new Capacity(known, total, accounts.size());
    }
    public static List<String> members(Path ledger, List<String> connected, String current, boolean hive) {
        var names = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
        names.addAll(connected); if (current != null && !current.isBlank()) names.add(current);
        if (hive) try (var db = new dev.doughbay.storage.Database(ledger);
                var query = db.connection().prepareStatement("SELECT client FROM automation_session_state WHERE client<>'' UNION SELECT client FROM positions WHERE client<>''");
                var result = query.executeQuery()) {
            while (result.next()) names.add(result.getString(1));
        } catch (Exception unavailable) { /* Keep the recognized active roster; never invent an account. */ }
        return List.copyOf(names);
    }
    public static long completedSince(Report report, long from, long now) {
        return report.completions().stream().filter(at -> at > from && at <= now).count();
    }
}
