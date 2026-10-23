package dev.doughbay.fabric.discord;

import dev.doughbay.fabric.OrderMetrics;
import java.util.*;

/** Shared by Discord, the local panel and the hosted panel's relayed image. */
public final class OrderMetricsPresentation {
    private static String value(List<String[]> rows, String label) {
        return rows.stream().filter(row -> row[0].equals(label)).findFirst().orElseThrow()[1];
    }
    /** A few additions to Money, preserving its existing sales, profit and auction layout. */
    public static List<String[]> compact(List<OrderMetrics.Account> accounts, long now) {
        var rows = summary(accounts, now);
        var result = new ArrayList<String[]>();
        result.add(new String[]{"Unfilled orders", value(rows,"Unfilled orders")});
        result.add(new String[]{"Orders filled", value(rows,"Orders filled 1h") + " / 1h · " + value(rows,"Orders filled 24h") + " / 24h"});
        result.add(new String[]{"Order funds", value(rows,"Order funds")});
        result.add(new String[]{"Awaiting collect", value(rows,"Awaiting collect")});
        boolean newHistory = rows.stream().anyMatch(row -> row[0].equals("Tracking"));
        result.add(new String[]{"Order data", value(rows,"Order coverage") + (newHistory ? "; new history" : ""), "dim"});
        var capacity = OrderMetrics.capacity(accounts, now);
        if (capacity.known() < capacity.accounts()) result.add(new String[]{"Auction capacity", value(rows,"Auction capacity"),"gold"});
        return result;
    }
    /** Existing three-column hourly report, with order figures added as ordinary rows. */
    public static List<String[]> hourlyRows(List<OrderMetrics.Account> accounts, long now) {
        var rows = summary(accounts, now);
        return List.of(new String[]{"orders filled (observed)", value(rows,"Orders filled 1h"), value(rows,"Orders filled 24h")},
                new String[]{"unfilled orders now", value(rows,"Unfilled orders"), ""},
                new String[]{"order funds now", value(rows,"Order funds"), ""},
                new String[]{"awaiting collection now", value(rows,"Awaiting collect"), ""},
                new String[]{"order data", value(rows,"Order coverage"), rows.stream().anyMatch(row -> row[0].equals("Tracking")) ? "new history" : ""});
    }
    public static List<String[]> summary(List<OrderMetrics.Account> accounts, long now) {
        long funds = 0, waiting = 0, hour = 0, day = 0, completed = 0;
        int unfilled = 0, fresh = 0, knownFunds = 0, knownWaiting = 0;
        int timed = 0; long since = 0;
        for (var account : accounts) {
            var report = account.report();
            if (!OrderMetrics.fresh(report, now)) continue;
            fresh++;
            unfilled += (int) report.rows().stream().filter(r -> !r.alreadyCancelled() && r.remaining() > 0).count();
            if (report.reserved() != null) { funds = Math.addExact(funds, report.reserved()); knownFunds++; }
            if (report.awaitingCollection() != null) { waiting = Math.addExact(waiting, report.awaitingCollection()); knownWaiting++; }
            hour += OrderMetrics.completedSince(report, now - 3_600_000, now);
            day += OrderMetrics.completedSince(report, now - 86_400_000, now);
            since = Math.max(since, report.trackingSince()); timed++;
            completed += report.completed();
        }
        String partial = fresh < accounts.size() ? " (partial)" : "";
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"Unfilled orders", fresh == 0 ? "unknown" : unfilled + partial});
        rows.add(new String[]{"Order funds", knownFunds == 0 ? "unknown" : DiscordBridge.money(funds) + (knownFunds < accounts.size() ? " (partial)" : "")});
        rows.add(new String[]{"Awaiting collect", knownWaiting == 0 ? "unknown" : DiscordBridge.money(waiting) + (knownWaiting < accounts.size() ? " (partial)" : "")});
        rows.add(new String[]{"Orders filled 1h", timed == 0 ? "unknown" : hour + " observed" + partial});
        rows.add(new String[]{"Orders filled 24h", timed == 0 ? "unknown" : day + " observed" + partial});
        rows.add(new String[]{"Orders filled", timed == 0 ? "unknown" : completed + " since tracking began" + partial});
        rows.add(new String[]{"Order coverage", fresh + "/" + accounts.size() + " accounts fresh", fresh < accounts.size() ? "gold" : "dim"});
        var capacity = OrderMetrics.capacity(accounts, now);
        rows.add(new String[]{"Auction capacity", capacity.known() == 0 ? "unknown" : capacity.total() + " slots" + (capacity.known() < capacity.accounts() ? " (partial)" : "")});
        if (timed > 0 && now - since < 86_400_000)
            rows.add(new String[]{"Tracking", "New history; 1h/24h windows may be incomplete", "gold"});
        return rows;
    }
}
